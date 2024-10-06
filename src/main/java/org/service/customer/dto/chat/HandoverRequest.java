package org.service.customer.dto.chat;

import lombok.Data;

@Data
public class HandoverRequest {
    private String sessionId;
    private String customerId;
    private String tenantId;
    private String summary;
    private String reason;
}
