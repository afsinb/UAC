package com.blacklight.uac.mcp;

import com.blacklight.uac.ai.AIDecisionEngine;

import java.util.*;

/**
 * MCPOrchestrator - Coordinates MCP servers and AI decision engine.
 *
 * Registered servers (developV2):
 *   • CodeAnalysis  – static analysis, vulnerability scanning
 *   • Deployment    – strategy recommendations, PR-gate enforcement
 *   • Merger        – serialised PR merge queue, conflict resolution
 */
public class MCPOrchestrator {

    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    private final Map<String, MCPServer> servers;
    private final AIDecisionEngine aiEngine;
    private int requestCounter = 0;

    public MCPOrchestrator() {
        this.servers  = new HashMap<>();
        this.aiEngine = new AIDecisionEngine();
        initializeServers();
    }

    // ── Initialisation ────────────────────────────────────────────────────

    private void initializeServers() {
        registerServer(new CodeAnalysisMCPServer());
        registerServer(new DeploymentMCPServer());
        registerServer(new MergerMCPServer());          // NEW in developV2

        System.out.println(CYAN + "🔌 MCP Orchestrator initialized with "
                + servers.size() + " servers" + RESET);
    }

    public void registerServer(MCPServer server) {
        servers.put(server.getName(), server);
        System.out.println(GREEN + "  ✓ Registered: " + server.getName()
                + " (" + server.getCapabilities().size() + " capabilities)" + RESET);
    }

    // ── AI decision ───────────────────────────────────────────────────────

    public AIDecisionEngine.AIDecision getAIDecision(String anomalyType, Map<String, Object> context) {
        System.out.println();
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "🤖 AI DECISION ENGINE" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        return aiEngine.analyze(anomalyType, context);
    }

    // ── Code analysis ─────────────────────────────────────────────────────

