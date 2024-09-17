package org.service.customer.controller.v1;

import lombok.extern.slf4j.Slf4j;
import org.service.customer.dto.HandoverRequest;
import org.service.customer.models.ChatMessage;
import org.service.customer.service.ChatService;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/v1/chats")
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/handover")
    public void handoverToAgent(@RequestBody HandoverRequest handoverRequest) {
        String sessionId = handoverRequest.getSessionId();
        String chatSummary = handoverRequest.getSummary();
        String customerId = handoverRequest.getCustomerId();
        String tenantId = handoverRequest.getTenantId();

        // Send message to customer_waiting queue
        ChatMessage aiMessage = new ChatMessage();
        aiMessage.setSessionId(sessionId);
        aiMessage.setType(ChatMessage.MessageType.CHAT);
        aiMessage.setContent(chatSummary);
        aiMessage.setSender("AI");
        aiMessage.setCustomerId(customerId);
        aiMessage.setTenantId(tenantId);
        aiMessage.setSource(ChatMessage.SourceType.AI);

        // Only publish that the customer is waiting
        chatService.publishCustomerWaiting(aiMessage);

    }





}
