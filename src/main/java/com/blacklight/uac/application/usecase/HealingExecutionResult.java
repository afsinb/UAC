package com.blacklight.uac.application.usecase;

import com.blacklight.uac.domain.healing.HealingResult;

/**
 * HealingExecutionResult - Application DTO
 * Result of healing execution use case
 */
public class HealingExecutionResult {
    private final boolean success;
    private final String message;
    private final HealingResult healingResult;
    private final long executionTime;
    
    public HealingExecutionResult(boolean success, String message, 
                                 HealingResult healingResult, long executionTime) {
        this.success = success;
        this.message = message;
        this.healingResult = healingResult;
        this.executionTime = executionTime;
    }
    
    public HealingExecutionResult(boolean success, String message, HealingResult result) {
        this(success, message, result, 0);
    }
    
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public HealingResult getHealingResult() { return healingResult; }
    public long getExecutionTime() { return executionTime; }
}

