package org.service.customer.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TaskCompletionEvent {
    @JsonProperty("tenant_id")
    private String tenantId;
    private String filename;

    public TaskCompletionEvent(String tenantId, String filename) {
        this.tenantId = tenantId;
        this.filename = filename;
    }
}

