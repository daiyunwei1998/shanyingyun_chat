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
        String sender = principal.getName();
        chatMessage.setSender(sender);

        // Extract tenantId
        //String tenantId = getTenantIdFromPrincipal(principal);
        String tenantId = "temp line35 change this";
        if (tenantId == null) {
            // Handle error
            return;
        }
        chatMessage.setTenantId(tenantId);

        // Retrieve the session ID from the header accessor
        String sessionId = headerAccessor.getSessionId();
        chatMessage.setSessionId(sessionId);

        // Determine user type
        String userType = sender.contains("customer") ? "customer" : "agent";

        // Create and save SessionInfo
        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setSessionId(sessionId);
        sessionInfo.setUserId(sender);
        sessionInfo.setUserType(userType);
        sessionInfo.setTenantId(tenantId);

        // Save session info
        chatService.saveSessionInfo(sessionInfo);

        // Map userId to sessionId
        chatService.saveUserSessionMapping(tenantId, sender, sessionId);

        // Save session-tenant mapping
        chatService.saveSessionTenantMapping(sessionId, tenantId);

        log.info("User {} of type {} has joined with session ID {} under tenant {}", sender, userType, sessionId, tenantId);

        chatMessage.setTimestamp(Instant.now());
        chatMessage.setType(ChatMessage.MessageType.JOIN);

        if ("customer".equals(userType)) {
            // Assign agent
            String agentId = chatService.findAvailableAgent(tenantId);
            if (agentId == null) {
                log.warn("No available agents for tenant {}", tenantId);
                // Notify customer about unavailability
                chatMessage.setContent("No agents are currently available. Please wait.");
                messagingTemplate.convertAndSendToUser(sender, "/queue/messages", chatMessage);
                return;
            }

            sessionInfo.setAssignedTo(agentId);
            chatService.saveSessionInfo(sessionInfo);

            // Update agent's session info
            chatService.assignCustomerToAgent(tenantId, agentId, sender);

            chatMessage.setReceiver(agentId);

            // Notify agent and customer
            messagingTemplate.convertAndSendToUser(agentId, "/queue/messages", chatMessage);
            messagingTemplate.convertAndSendToUser(sender, "/queue/messages", chatMessage);
        } else if ("agent".equals(userType)) {
            log.info("Agent {} is now available under tenant {}", sender, tenantId);
            // Add agent to available list
            chatService.addAgentToAvailableList(tenantId, sender);
        }
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(ChatMessage chatMessage, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String sender = principal.getName();
        chatMessage.setSender(sender);
        chatMessage.setTimestamp(Instant.now());

        // Extract tenantId
        String tenantId = getTenantIdFromPrincipal(principal);
        if (tenantId == null) {
            // Handle error
            return;
        }
        chatMessage.setTenantId(tenantId);

        String sessionId = headerAccessor.getSessionId();
        chatMessage.setSessionId(sessionId);

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
                log.warn("Customer {} is not assigned to any agent under tenant {}", sender, tenantId);
                return;
            }
            chatMessage.setReceiver(receiver);
            chatMessage.setCustomerId(sender);
        } else if ("agent".equals(sessionInfo.getUserType())) {
            chatMessage.setSource(ChatMessage.SourceType.AGENT);
            // Get the customers assigned to this agent
            List<String> assignedCustomers = chatService.getAssignedCustomers(tenantId, sender);
            if (assignedCustomers.isEmpty()) {
                log.warn("Agent {} is not assigned to any customers under tenant {}", sender, tenantId);
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
