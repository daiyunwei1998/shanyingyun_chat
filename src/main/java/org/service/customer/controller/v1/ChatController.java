package org.service.customer.controller.v1;

import lombok.extern.slf4j.Slf4j;
import org.service.customer.models.ChatMessage;
import org.service.customer.models.SessionInfo;
import org.service.customer.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatService chatService) {
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
    }

    @MessageMapping("/chat.addUser")
    public void addUser(ChatMessage chatMessage, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String tenantId = chatMessage.getTenantId();
        String userId = chatMessage.getSender();
        String userType = chatMessage.getUserType();

        if (tenantId == null || userId == null || userType == null) {
            // Handle error
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

        // Save session info and mappings
        chatService.saveSessionInfo(sessionInfo);
        chatService.saveUserSessionMapping(tenantId, userId, chatMessage.getSessionId());
        chatService.saveSessionTenantMapping(chatMessage.getSessionId(), tenantId);

        log.info("User {} of type {} has joined with session ID {} under tenant {}", userId, userType, chatMessage.getSessionId(), tenantId);

        chatMessage.setTimestamp(Instant.now());
        chatMessage.setType(ChatMessage.MessageType.JOIN);



        if (tenantId == null || userId == null || userType == null) {
            // Handle error
            log.error("Missing user information in session attributes");
            return;
        }

        if ("customer".equals(userType)) {
            // Assign agent
            String agentId = chatService.findAvailableAgent(tenantId);
            if (agentId == null) {
                log.warn("No available agents for tenant {}", tenantId);
                // Notify customer about unavailability
                chatMessage.setContent("No agents are currently available. Please wait.");
                messagingTemplate.convertAndSendToUser(userId, "/queue/messages", chatMessage);
                return;
            }

            sessionInfo.setAssignedTo(agentId);
            chatService.saveSessionInfo(sessionInfo);

            // Update agent's session info
            chatService.assignCustomerToAgent(tenantId, agentId, userId);

            chatMessage.setReceiver(agentId);

            // Notify agent and customer
            messagingTemplate.convertAndSendToUser(agentId, "/queue/messages", chatMessage);
            messagingTemplate.convertAndSendToUser(userId, "/queue/messages", chatMessage);
        } else if ("agent".equals(userType)) {
            log.info("Agent {} is now available under tenant {}", userId, tenantId);
            // Add agent to available list
            chatService.addAgentToAvailableList(tenantId, userId);
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(ChatMessage chatMessage, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String tenantId = chatMessage.getTenantId();
        String userId = chatMessage.getSender();
        String userType = chatMessage.getUserType();

        if (tenantId == null || userId == null || userType == null) {
            // Handle error
            log.error("Missing user information in chat message");
            return;
        }

        String sessionId = headerAccessor.getSessionId();
        chatMessage.setSessionId(sessionId);
        chatMessage.setTimestamp(Instant.now());

        // Retrieve session info
        SessionInfo sessionInfo = chatService.getSessionInfo(tenantId, sessionId);
        if (sessionInfo == null) {
            log.warn("No session info found for session ID {} under tenant {}", sessionId, tenantId);
            return;
        }

        String receiver;

        if ("customer".equals(sessionInfo.getUserType())) {
            chatMessage.setSource(ChatMessage.SourceType.USER);
            receiver = sessionInfo.getAssignedTo(); // Assigned agent
            if (receiver == null) {
                log.warn("Customer {} is not assigned to any agent under tenant {}", userId, tenantId);
                return;
            }
            chatMessage.setReceiver(receiver);
            chatMessage.setCustomerId(userId);
        } else if ("agent".equals(sessionInfo.getUserType())) {
            chatMessage.setSource(ChatMessage.SourceType.AGENT);
            // Get the customers assigned to this agent
            List<String> assignedCustomers = chatService.getAssignedCustomers(tenantId, userId);
            if (assignedCustomers.isEmpty()) {
                log.warn("Agent {} is not assigned to any customers under tenant {}", userId, tenantId);
                return;
            }
            // For simplicity, send the message to all assigned customers
            for (String customerId : assignedCustomers) {
                chatMessage.setReceiver(customerId);
                chatMessage.setCustomerId(customerId);

                // Save the message
                chatService.saveMessage(chatMessage);

                // Send the message to the customer
                messagingTemplate.convertAndSendToUser(customerId, "/queue/messages", chatMessage);
            }
            return;
        } else {
            log.error("Unknown user type: {}", sessionInfo.getUserType());
            return;
        }

        // Save the message
        chatService.saveMessage(chatMessage);

        // Send the message to the receiver
        messagingTemplate.convertAndSendToUser(receiver, "/queue/messages", chatMessage);
    }

    // Utility method to extract tenantId from Principal
    private String getTenantIdFromPrincipal(Principal principal) {
        String name = principal.getName(); // Expected format: tenantId:username
        String[] parts = name.split(":");
        if (parts.length == 2) {
            return parts[0];
        } else {
            log.error("Invalid principal name format: {}", name);
            return null;
        }
    }



}
