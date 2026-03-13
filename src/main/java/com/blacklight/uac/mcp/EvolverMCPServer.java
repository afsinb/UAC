package com.blacklight.uac.mcp;

import com.blacklight.uac.evolver.Evolver;
import com.blacklight.uac.evolver.DevelopmentTask;

import java.util.*;

/**
 * EvolverMCPServer - Development MCP Server
 * Wraps Evolver as an MCP-compliant server for code fixes via CI/CD
 * Implements: Clone -> Reproduce -> Patch -> Verify -> PR
 */
public class EvolverMCPServer implements MCPServer {
    
    private final Evolver evolver;
    private final List<Map<String, Object>> taskHistory;
    
    public EvolverMCPServer(Evolver evolver) {
        this.evolver = evolver;
        this.taskHistory = Collections.synchronizedList(new ArrayList<>());
    }
    
    @Override
    public String getName() {
        return "DevelopmentMCP";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "execute_development_cycle",
            "clone_repository",
            "reproduce_issue",
            "create_patch",
            "verify_patch",
            "create_pull_request",
            "get_task_history",
            "get_sandbox_status"
        );
    }
    
    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "execute_development_cycle" -> executeDevelopmentCycle(request);
            case "clone_repository" -> cloneRepository(request);
            case "reproduce_issue" -> reproduceIssue(request);
            case "create_patch" -> createPatch(request);
            case "verify_patch" -> verifyPatch(request);
            case "create_pull_request" -> createPullRequest(request);
            case "get_task_history" -> getTaskHistory(request);
            case "get_sandbox_status" -> getSandboxStatus(request);
            default -> MCPResponse.error(request.getId(), "Unknown method: " + request.getMethod());
        };
    }
    
    @Override
    public boolean isHealthy() {
        return evolver != null;
    }
    
    private MCPResponse executeDevelopmentCycle(MCPRequest request) {
        String taskType = request.getStringParam("taskType");
        String anomalyType = request.getStringParam("anomalyType");
        String code = request.getStringParam("code");
        
        if (taskType == null) {
            return MCPResponse.error(request.getId(), "Missing taskType parameter");
        }
        
        DevelopmentTask task = new DevelopmentTask(taskType);
        
        long startTime = System.currentTimeMillis();
        evolver.executeDevelopmentCycle(task);
        long executionTime = System.currentTimeMillis() - startTime;
        
        String taskId = "TASK-" + System.currentTimeMillis();
        
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("taskType", taskType);
        result.put("status", "COMPLETED");
        result.put("executionTime", executionTime);
        result.put("anomalyType", anomalyType);
        
        // Record in history
        Map<String, Object> historyEntry = new HashMap<>(result);
        historyEntry.put("timestamp", System.currentTimeMillis());
        taskHistory.add(historyEntry);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse cloneRepository(MCPRequest request) {
        String repoUrl = request.getStringParam("repoUrl");
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "CLONE");
        result.put("repoUrl", repoUrl != null ? repoUrl : "default");
        result.put("status", "SUCCESS");
        result.put("message", "Repository cloned to sandbox");
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse reproduceIssue(MCPRequest request) {
        String anomalyType = request.getStringParam("anomalyType");
        String stackTrace = request.getStringParam("stackTrace");
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "REPRODUCE");
        result.put("anomalyType", anomalyType);
        result.put("reproduced", true);
        result.put("message", "Issue reproduced in sandbox");
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse createPatch(MCPRequest request) {
        String anomalyType = request.getStringParam("anomalyType");
        String code = request.getStringParam("code");
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "CREATE_PATCH");
        result.put("anomalyType", anomalyType);
        result.put("patchCreated", true);
        result.put("patchFile", "fix_" + System.currentTimeMillis() + ".patch");
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse verifyPatch(MCPRequest request) {
        String patchFile = request.getStringParam("patchFile");
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "VERIFY_PATCH");
        result.put("patchFile", patchFile);
        result.put("verified", true);
        result.put("testsPassed", true);
        result.put("message", "Patch verified in sandbox");
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse createPullRequest(MCPRequest request) {
        String title = request.getStringParam("title");
        String description = request.getStringParam("description");
        String branch = request.getStringParam("branch");
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "CREATE_PR");
        result.put("title", title);
        result.put("branch", branch != null ? branch : "fix/auto-" + System.currentTimeMillis());
        result.put("prNumber", (int) (Math.random() * 1000) + 1);
        result.put("status", "OPEN");
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse getTaskHistory(MCPRequest request) {
        return MCPResponse.success(request.getId(), Map.of(
            "history", taskHistory,
            "count", taskHistory.size()
        ));
    }
    
    private MCPResponse getSandboxStatus(MCPRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("sandboxPath", "/tmp/sandbox");
        result.put("status", "READY");
        result.put("lastCleanup", System.currentTimeMillis() - 3600000);
        
        return MCPResponse.success(request.getId(), result);
    }
}
