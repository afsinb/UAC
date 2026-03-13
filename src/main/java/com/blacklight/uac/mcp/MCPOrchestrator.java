package com.blacklight.uac.mcp;

import com.blacklight.uac.ai.AIDecisionEngine;

import java.util.*;

/**
 * MCPOrchestrator - Coordinates MCP servers and AI decision engine
 * Provides unified interface for AI-powered autonomous operations
 */
public class MCPOrchestrator {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final Map<String, MCPServer> servers;
    private final AIDecisionEngine aiEngine;
    private int requestCounter = 0;
    
    public MCPOrchestrator() {
        this.servers = new HashMap<>();
        this.aiEngine = new AIDecisionEngine();
        initializeServers();
    }
    
    /**
     * Initialize MCP servers
     */
    private void initializeServers() {
        registerServer(new CodeAnalysisMCPServer());
        registerServer(new DeploymentMCPServer());
        
        System.out.println(CYAN + "🔌 MCP Orchestrator initialized with " + servers.size() + " servers" + RESET);
    }
    
    /**
     * Register an MCP server
     */
    public void registerServer(MCPServer server) {
        servers.put(server.getName(), server);
        System.out.println(GREEN + "  ✓ Registered: " + server.getName() + 
                          " (" + server.getCapabilities().size() + " capabilities)" + RESET);
    }
    
    /**
     * Get AI decision for an anomaly
     */
    public AIDecisionEngine.AIDecision getAIDecision(String anomalyType, Map<String, Object> context) {
        System.out.println();
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "🤖 AI DECISION ENGINE" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        return aiEngine.analyze(anomalyType, context);
    }
    
    /**
     * Analyze code using MCP
     */
    public Map<String, Object> analyzeCode(String code) {
        MCPServer server = servers.get("CodeAnalysis");
        if (server == null) {
            return Map.of("error", "CodeAnalysis server not available");
        }
        
        MCPServer.MCPRequest request = createRequest("analyze_code", Map.of("code", code));
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        return response.isSuccess() ? 
            (Map<String, Object>) response.getResult() :
            Map.of("error", response.getError());
    }
    
    /**
     * Find vulnerabilities using MCP
     */
    public List<Map<String, Object>> findVulnerabilities(String code) {
        MCPServer server = servers.get("CodeAnalysis");
        if (server == null) {
            return Collections.emptyList();
        }
        
        MCPServer.MCPRequest request = createRequest("find_vulnerabilities", Map.of("code", code));
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        if (response.isSuccess()) {
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            return (List<Map<String, Object>>) result.get("vulnerabilities");
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Get deployment recommendation using MCP
     */
    public Map<String, Object> getDeploymentRecommendation(String changeType, String environment, double riskLevel) {
        MCPServer server = servers.get("Deployment");
        if (server == null) {
            return Map.of("error", "Deployment server not available");
        }
        
        MCPServer.MCPRequest request = createRequest("recommend_strategy", Map.of(
            "changeType", changeType,
            "environment", environment,
            "riskLevel", riskLevel
        ));
        
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        return response.isSuccess() ?
            (Map<String, Object>) response.getResult() :
            Map.of("error", response.getError());
    }
    
    /**
     * Assess deployment risk using MCP
     */
    public Map<String, Object> assessDeploymentRisk(String changeType, int linesChanged, 
                                                     boolean hasTests, boolean hasBreakingChanges) {
        MCPServer server = servers.get("Deployment");
        if (server == null) {
            return Map.of("error", "Deployment server not available");
        }
        
        MCPServer.MCPRequest request = createRequest("assess_risk", Map.of(
            "changeType", changeType,
            "linesChanged", linesChanged,
            "hasTests", hasTests,
            "hasBreakingChanges", hasBreakingChanges
        ));
        
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        return response.isSuccess() ?
            (Map<String, Object>) response.getResult() :
            Map.of("error", response.getError());
    }
    
    /**
     * Learn from decision outcome
     */
    public void learn(String anomalyType, String recipeId, boolean success, Map<String, Object> metrics) {
        aiEngine.learn(anomalyType, recipeId, success, metrics);
    }
    
    /**
     * Get AI insights
     */
    public Map<String, Object> getAIInsights() {
        return aiEngine.getInsights();
    }
    
    /**
     * Get all available capabilities
     */
    public Map<String, List<String>> getAllCapabilities() {
        Map<String, List<String>> capabilities = new HashMap<>();
        servers.forEach((name, server) -> capabilities.put(name, server.getCapabilities()));
        return capabilities;
    }
    
    /**
     * Check health of all servers
     */
    public Map<String, Boolean> checkHealth() {
        Map<String, Boolean> health = new HashMap<>();
        servers.forEach((name, server) -> health.put(name, server.isHealthy()));
        return health;
    }
    
    private MCPServer.MCPRequest createRequest(String method, Map<String, Object> params) {
        String id = "REQ-" + (++requestCounter) + "-" + System.currentTimeMillis();
        return new MCPServer.MCPRequest(id, method, params);
    }
}
