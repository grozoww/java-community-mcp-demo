package com.dataart.jc.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChatClientConfig {

    @Bean
    ChatMemory chatMemory() {
        // Small models forget nothing gracefully. Keep the window short and the demo predictable.
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }

    @Bean
    ChatClient agentChatClient(ChatModel chatModel, ChatMemory chatMemory, AgentProperties properties) {
        return ChatClient.builder(chatModel)
                .defaultSystem(properties.systemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
