package com.blacklight.uac.domain.healing;

import com.blacklight.uac.domain.common.DomainEntity;

/**
 * HealingAction - Domain Entity
 * Represents an action to heal the system
 */
public class HealingAction extends DomainEntity {
    private final String anomalyId;
    private final HealingType type;
    private final String targetComponent;
    private final int priority;
    private HealingStatus status;
    private String executionDetails;
    
    public enum HealingType {
        SCALE_UP("Scale up resources"),
        SCALE_DOWN("Scale down resources"),
        RESTART_SERVICE("Restart service"),
        ROLLBACK_VERSION("Rollback to previous version"),
        PATCH_CODE("Apply code patch"),
        CLEAR_CACHE("Clear cache/memory"),
        RESET_CONNECTION_POOL("Reset connection pool");
        
        private final String description;
        
        HealingType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum HealingStatus {
        PENDING("Waiting to execute"),
        EXECUTING("Currently executing"),
        SUCCESS("Successfully completed"),
        FAILED("Execution failed"),
        CANCELLED("Healing cancelled");
        
        private final String description;
        
        HealingStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public HealingAction(String anomalyId, HealingType type, String targetComponent, int priority) {
        super();
        this.anomalyId = anomalyId;
        this.type = type;
        this.targetComponent = targetComponent;
        this.priority = Math.max(1, Math.min(10, priority));
        this.status = HealingStatus.PENDING;
    }
    
    public void markExecuting() {
        this.status = HealingStatus.EXECUTING;
        markUpdated();
    }
    
    public void markSuccess(String details) {
        this.status = HealingStatus.SUCCESS;
        this.executionDetails = details;
        markUpdated();
    }
    
    public void markFailed(String reason) {
        this.status = HealingStatus.FAILED;
        this.executionDetails = reason;
        markUpdated();
    }
    
    // Getters
    public String getAnomalyId() {
        return anomalyId;
    }
    
    public HealingType getType() {
        return type;
    }
    
    public String getTargetComponent() {
        return targetComponent;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public HealingStatus getStatus() {
        return status;
    }
    
    public String getExecutionDetails() {
        return executionDetails;
    }
    
    public boolean isPending() {
        return status == HealingStatus.PENDING;
    }
    
    public boolean isSuccess() {
        return status == HealingStatus.SUCCESS;
    }
    
    public boolean isFailed() {
        return status == HealingStatus.FAILED;
    }
}

