package com.blacklight.uac.domain.healing;

/**
 * HealingStrategy - Domain Interface
 * Strategy pattern for different healing approaches
 */
public interface HealingStrategy {
    
    /**
     * Get the type of healing this strategy handles
     */
    HealingAction.HealingType getHealingType();
    
    /**
     * Check if this strategy can handle the given anomaly
     */
    boolean canHandle(String anomalyId, HealingContext context);
    
    /**
     * Execute the healing strategy
     */
    HealingResult execute(HealingAction action, HealingContext context);
    
    /**
     * Get priority of this strategy (1-10, where 10 is highest)
     */
    int getPriority();
    
    /**
     * Check if this strategy is idempotent (safe to execute multiple times)
     */
    boolean isIdempotent();
}

