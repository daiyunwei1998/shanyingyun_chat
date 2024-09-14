package org.service.customer.config;

import lombok.extern.slf4j.Slf4j;
import org.service.customer.models.SessionInfo;
import org.service.customer.service.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
public class WebSocketEventListener {

    private final ChatService chatService;

    public WebSocketEventListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        // We need to find the tenantId associated with this session
        // Since we don't have the Principal here, we need to search all tenants
        // Alternatively, store a mapping of sessionId to tenantId when the session is created

        // For simplicity, let's assume we have a method to get tenantId by sessionId
        String tenantId = chatService.getTenantIdBySessionId(sessionId);
        if (tenantId == null) {
            log.warn("No tenantId found for disconnected session ID {}", sessionId);
            return;
        }

        SessionInfo sessionInfo = chatService.getSessionInfo(tenantId, sessionId);

        if (sessionInfo != null) {
            String userId = sessionInfo.getUserId();
            String userType = sessionInfo.getUserType();

            // Remove userId to sessionId mapping
            chatService.deleteUserSessionMapping(tenantId, userId);

            // Delete session info
            chatService.deleteSessionInfo(tenantId, sessionId);

            log.info("User {} of type {} has disconnected and session data cleaned up under tenant {}", userId, userType, tenantId);

            // Additional cleanup if needed (e.g., reassign customers, remove agents from available list)
            if ("agent".equals(userType)) {
                // Remove agent from available list
                chatService.removeAgentFromAvailableList(tenantId, userId);
            }
        } else {
            log.warn("No session info found for disconnected session ID {} under tenant {}", sessionId, tenantId);
        }
    }
}
