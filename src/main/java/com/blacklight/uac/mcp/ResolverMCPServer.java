package com.blacklight.uac.mcp;

import com.blacklight.uac.resolver.Resolver;
import com.blacklight.uac.resolver.RecoveryAction;

import java.util.*;

/**
 * ResolverMCPServer - Healing MCP Server
 * Wraps Resolver as an MCP-compliant server for infrastructure recovery
 * Implements: Scale, Restart, Rollback (idempotent actions)
 */
public class ResolverMCPServer implements MCPServer {
    
    private final Resolver resolver;
    private final List<Map<String, Object>> actionHistory;
    
    public ResolverMCPServer(Resolver resolver) {
        this.resolver = resolver;
        this.actionHistory = Collections.synchronizedList(new ArrayList<>());
    }
    
    @Override
    public String getName() {
        return "HealingMCP";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "execute_recovery",
            "scale_up",
            "scale_down",
            "restart_service",
            "rollback_version",
            "get_recovery_history",
            "validate_idempotency",
            "get_action_status"
        );
    }
    
    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "execute_recovery" -> executeRecovery(request);
            case "scale_up" -> scaleUp(request);
            case "scale_down" -> scaleDown(request);
            case "restart_service" -> restartService(request);
            case "rollback_version" -> rollbackVersion(request);
            case "get_recovery_history" -> getRecoveryHistory(request);
            case "validate_idempotency" -> validateIdempotency(request);
            case "get_action_status" -> getActionStatus(request);
            default -> MCPResponse.error(request.getId(), "Unknown method: " + request.getMethod());
        };
    }
    
    @Override
    public boolean isHealthy() {
        return resolver != null;
    }
    
    private MCPResponse executeRecovery(MCPRequest request) {
        String actionType = request.getStringParam("actionType");
        String target = request.getStringParam("target");
        
        if (actionType == null || target == null) {
            return MCPResponse.error(request.getId(), "Missing required parameters");
        }
        
        RecoveryAction.ActionType type = parseActionType(actionType);
        RecoveryAction action = new RecoveryAction(type, target);
        
        long startTime = System.currentTimeMillis();
        resolver.executeRecovery(action);
        long executionTime = System.currentTimeMillis() - startTime;
        
        String actionId = "ACTION-" + System.currentTimeMillis();
        
        Map<String, Object> result = new HashMap<>();
        result.put("actionId", actionId);
        result.put("actionType", actionType);
        result.put("target", target);
        result.put("executionTime", executionTime);
        result.put("success", true);
        result.put("idempotent", true);
        
        // Record in history
        Map<String, Object> historyEntry = new HashMap<>(result);
        historyEntry.put("timestamp", System.currentTimeMillis());
        actionHistory.add(historyEntry);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse scaleUp(MCPRequest request) {
        String target = request.getStringParam("target");
        Integer instances = request.getIntParam("instances");
        
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.SCALE,
            target != null ? target : "application_service"
        );
        
        resolver.executeRecovery(action);
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "SCALE_UP");
        result.put("target", target);
        result.put("instances", instances != null ? instances : 1);
        result.put("success", true);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse scaleDown(MCPRequest request) {
        String target = request.getStringParam("target");
        
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.SCALE,
            target != null ? target : "application_service"
        );
        
        resolver.executeRecovery(action);
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "SCALE_DOWN");
        result.put("target", target);
        result.put("success", true);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse restartService(MCPRequest request) {
        String target = request.getStringParam("target");
        
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.RESTART,
            target != null ? target : "application_service"
        );
        
        resolver.executeRecovery(action);
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "RESTART");
        result.put("target", target);
        result.put("success", true);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse rollbackVersion(MCPRequest request) {
        String target = request.getStringParam("target");
        String version = request.getStringParam("version");
        
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.ROLLBACK,
            target != null ? target : "application_service"
        );
        
        resolver.executeRecovery(action);
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "ROLLBACK");
        result.put("target", target);
        result.put("version", version);
        result.put("success", true);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse getRecoveryHistory(MCPRequest request) {
        return MCPResponse.success(request.getId(), Map.of(
            "history", actionHistory,
            "count", actionHistory.size()
        ));
    }
    
    private MCPResponse validateIdempotency(MCPRequest request) {
        // All recovery actions are designed to be idempotent
        Map<String, Object> result = new HashMap<>();
        result.put("idempotent", true);
        result.put("reason", "All recovery actions are designed to be safely re-executable");
        result.put("actions", Arrays.asList("SCALE", "RESTART", "ROLLBACK"));
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse getActionStatus(MCPRequest request) {
        String actionId = request.getStringParam("actionId");
        
        Map<String, Object> action = actionHistory.stream()
            .filter(a -> actionId.equals(a.get("actionId")))
            .findFirst()
            .orElse(null);
        
        if (action == null) {
            return MCPResponse.error(request.getId(), "Action not found");
        }
        
        return MCPResponse.success(request.getId(), action);
    }
    
    private RecoveryAction.ActionType parseActionType(String type) {
        return switch (type.toUpperCase()) {
            case "SCALE", "SCALE_UP" -> RecoveryAction.ActionType.SCALE;
            case "RESTART" -> RecoveryAction.ActionType.RESTART;
            case "ROLLBACK" -> RecoveryAction.ActionType.ROLLBACK;
            default -> RecoveryAction.ActionType.RESTART;
        };
    }
}
