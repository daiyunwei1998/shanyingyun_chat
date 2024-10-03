package org.service.customer.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import java.time.Instant;
import java.io.Serializable;

@Data
@ToString
public class ChatMessage implements Serializable {
    @JsonProperty("session_id")
    private String sessionId;
    private MessageType type;
    private String content;
    private String sender;
    @JsonProperty("sender_name")
    private String senderName;
    private String receiver;
    @JsonProperty("tenant_id")
    private String tenantId;
    private Instant timestamp;
    private SourceType source;
    @JsonProperty("user_type")
    private String userType;
    @JsonProperty("customer_id")
    private String customerId;

    public ChatMessage() {
    }

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
    }

    public enum SourceType {
        USER,
        AGENT,
        AI
    }
}
