package com.dataart.jc.agent.codemode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A single-use GraalJS context: the JVM's answer to Cloudflare's V8 isolate.
 *
 * <p>Everything is denied by default - no file system, no threads, no processes, no environment, no
 * host class lookup. The only way out is {@link McpBridge}. A watchdog closes the context from
 * another thread if the script overruns, which cancels execution even inside a tight loop.
 *
 * <p>Note what this costs you: a second language runtime, a second security model, and a second
 * place where bugs can hide. That is the real price of Code Mode, and it is not on the slide that
 * shows the 98% token saving.
 */
@Component
public class JsSandbox {

    private static final Logger log = LoggerFactory.getLogger(JsSandbox.class);

    private static final String PRELUDE = """
            const mcp = new Proxy({}, {
              get(_target, name) {
                return (args) => JSON.parse(__mcp.call(String(name), JSON.stringify(args === undefined ? {} : args)));
              }
            });
            function print(...values) {
              console.log(values.map(v => (typeof v === 'string' ? v : JSON.stringify(v, null, 0))).join(' '));
            }
            """;

    public record Result(boolean ok, String output, String error, long millis, int toolCalls) {
    }

    public Result run(String script, McpBridge bridge, long timeoutMillis) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        long start = System.nanoTime();

        Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
                .option("engine.WarnInterpreterOnly", "false")
                .out(captured)
                .err(captured)
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            context.getBindings("js").putMember("__mcp", bridge);
            context.eval(Source.create("js", PRELUDE));

            Future<?> task = executor.submit(() -> context.eval(Source.create("js", script)));
            try {
                task.get(timeoutMillis, TimeUnit.MILLISECONDS);
                return new Result(true, output(captured), null, millis(start), bridge.callCount());
            } catch (TimeoutException e) {
                context.close(true);          // cancels the running script
                return new Result(false, output(captured),
                        "Script exceeded the %d ms budget and was cancelled.".formatted(timeoutMillis),
                        millis(start), bridge.callCount());
            } catch (Exception e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                String message = cause instanceof PolyglotException polyglot && polyglot.isGuestException()
                        ? polyglot.getMessage()
                        : String.valueOf(cause.getMessage());
                log.debug("code-mode script failed", cause);
                return new Result(false, output(captured), message, millis(start), bridge.callCount());
            }
        } finally {
            try {
                context.close(true);
            } catch (RuntimeException ignored) {
                // already closed by the watchdog path
            }
        }
    }

    private static String output(ByteArrayOutputStream captured) {
        String text = captured.toString(StandardCharsets.UTF_8);
        return text.length() > 12_000 ? text.substring(0, 12_000) + "\n...[output clipped]" : text;
    }

    private static long millis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
