package org.service.customer.service;

import lombok.extern.slf4j.Slf4j;
import org.service.customer.models.ChatMessage;
import org.service.customer.models.SessionInfo;
import org.service.customer.repository.ChatMessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private final MongoTemplate mongoTemplate;

    public ChatService(RedisTemplate<String, Object> redisTemplate, MongoTemplate mongoTemplate) {
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
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

    // Save chat message to both Redis and MongoDB
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

    // Save message to MongoDB
    private void saveMessageToMongo(ChatMessage chatMessage) {
        String collectionName = "chat_messages_" + chatMessage.getTenantId();
        mongoTemplate.save(chatMessage, collectionName);
    }

    // Get available agents for a tenant
    public List<String> getAvailableAgents(String tenantId) {
        Queue<String> agentQueue = tenantAgentQueueMap.get(tenantId);
        return agentQueue != null ? new ArrayList<>(agentQueue) : Collections.emptyList();
    }

    // Add an agent to the available list for a tenant
    public void addAgentToAvailableList(String tenantId, String agentId) {
        tenantAgentQueueMap.computeIfAbsent(tenantId, k -> new LinkedList<>()).add(agentId);
    }

    // Remove an agent from the available list for a tenant
    public void removeAgentFromAvailableList(String tenantId, String agentId) {
        Queue<String> agentQueue = tenantAgentQueueMap.get(tenantId);
        if (agentQueue != null) {
            agentQueue.remove(agentId);
        }
    }

    // Save session to user mapping
    public void saveUserSessionMapping(String tenantId, String userId, String sessionId) {
        String key = "tenant:" + tenantId + ":user_session:" + userId;
        redisTemplate.opsForValue().set(key, sessionId, Duration.ofHours(1));
    }

    // Map session to tenant
    public void saveSessionTenantMapping(String sessionId, String tenantId) {
        String key = "session_tenant_mapping:" + sessionId;
        redisTemplate.opsForValue().set(key, tenantId, Duration.ofHours(1));
    }

    public String getTenantIdBySessionId(String sessionId) {
        String key = "session_tenant_mapping:" + sessionId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteUserSessionMapping(String tenantId, String userId) {
        String key = "tenant:" + tenantId + ":user_session:" + userId;
        redisTemplate.delete(key);
    }

    public void deleteSessionInfo(String tenantId, String sessionId) {
        String key = "tenant:" + tenantId + ":session:" + sessionId;
        redisTemplate.delete(key);
        log.info("Deleted session info for session ID {} under tenant {}", sessionId, tenantId);
    }

}
