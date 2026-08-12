package com.dataart.jc.agent;

import com.dataart.jc.agent.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AgentChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentChatApplication.class, args);
    }
}
