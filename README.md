# MCP for Java Engineers — demo monorepo

Companion code for the talk *“MCP vs API: why traditional APIs are failing AI agents — and what
comes after MCP.”*

Three Spring Boot applications, one Gradle build, one local model.

```
agent-chat  :8080   Ollama + Spring AI ChatClient + web chat UI
                    ├── TOOLS mode  → classic MCP tool calling
                    └── CODE  mode  → code execution in a GraalJS sandbox
mcp-server-github    :8081   13 MCP tools over the GitHub REST API
mcp-server-actuator  :8082   8 MCP tools over Spring Boot Actuator (the agent inspects itself)
```

## Stack

| | |
|---|---|
| Java | 25 (LTS) — records, sealed types, pattern matching, virtual threads, `ScopedValue` |
| Build | Gradle 9.7 (Kotlin DSL, version catalog, configuration cache) |
| Framework | Spring Boot 4.1.0 / Spring Framework 7 |
| AI | Spring AI 2.0.0 — `@McpTool` annotations, MCP client/server starters, Ollama |
| Sandbox | GraalJS 25 (`org.graalvm.polyglot`) |
| Model | `qwen2.5:1.5b` by default, `llama3:8b` via the `llama` profile |

## Prerequisites

```bash
java -version          # 25
ollama --version
ollama pull qwen2.5:1.5b
ollama pull llama3:8b  # optional but far more reliable at tool calling
```

A **fine-grained** GitHub personal access token, scoped to a single throwaway repository:

* Contents — read & write
* Issues — read & write
* Pull requests — read & write
* Metadata — read

Copy `.env.example` to `.env` and fill it in, or export the variables in your shell.

## Run

Three terminals (or a run configuration each in IDEA):

```bash
export GITHUB_TOKEN=github_pat_...
export DEMO_GITHUB_OWNER=your-user
export DEMO_GITHUB_REPO=mcp-java-community
export DEMO_GITHUB_WRITE_ENABLED=true

./gradlew :apps:mcp-server-github:bootRun
./gradlew :apps:mcp-server-actuator:bootRun
./gradlew :apps:agent-chat:bootRun          # add --args='--spring.profiles.active=llama' for llama3:8b
```

Open <http://localhost:8080>.

Start the two MCP servers **before** the agent: the Spring AI MCP client connects and calls
`tools/list` at startup. If a server is down, its tools are simply missing — the agent still starts.

## What to look at, in order

1. **`RepositoryTools` / `IssueTools`** — `@McpTool` methods. The descriptions are the prompt;
   the return records are the context budget.
2. **`RepoGuard`** — allow-list enforced on the server, not in the system prompt.
3. **`GuardedToolCallback`** — human-in-the-loop implemented as an ordinary tool result.
4. **`McpToolCatalog#contextCostInTokens`** — the number shown in the UI badge, and the reason the
   second half of the talk exists.
5. **`CodeModeTools` + `JsSandbox` + `McpBridge`** — the same tools, reached by writing code.

## The three modes in the UI

| Mode | What the model sees | Use it to show |
|---|---|---|
| **Tools** | all 21 tool definitions, every turn | the classic MCP loop, and its context cost |
| **Code mode** | 3 tools: `list_tools`, `describe_tools`, `run_script` | progressive disclosure + filtering in the sandbox |
| **No tools** | nothing | what the model actually knows on its own (spoiler: it will confidently make things up) |

## Known sharp edges

* `qwen2.5:1.5b` will occasionally invent a tool name or drop a required argument. That is not a bug
  in the demo — it is the argument for narrow, well-described tools, and for Code Mode.
* Write tools are blocked inside Code Mode on purpose (`McpBridge`). A script that makes forty calls
  has nowhere to pause and ask a human.
* GraalJS on a stock JDK runs in interpreter mode. Fine for a demo; use GraalVM if you care.
