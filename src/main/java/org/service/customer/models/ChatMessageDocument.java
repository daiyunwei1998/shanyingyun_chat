package org.service.customer.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "chat_messages")
public class ChatMessageDocument {
    @Id
    private String id;
    private String customerId;
    private String sender;
    private String receiver;
    private String content;
    private Instant timestamp;
    private ChatMessage.MessageType type;
    private ChatMessage.SourceType source; // Include source
    private String tenant;

    // Getters and setters
}
