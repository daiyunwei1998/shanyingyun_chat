package org.service.customer.dto.chat;

import lombok.Data;
import java.time.Instant;

@Data
public class PickUpInfo {
    private String agent;
    private String customer;
    private String type;
    private String tenant_id;
    private Instant timestamp;
}

