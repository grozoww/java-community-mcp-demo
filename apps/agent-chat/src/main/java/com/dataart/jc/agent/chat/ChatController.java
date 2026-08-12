package com.dataart.jc.agent.chat;

import java.util.List;
import java.util.Map;

import com.dataart.jc.agent.approval.ApprovalRegistry;
import com.dataart.jc.agent.chat.ChatDtos.ChatRequest;
import com.dataart.jc.agent.chat.ChatDtos.ChatResponsePayload;
import com.dataart.jc.agent.chat.ChatDtos.Mode;
import com.dataart.jc.agent.config.AgentProperties;
import com.dataart.jc.agent.mcp.McpToolCatalog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final McpToolCatalog catalog;
    private final ApprovalRegistry approvals;
    private final AgentProperties properties;

    public ChatController(ChatService chatService,
                          McpToolCatalog catalog,
                          ApprovalRegistry approvals,
                          AgentProperties properties) {
        this.chatService = chatService;
        this.catalog = catalog;
        this.approvals = approvals;
        this.properties = properties;
    }

    @PostMapping("/chat")
    public ChatResponsePayload chat(@RequestBody ChatRequest request) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? "default" : request.conversationId();
        Mode mode = request.mode() == null ? Mode.TOOLS : request.mode();
        return chatService.chat(conversationId, request.message(), mode);
    }

    @GetMapping("/tools")
    public Map<String, Object> tools() {
        return Map.of(
                "tools", catalog.describe(),
                "count", catalog.describe().size(),
                "contextCostTokens", catalog.contextCostInTokens(),
                "suggestions", properties.suggestions());
    }

    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable String id) {
        return approvals.approve(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/approvals/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable String id) {
        return approvals.reject(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/approvals")
    public List<?> pending(String conversationId) {
        return approvals.pendingFor(conversationId == null ? "default" : conversationId);
    }
}
