package com.blacklight.uac.api.rest;

import com.blacklight.uac.api.dto.HealingDto;
import com.blacklight.uac.domain.healing.HealingAction;
import com.blacklight.uac.domain.healing.HealingContext;
import com.blacklight.uac.domain.healing.HealingResult;
import com.blacklight.uac.domain.healing.HealingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HealingController - REST API for healing actions
 * Provides endpoints for managing and executing healing actions
 */
public class HealingController {
    private final HealingService healingService;
    private final Map<String, HealingDto> healingHistory;
    
    public HealingController(HealingService healingService) {
        this.healingService = healingService;
        this.healingHistory = new ConcurrentHashMap<>();
    }
    
    /**
     * GET /api/healing/actions
     * Returns list of all healing actions
     */
    public List<HealingDto> getAllActions() {
        return new ArrayList<>(healingHistory.values());
    }
    
    /**
     * GET /api/healing/actions/{id}
     * Returns specific healing action by ID
     */
    public HealingDto getAction(String id) {
        return healingHistory.get(id);
    }
    
    /**
     * GET /api/healing/history/{anomalyId}
     * Returns healing history for a specific anomaly
     */
    public List<HealingDto> getHealingHistory(String anomalyId) {
        return healingHistory.values().stream()
            .filter(h -> anomalyId.equals(h.getAnomalyId()))
            .toList();
    }
    
    /**
     * POST /api/healing/execute
     * Executes a healing action
     */
    public HealingDto executeHealing(String anomalyId, String actionType, String targetComponent) {
        String actionId = "HEAL-" + System.currentTimeMillis();
        
        HealingDto healingDto = new HealingDto(actionId, anomalyId, actionType, targetComponent);
        healingDto.setStatus("EXECUTING");
        healingHistory.put(actionId, healingDto);
        
        // Simulate healing execution
        long startTime = System.currentTimeMillis();
        
        try {
            // Create healing action
            HealingAction.HealingType type = parseActionType(actionType);
            HealingAction action = new HealingAction(anomalyId, type, targetComponent, 5);
            
            // Create context
            HealingContext context = new HealingContext.Builder()
                .withSystemState("normal")
                .build();
            
            // Execute healing
            HealingResult result = healingService.executeHealing(action, context);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            healingDto.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
            healingDto.setResult(result.getMessage());
            healingDto.setExecutionTime(executionTime);
            
        } catch (Exception e) {
            healingDto.setStatus("ERROR");
            healingDto.setResult("Error: " + e.getMessage());
        }
        
        return healingDto;
    }
    
    /**
     * POST /api/healing/rollback/{actionId}
     * Rolls back a healing action
     */
    public HealingDto rollbackAction(String actionId) {
        HealingDto original = healingHistory.get(actionId);
        if (original == null) {
            return null;
        }
        
        String rollbackId = "ROLLBACK-" + System.currentTimeMillis();
        HealingDto rollback = new HealingDto(rollbackId, original.getAnomalyId(), 
            "ROLLBACK", original.getTargetComponent());
        rollback.setStatus("EXECUTING");
        healingHistory.put(rollbackId, rollback);
        
        // Simulate rollback
        rollback.setStatus("SUCCESS");
        rollback.setResult("Rollback completed successfully");
        
        return rollback;
    }
    
    private HealingAction.HealingType parseActionType(String actionType) {
        return switch (actionType.toUpperCase()) {
            case "SCALE", "SCALE_UP" -> HealingAction.HealingType.SCALE_UP;
            case "RESTART", "RESTART_SERVICE" -> HealingAction.HealingType.RESTART_SERVICE;
            case "ROLLBACK", "ROLLBACK_VERSION" -> HealingAction.HealingType.ROLLBACK_VERSION;
            case "PATCH", "PATCH_CODE" -> HealingAction.HealingType.PATCH_CODE;
            case "CLEAR_CACHE" -> HealingAction.HealingType.CLEAR_CACHE;
            case "RESET_CONNECTION" -> HealingAction.HealingType.RESET_CONNECTION_POOL;
            case "SCALE_DOWN" -> HealingAction.HealingType.SCALE_DOWN;
            default -> HealingAction.HealingType.RESTART_SERVICE;
        };
    }
}
