package org.service.customer.controller.v1;

import lombok.extern.slf4j.Slf4j;
import org.service.customer.models.ChatMessage;
import org.service.customer.models.SessionInfo;
import org.service.customer.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Slf4j
@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload ChatMessage chatMessage, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String tenantId = chatMessage.getTenantId();
        String userId = chatMessage.getSender();
        String userType = chatMessage.getUserType();

        if (tenantId == null || userId == null || userType == null) {
            log.error("Missing user information in chat message");
            return;
        }

        chatMessage.setSessionId(headerAccessor.getSessionId());

        // Create and save SessionInfo
        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setSessionId(chatMessage.getSessionId());
        sessionInfo.setUserId(userId);
        sessionInfo.setUserType(userType);
        sessionInfo.setTenantId(tenantId);

        chatService.saveSessionInfo(sessionInfo);
        chatService.saveUserSessionMapping(tenantId, userId, chatMessage.getSessionId());
        chatService.saveSessionTenantMapping(chatMessage.getSessionId(), tenantId);

        log.info("User {} of type {} has joined with session ID {} under tenant {}", userId, userType, chatMessage.getSessionId(), tenantId);

        chatMessage.setTimestamp(Instant.now());
        chatMessage.setType(ChatMessage.MessageType.JOIN);

        // Delegate queue creation and binding to ChatService
        chatService.createAndBindTenantQueues(tenantId);

        if ("agent".equals(userType)) {
            chatService.addAgentToAvailableList(tenantId, userId);
            log.info("Agent {} is now available under tenant {}", userId, tenantId);
        }

        // Send a notification to all agents that a customer has joined
        if ("customer".equals(userType)) {
            chatService.notifyNewCustomerSession(tenantId, chatMessage);
            log.info("Broadcasted customer join message to /topic/{}.new_customer", tenantId);
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String tenantId = chatMessage.getTenantId();
        String userId = chatMessage.getSender();
        String userType = chatMessage.getUserType();

        if (tenantId == null || userId == null || userType == null) {
            log.error("Missing user information in chat message");
            return;
        }

        String sessionId = headerAccessor.getSessionId();
        chatMessage.setSessionId(sessionId);
        chatMessage.setTimestamp(Instant.now());

        SessionInfo sessionInfo = chatService.getSessionInfo(tenantId, sessionId);
        if (sessionInfo == null) {
            log.warn("No session info found for session ID {} under tenant {}", sessionId, tenantId);
            return;
        }

        if ("customer".equals(sessionInfo.getUserType())) {
            chatMessage.setSource(ChatMessage.SourceType.USER);
            chatService.broadcastMessage(chatMessage);
            log.info("Broadcasted customer message from {} under tenant {}", userId, tenantId);
        } else if ("agent".equals(sessionInfo.getUserType())) {
            chatMessage.setSource(ChatMessage.SourceType.AGENT);
            // 'receiver' contains the customer's userId
            // Forward the message directly to the customer via SimpMessagingTemplate
            chatService.sendAgentMessageToCustomer(chatMessage);
            log.info("Sent agent {} message to customer {} under tenant {}", userId, chatMessage.getReceiver(), tenantId);
        }

        // Save the message
        chatService.saveMessage(chatMessage);
    }
}
