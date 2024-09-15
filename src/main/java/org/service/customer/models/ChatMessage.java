package org.service.customer.models;

import lombok.Data;
import java.io.Serializable;
import java.time.Instant;

@Data
public class ChatMessage implements Serializable {
    private String sessionId;
    private MessageType type;
    private String content;
    private String sender;
    private String receiver;
    private String tenantId;
    private Instant timestamp;
    private SourceType source;
    private String userType;
    private String customerId;

    public ChatMessage() {
        this.timestamp = Instant.now();
    }

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }

    public enum SourceType {
        USER,
        AGENT,
        AI
    }
}
