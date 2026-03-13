package com.blacklight.uac.application.usecase;

import com.blacklight.uac.domain.healing.*;

/**
 * HealingExecutionUseCase - Application Service
 * Use case for executing healing actions
 */
public class HealingExecutionUseCase {
    private final HealingService healingService;
    
    public HealingExecutionUseCase(HealingService healingService) {
        this.healingService = healingService;
    }
    
    /**
     * Execute healing based on anomaly diagnosis
     */
    public HealingExecutionResult execute(HealingExecutionRequest request) {
        // Get best healing strategy
        HealingContext context = request.getHealingContext();
        HealingStrategy strategy = healingService.findBestStrategy(
            request.getAnomalyId(), context);
        
        if (strategy == null) {
            return new HealingExecutionResult(false, "No suitable healing strategy found", null);
        }
        
        // Create healing action
        HealingAction action = healingService.createHealingAction(
            request.getAnomalyId(),
            request.getHealingType(),
            request.getTargetComponent()
        );
        
        // Execute healing
        long startTime = System.currentTimeMillis();
        HealingResult result = healingService.executeHealing(action, context);
        long executionTime = System.currentTimeMillis() - startTime;
        
        // Update action status
        if (result.isSuccess()) {
            action.markSuccess(result.getExecutionLog());
        } else {
            action.markFailed(result.getMessage());
        }
        
        return new HealingExecutionResult(result.isSuccess(), result.getMessage(), 
            result, executionTime);
    }
}

