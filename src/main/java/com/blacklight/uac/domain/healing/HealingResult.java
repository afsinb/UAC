package com.blacklight.uac.domain.healing;

/**
 * HealingResult - Domain Value Object
 * Result of executing a healing action
 */
public class HealingResult {
    private final String actionId;
    private final boolean success;
    private final String message;
    private final String executionLog;
    private final long executionTime;
    
    public HealingResult(String actionId, boolean success, String message, 
                        String executionLog, long executionTime) {
        this.actionId = actionId;
        this.success = success;
        this.message = message;
        this.executionLog = executionLog;
        this.executionTime = executionTime;
    }
    
    public String getActionId() {
        return actionId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getExecutionLog() {
        return executionLog;
    }
    
    public long getExecutionTime() {
        return executionTime;
    }
    
    @Override
    public String toString() {
        return String.format("HealingResult{actionId='%s', success=%s, time=%dms}", 
            actionId, success, executionTime);
    }
}

