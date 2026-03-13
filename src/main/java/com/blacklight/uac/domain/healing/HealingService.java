package com.blacklight.uac.domain.healing;

/**
 * HealingService - Domain Service Interface
 * Coordinates healing operations
 */
public interface HealingService {
    
    /**
     * Create a healing action for an anomaly
     */
    HealingAction createHealingAction(String anomalyId, HealingAction.HealingType type, 
                                       String targetComponent);
    
    /**
     * Execute pending healing actions
     */
    HealingResult executeHealing(HealingAction action, HealingContext context);
    
    /**
     * Find best healing strategy for anomaly
     */
    HealingStrategy findBestStrategy(String anomalyId, HealingContext context);
    
    /**
     * Get healing history for anomaly
     */
    java.util.List<HealingAction> getHealingHistory(String anomalyId);
    
    /**
     * Register new healing strategy
     */
    void registerStrategy(HealingStrategy strategy);
}

