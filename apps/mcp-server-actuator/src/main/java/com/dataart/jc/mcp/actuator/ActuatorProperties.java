package com.dataart.jc.mcp.actuator;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl            actuator base URL of the application under observation.
 * @param writableLoggers    logger-name prefixes whose level the agent may change at runtime.
 * @param writeEnabled       master switch for the one mutating tool.
 */
@ConfigurationProperties(prefix = "demo.actuator")
public record ActuatorProperties(String baseUrl, List<String> writableLoggers, boolean writeEnabled) {

    public ActuatorProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080/actuator";
        }
        if (writableLoggers == null || writableLoggers.isEmpty()) {
            writableLoggers = List.of("com.dataart.jc", "org.springframework.ai");
        }
    }
}
