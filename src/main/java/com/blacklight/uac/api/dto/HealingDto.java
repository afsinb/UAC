package com.blacklight.uac.api.dto;

/**
 * HealingDto - Data Transfer Object for healing action information
 */
public class HealingDto {
    private String id;
    private String anomalyId;
    private String actionType;
    private String targetComponent;
    private String status;
    private String result;
    private long executedAt;
    private long executionTime;
    
    public HealingDto() {
        this.executedAt = System.currentTimeMillis();
    }
    
    public HealingDto(String id, String anomalyId, String actionType, String targetComponent) {
        this.id = id;
        this.anomalyId = anomalyId;
        this.actionType = actionType;
        this.targetComponent = targetComponent;
        this.executedAt = System.currentTimeMillis();
        this.status = "PENDING";
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getAnomalyId() { return anomalyId; }
    public void setAnomalyId(String anomalyId) { this.anomalyId = anomalyId; }
    
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    
    public String getTargetComponent() { return targetComponent; }
    public void setTargetComponent(String targetComponent) { this.targetComponent = targetComponent; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    
    public long getExecutedAt() { return executedAt; }
    public void setExecutedAt(long executedAt) { this.executedAt = executedAt; }
    
    public long getExecutionTime() { return executionTime; }
    public void setExecutionTime(long executionTime) { this.executionTime = executionTime; }
    
    @Override
    public String toString() {
        return String.format("HealingDto{id='%s', action='%s', target='%s', status='%s'}", 
            id, actionType, targetComponent, status);
    }
}
