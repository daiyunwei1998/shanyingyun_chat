package org.service.customer.dto;

public class TaskCompletionMessage {
    private String tenantId;
    private String message;
    private String file;

    // Default constructor (required for deserialization)
    public TaskCompletionMessage() {}

    // Getters and setters
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
}