    public Map<String, Object> analyzeCode(String code) {
        MCPServer server = servers.get("CodeAnalysis");
        if (server == null) return Map.of("error", "CodeAnalysis server not available");
        MCPServer.MCPRequest request = createRequest("analyze_code", Map.of("code", code));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    public List<Map<String, Object>> findVulnerabilities(String code) {
        MCPServer server = servers.get("CodeAnalysis");
        if (server == null) return Collections.emptyList();
        MCPServer.MCPRequest request = createRequest("find_vulnerabilities", Map.of("code", code));
        MCPServer.MCPResponse response = server.handleRequest(request);
        if (response.isSuccess()) {
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            return (List<Map<String, Object>>) result.get("vulnerabilities");
        }
        return Collections.emptyList();
    }

    // ── Deployment ────────────────────────────────────────────────────────

    public Map<String, Object> getDeploymentRecommendation(String changeType, String environment,
                                                            double riskLevel) {
        MCPServer server = servers.get("Deployment");
        if (server == null) return Map.of("error", "Deployment server not available");
        MCPServer.MCPRequest request = createRequest("recommend_strategy", Map.of(
                "changeType",  changeType,
                "environment", environment,
                "riskLevel",   riskLevel
        ));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    public Map<String, Object> assessDeploymentRisk(String changeType, int linesChanged,
                                                     boolean hasTests, boolean hasBreakingChanges) {
        MCPServer server = servers.get("Deployment");
        if (server == null) return Map.of("error", "Deployment server not available");
        MCPServer.MCPRequest request = createRequest("assess_risk", Map.of(
                "changeType",       changeType,
                "linesChanged",     linesChanged,
                "hasTests",         hasTests,
                "hasBreakingChanges", hasBreakingChanges
        ));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    /**
     * Check whether a PR has been approved and merged, making deployment eligible.
     *
     * @param prNumber the GitHub PR number
     * @return result map with "deploymentAllowed" (boolean) and "prState" (String)
     */
    public Map<String, Object> checkPrDeploymentGate(int prNumber) {
        MCPServer server = servers.get("Deployment");
        if (server == null) return Map.of("error", "Deployment server not available");
        MCPServer.MCPRequest request = createRequest("check_pr_gate", Map.of("prNumber", prNumber));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError(), "deploymentAllowed", false);
    }

    // ── Merger ────────────────────────────────────────────────────────────

    /**
     * Enqueue a PR for serialised merging.
     * The PR must be in APPROVED state (via PRDeploymentGate) before calling this.
     *
     * @param prNumber     GitHub PR number
     * @param pipelineId   TaskDependencyGraph pipeline ID
     * @param branch       source branch
     * @param targetBranch base branch (e.g. "master")
     * @param priority     merge priority (lower = earlier; default 5)
     */
    public Map<String, Object> queueMerge(int prNumber, String pipelineId,
                                           String branch, String targetBranch, int priority) {
        MCPServer server = servers.get("Merger");
        if (server == null) return Map.of("error", "Merger server not available");
        MCPServer.MCPRequest request = createRequest("queue_merge", Map.of(
                "prNumber",     prNumber,
                "pipelineId",   pipelineId,
                "branch",       branch,
                "targetBranch", targetBranch,
                "priority",     priority
        ));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    /**
     * Check whether a PR branch has merge conflicts with the target branch.
     */
    public Map<String, Object> checkMergeConflict(int prNumber, String branch, String targetBranch) {
        MCPServer server = servers.get("Merger");
        if (server == null) return Map.of("error", "Merger server not available");
        MCPServer.MCPRequest request = createRequest("check_conflict", Map.of(
                "prNumber",     prNumber,
                "branch",       branch,
                "targetBranch", targetBranch
        ));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    /**
     * Attempt automated conflict resolution for a PR.
     *
     * @param prNumber  PR number
     * @param strategy  one of: REBASE, ACCEPT_OURS, ACCEPT_THEIRS, MANUAL
     */
    public Map<String, Object> resolveConflict(int prNumber, String strategy) {
        MCPServer server = servers.get("Merger");
        if (server == null) return Map.of("error", "Merger server not available");
        MCPServer.MCPRequest request = createRequest("resolve_conflict", Map.of(
                "prNumber", prNumber,
                "strategy", strategy
        ));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    /**
     * Get the current state of the merge queue.
     */
    public Map<String, Object> getMergeQueueStatus() {
        MCPServer server = servers.get("Merger");
        if (server == null) return Map.of("error", "Merger server not available");
        MCPServer.MCPRequest request = createRequest("get_queue_status", Map.of());
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    /**
     * Abort a queued or active merge.
     */
    public Map<String, Object> abortMerge(int prNumber, String reason) {
        MCPServer server = servers.get("Merger");
        if (server == null) return Map.of("error", "Merger server not available");
        MCPServer.MCPRequest request = createRequest("abort_merge", Map.of(
                "prNumber", prNumber,
                "reason",   reason
        ));
        MCPServer.MCPResponse response = server.handleRequest(request);
        return response.isSuccess()
                ? (Map<String, Object>) response.getResult()
                : Map.of("error", response.getError());
    }

    // ── Learning & insights ───────────────────────────────────────────────

    public void learn(String anomalyType, String recipeId, boolean success,
                      Map<String, Object> metrics) {
        aiEngine.learn(anomalyType, recipeId, success, metrics);
    }

    public Map<String, Object> getAIInsights() {
        return aiEngine.getInsights();
    }

    // ── Admin ─────────────────────────────────────────────────────────────

    public Map<String, List<String>> getAllCapabilities() {
        Map<String, List<String>> capabilities = new HashMap<>();
        servers.forEach((name, server) -> capabilities.put(name, server.getCapabilities()));
        return capabilities;
    }

    public Map<String, Boolean> checkHealth() {
        Map<String, Boolean> health = new HashMap<>();
        servers.forEach((name, server) -> health.put(name, server.isHealthy()));
        return health;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private MCPServer.MCPRequest createRequest(String method, Map<String, Object> params) {
        String id = "REQ-" + (++requestCounter) + "-" + System.currentTimeMillis();
        return new MCPServer.MCPRequest(id, method, params);
    }
}

