package com.dataart.jc.mcp.actuator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The "agent looks at itself" server.
 *
 * <p>It speaks MCP to the model and plain HTTP to Spring Boot Actuator. That is the whole idea of
 * MCP as middleware: the API underneath does not change, and does not need to know an agent exists.
 *
 * <p>It is also the demo's safety net - it needs no network, no token and no rate limit, so the
 * talk survives a dead conference Wi-Fi.
 */
@SpringBootApplication
@EnableConfigurationProperties(ActuatorProperties.class)
public class ActuatorMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActuatorMcpServerApplication.class, args);
    }
}
