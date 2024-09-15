package org.service.customer.controller.v1;

import lombok.extern.slf4j.Slf4j;
import org.service.customer.models.ChatMessage;
import org.service.customer.models.SessionInfo;
import org.service.customer.service.ChatService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
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
    private final RabbitAdmin rabbitAdmin;
    private final TopicExchange topicExchange;

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatService chatService, RabbitAdmin rabbitAdmin, TopicExchange topicExchange) {
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
        this.rabbitAdmin = rabbitAdmin;
        this.topicExchange = topicExchange;
    }

    @MessageMapping("/chat.addUser")
    public void addUser(ChatMessage chatMessage, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
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

        // Dynamically create and bind the queue for this tenant
        createAndBindTenantQueue(tenantId);

        if ("agent".equals(userType)) {
            log.info("Agent {} is now available under tenant {}", userId, tenantId);
            chatService.addAgentToAvailableList(tenantId, userId);
        }

        // Send a notification to all agents that a customer has joined
        if ("customer".equals(userType)) {
            messagingTemplate.convertAndSend("/topic/" + tenantId + ".new_customer", chatMessage);
            log.info("Message sent to: /topic/" + tenantId + ".new_customer");
        }
    }

    private void createAndBindTenantQueue(String tenantId) {
        String stompExchange = "amq.topic"; // Default STOMP broker relay exchange

        // Create tenant-specific queues
        Queue newCustomerQueue = new Queue(tenantId + ".new_customer", true);
        Queue customerMessageQueue = new Queue(tenantId + ".customer_message", true);
        rabbitAdmin.declareQueue(newCustomerQueue);
        rabbitAdmin.declareQueue(customerMessageQueue);

        // Bind queues to the 'amq.topic' exchange with appropriate routing keys
        Binding newCustomerBinding = BindingBuilder.bind(newCustomerQueue)
                .to(new TopicExchange(stompExchange))
                .with(tenantId + ".new_customer");
        rabbitAdmin.declareBinding(newCustomerBinding);

        Binding customerMessageBinding = BindingBuilder.bind(customerMessageQueue)
                .to(new TopicExchange(stompExchange))
                .with(tenantId + ".customer_message");
        rabbitAdmin.declareBinding(customerMessageBinding);

        log.info("Queues {} and {} bound to exchange {} with routing keys {} and {}",
                newCustomerQueue.getName(), customerMessageQueue.getName(),
                stompExchange, tenantId + ".new_customer", tenantId + ".customer_message");
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(ChatMessage chatMessage, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
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

            // Broadcast the message to all available agents
            messagingTemplate.convertAndSend("/topic/" + tenantId + ".customer_message", chatMessage);

        } else if ("agent".equals(sessionInfo.getUserType())) {
            chatMessage.setSource(ChatMessage.SourceType.AGENT);

            // Send the agent's reply directly to the customer
            messagingTemplate.convertAndSendToUser(chatMessage.getReceiver(), "/queue/messages", chatMessage);
        }

        // Save the message
        chatService.saveMessage(chatMessage);
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
