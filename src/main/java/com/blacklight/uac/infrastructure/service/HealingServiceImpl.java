package com.blacklight.uac.infrastructure.service;

import com.blacklight.uac.domain.healing.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * HealingServiceImpl - Healing Service Implementation
 * Manages healing actions and strategy execution
 */
public class HealingServiceImpl implements HealingService {
    private final Map<String, HealingAction> actionHistory = new ConcurrentHashMap<>();
    private final List<HealingStrategy> strategies = new CopyOnWriteArrayList<>();
    
    @Override
    public HealingAction createHealingAction(String anomalyId, HealingAction.HealingType type, 
                                              String targetComponent) {
        HealingAction action = new HealingAction(anomalyId, type, targetComponent, 
            calculatePriority(type));
        actionHistory.put(action.getId(), action);
        return action;
    }
    
    @Override
    public HealingResult executeHealing(HealingAction action, HealingContext context) {
        action.markExecuting();
        long startTime = System.currentTimeMillis();
        
        try {
            HealingStrategy strategy = findBestStrategy(action.getAnomalyId(), context);
            
            if (strategy == null) {
                action.markFailed("No suitable healing strategy found");
                return new HealingResult(action.getId(), false, 
                    "No strategy available", "", 
                    System.currentTimeMillis() - startTime);
            }
            
            HealingResult result = strategy.execute(action, context);
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (result.isSuccess()) {
                action.markSuccess("Healing completed successfully");
                return new HealingResult(action.getId(), true, 
                    "Healing successful", 
                    String.format("Strategy: %s, Time: %dms", 
                        strategy.getHealingType(), executionTime),
                    executionTime);
            } else {
                action.markFailed("Strategy execution failed");
                return new HealingResult(action.getId(), false, 
                    "Healing failed", 
                    String.format("Strategy: %s failed", strategy.getHealingType()),
                    executionTime);
            }
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            action.markFailed("Exception: " + e.getMessage());
            return new HealingResult(action.getId(), false, 
                "Healing failed with exception", e.getMessage(), executionTime);
        }
    }
    
    @Override
    public HealingStrategy findBestStrategy(String anomalyId, HealingContext context) {
        return strategies.stream()
            .filter(s -> s.canHandle(anomalyId, context))
            .max(Comparator.comparingInt(HealingStrategy::getPriority))
            .orElse(null);
    }
    
    @Override
    public List<HealingAction> getHealingHistory(String anomalyId) {
        return actionHistory.values().stream()
            .filter(a -> a.getAnomalyId().equals(anomalyId))
            .sorted(Comparator.comparing(HealingAction::getCreatedAt).reversed())
            .toList();
    }
    
    @Override
    public void registerStrategy(HealingStrategy strategy) {
        strategies.add(strategy);
        strategies.sort(Comparator.comparingInt(HealingStrategy::getPriority).reversed());
    }
    
    private int calculatePriority(HealingAction.HealingType type) {
        return switch (type) {
            case RESTART_SERVICE -> 8;
            case SCALE_UP -> 7;
            case ROLLBACK_VERSION -> 9;
            case PATCH_CODE -> 6;
            case CLEAR_CACHE -> 5;
            case RESET_CONNECTION_POOL -> 4;
            case SCALE_DOWN -> 3;
        };
    }
}
