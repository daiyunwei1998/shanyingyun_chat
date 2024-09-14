package org.service.customer.service;

import lombok.extern.slf4j.Slf4j;
import org.service.customer.models.ChatMessage;
import org.service.customer.models.SessionInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Queue<String>> tenantAgentQueueMap = new HashMap<>();

    public ChatService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Save session information to Redis
    public void saveSessionInfo(SessionInfo sessionInfo) {
        String key = "tenant:" + sessionInfo.getTenantId() + ":session:" + sessionInfo.getSessionId();
        redisTemplate.opsForValue().set(key, sessionInfo, Duration.ofHours(1));
        log.info("Saved session info for user {} with session ID {} under tenant {}", sessionInfo.getUserId(), sessionInfo.getSessionId(), sessionInfo.getTenantId());
    }

    // Retrieve session information from Redis
    public SessionInfo getSessionInfo(String tenantId, String sessionId) {
        String key = "tenant:" + tenantId + ":session:" + sessionId;
        return (SessionInfo) redisTemplate.opsForValue().get(key);
    }

    // Delete session information from Redis
    public void deleteSessionInfo(String tenantId, String sessionId) {
        String key = "tenant:" + tenantId + ":session:" + sessionId;
        redisTemplate.delete(key);
        log.info("Deleted session info for session ID {} under tenant {}", sessionId, tenantId);
    }

    // Map userId to sessionId for quick lookup
    public void saveUserSessionMapping(String tenantId, String userId, String sessionId) {
        String key = "tenant:" + tenantId + ":user_session:" + userId;
        redisTemplate.opsForValue().set(key, sessionId, Duration.ofHours(1));
    }

    public String getSessionIdByUserId(String tenantId, String userId) {
        String key = "tenant:" + tenantId + ":user_session:" + userId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteUserSessionMapping(String tenantId, String userId) {
        String key = "tenant:" + tenantId + ":user_session:" + userId;
        redisTemplate.delete(key);
    }

    // Assign customer to agent
    public void assignCustomerToAgent(String tenantId, String agentId, String customerId) {
        // Retrieve agent's session info
        SessionInfo agentSessionInfo = getSessionInfoByUserId(tenantId, agentId);
        if (agentSessionInfo != null) {
            // Assume agent handles multiple customers
            List<String> assignedCustomers = agentSessionInfo.getAssignedTo() != null
                    ? new ArrayList<>(Arrays.asList(agentSessionInfo.getAssignedTo().split(",")))
                    : new ArrayList<>();
            assignedCustomers.add(customerId);
            agentSessionInfo.setAssignedTo(String.join(",", assignedCustomers));
            saveSessionInfo(agentSessionInfo);
        } else {
            log.warn("No session info found for agent {}", agentId);
        }
    }

    // Get assigned agent for customer
    public String getAssignedAgent(String tenantId, String customerId) {
        SessionInfo customerSessionInfo = getSessionInfoByUserId(tenantId, customerId);
        return customerSessionInfo != null ? customerSessionInfo.getAssignedTo() : null;
    }

    // Get assigned customers for agent
    public List<String> getAssignedCustomers(String tenantId, String agentId) {
        SessionInfo agentSessionInfo = getSessionInfoByUserId(tenantId, agentId);
        if (agentSessionInfo != null && agentSessionInfo.getAssignedTo() != null) {
            return Arrays.asList(agentSessionInfo.getAssignedTo().split(","));
        }
        return Collections.emptyList();
    }

    // Get SessionInfo by userId
    public SessionInfo getSessionInfoByUserId(String tenantId, String userId) {
        String sessionId = getSessionIdByUserId(tenantId, userId);
        if (sessionId != null) {
            return getSessionInfo(tenantId, sessionId);
        }
        return null;
    }

    // Save chat message
    public void saveMessage(ChatMessage chatMessage) {
        saveMessageToRedis(chatMessage);
        saveMessageToMongo(chatMessage);
    }

    // Save message to Redis
    private void saveMessageToRedis(ChatMessage chatMessage) {
        String key = "tenant:" + chatMessage.getTenantId() + ":chat:customer_messages:" + chatMessage.getCustomerId();
        redisTemplate.opsForList().rightPush(key, chatMessage);
        log.info("Saved message to Redis under key: {}", key);
    }

    // Placeholder for saving message to MongoDB
    private void saveMessageToMongo(ChatMessage chatMessage) {
        // Implement saving to MongoDB
    }

    // Get messages for a customer
    public List<ChatMessage> getMessagesForCustomer(String tenantId, String customerId) {
        String key = "tenant:" + tenantId + ":chat:customer_messages:" + customerId;
        List<Object> messages = redisTemplate.opsForList().range(key, 0, -1);
        if (messages != null) {
            return messages.stream()
                    .map(message -> (ChatMessage) message)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // Agent availability management
    public void addAgentToAvailableList(String tenantId, String agentId) {
        tenantAgentQueueMap.computeIfAbsent(tenantId, k -> new LinkedList<>()).add(agentId);
    }

    public String findAvailableAgent(String tenantId) {
        Queue<String> agentQueue = tenantAgentQueueMap.get(tenantId);
        if (agentQueue != null && !agentQueue.isEmpty()) {
            return agentQueue.poll(); // Get next available agent
        }
        return null; // No available agents
    }

    public void removeAgentFromAvailableList(String tenantId, String agentId) {
        Queue<String> agentQueue = tenantAgentQueueMap.get(tenantId);
        if (agentQueue != null) {
            agentQueue.remove(agentId);
        }
    }

    // Session-Tenant mapping
    public void saveSessionTenantMapping(String sessionId, String tenantId) {
        String key = "session_tenant_mapping:" + sessionId;
        redisTemplate.opsForValue().set(key, tenantId, Duration.ofHours(1));
    }

    public String getTenantIdBySessionId(String sessionId) {
        String key = "session_tenant_mapping:" + sessionId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteSessionTenantMapping(String sessionId) {
        String key = "session_tenant_mapping:" + sessionId;
        redisTemplate.delete(key);
    }

    // Getter for RedisTemplate
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }
}
