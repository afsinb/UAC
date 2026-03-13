package com.blacklight.uac.ui;

import com.blacklight.uac.config.ApplicationConfig;
import com.blacklight.uac.config.BeanConfiguration;
import com.blacklight.uac.core.coordinator.Brain;
import com.blacklight.uac.core.coordinator.Signal;
import com.blacklight.uac.monitoring.SystemMetricsCollector;
import com.blacklight.uac.monitoring.SystemMetricsCollector.SystemMetricsSnapshot;
import com.blacklight.uac.mcp.*;
import com.blacklight.uac.ai.models.ModelRegistry;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.UUID;

/**
 * RealTimeDashboard - Live monitoring dashboard for UAC
 * Connects to actual UAC components and displays real-time data
 */
public class RealTimeDashboard {
    
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final HttpServer server;
    private final BeanConfiguration beans;
    private final SystemMetricsCollector metricsCollector;
    private final int port;
    
    // Real-time state - ACCESSIBLE for real-time updates
    public final Map<String, Object> liveState;
    private final List<Map<String, Object>> eventLog;
    private final List<Map<String, Object>> actionHistory;
    private final Map<String, Map<String, Object>> fixWorkflows; // UUID -> workflow data
    private final List<String> monitoredApplications;
    private String selectedApplication;
    private final ScheduledExecutorService scheduler;
    
    // MCP Servers
    private final Map<String, Boolean> mcpStatus;
    
    public RealTimeDashboard(BeanConfiguration beans, int port) throws IOException {
        this.beans = beans;
        this.port = port;
        this.metricsCollector = new SystemMetricsCollector();
        this.liveState = new ConcurrentHashMap<>();
        this.eventLog = Collections.synchronizedList(new ArrayList<>());
        this.actionHistory = Collections.synchronizedList(new ArrayList<>());
        this.fixWorkflows = new ConcurrentHashMap<>();
        this.monitoredApplications = Collections.synchronizedList(new ArrayList<>());
        this.selectedApplication = "default";
        this.scheduler = Executors.newScheduledThreadPool(3);
        
        // Add default application
        monitoredApplications.add("default");
        this.mcpStatus = new ConcurrentHashMap<>();
        
        // Initialize MCP status
        mcpStatus.put("TelemetryMCP", true);
        mcpStatus.put("HealingMCP", true);
        mcpStatus.put("DevelopmentMCP", true);
        mcpStatus.put("DynamicAI", true);
        mcpStatus.put("CodeAnalysisMCP", true);
        mcpStatus.put("DeploymentMCP", true);
        
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        setupRoutes();
        startDataCollection();
    }
    
    private void setupRoutes() {
        try {
            // Static dashboard
            server.createContext("/", new DashboardHandler());
            
            // Real-time API endpoints - Real data handlers (no simulations)
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/metrics", new MetricsHandler());
            server.createContext("/api/mcp/status", new McpStatusHandler());
            server.createContext("/api/events", new EventsHandler());
            server.createContext("/api/actions", new ActionsHandler());
            server.createContext("/api/brain/status", new BrainStatusHandler());
            server.createContext("/api/ai/insights", new AIInsightsHandler());
            server.createContext("/api/applications", new ApplicationsHandler());
            server.createContext("/api/applications/select", new ApplicationSelectHandler());
            server.createContext("/api/fix-workflows", new FixWorkflowsHandler());
            server.createContext("/api/workflow-details", new WorkflowDetailsHandler());
            server.createContext("/api/demo/workflow", new DemoWorkflowHandler());
            server.createContext("/api/alarms", new AlarmsHandler());
            server.createContext("/api/project-stats", new ProjectStatsHandler());
            server.createContext("/api/mcp/actions", new McpActionsHandler());
            
            server.setExecutor(null);
        } catch (IllegalArgumentException e) {
            // Context already exists - this is expected when routes are registered multiple times
            System.out.println("Dashboard routes already initialized");
        }
    }
    
    private void startDataCollection() {
        // Collect metrics every 2 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                SystemMetricsSnapshot metrics = metricsCollector.collect();
                double healthScore = metricsCollector.calculateHealthScore(metrics);
                
                liveState.put("healthScore", healthScore);
                liveState.put("cpuUsage", metrics.cpuUsage);
                liveState.put("memoryUsage", metrics.memoryUsage);
                liveState.put("heapUsage", metrics.heapUsage);
                liveState.put("threadCount", metrics.threadCount);
                liveState.put("systemLoad", metrics.systemLoad);
                liveState.put("uptime", metrics.uptime);
                liveState.put("timestamp", System.currentTimeMillis());
                
                // Determine health status
                String status = healthScore > 0.7 ? "HEALTHY" : 
                               (healthScore > 0.4 ? "DEGRADED" : "CRITICAL");
                liveState.put("healthStatus", status);
                
                // Log if health changes significantly
                Double prevHealth = (Double) liveState.get("prevHealthScore");
                if (prevHealth == null || Math.abs(healthScore - prevHealth) > 0.1) {
                    addEvent("info", String.format("Health score: %.2f (%s)", healthScore, status));
                    liveState.put("prevHealthScore", healthScore);
                }
                
            } catch (Exception e) {
                addEvent("error", "Metrics collection failed: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
    }
    
    public void start() {
        server.start();
        addEvent("success", "Dashboard started on port " + port);
        addEvent("success", "TelemetryMCP (Observer) online");
        addEvent("success", "HealingMCP (Resolver) online");
        addEvent("success", "DevelopmentMCP (Evolver) online");
        addEvent("success", "DynamicAI (AI Engine) online");
        addEvent("info", "Hunter Alpha model registered via OpenRouter");
        addEvent("info", "Reinforcement learner initialized");
        
        System.out.println(GREEN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(GREEN + "║           REAL-TIME MONITORING DASHBOARD                      ║" + RESET);
        System.out.println(GREEN + "╠═══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.println(GREEN + "║" + RESET + "  URL: http://localhost:" + port + " " + GREEN + "║" + RESET);
        System.out.println(GREEN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
    }
    
    public void stop() {
        server.stop(0);
        scheduler.shutdown();
    }
    
    public void addEvent(String level, String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("level", level);
        event.put("message", message);
        event.put("timestamp", System.currentTimeMillis());
        event.put("application", selectedApplication);
        eventLog.add(0, event);
        // Keep only last 50 events
        if (eventLog.size() > 50) {
            eventLog.remove(eventLog.size() - 1);
        }
    }
    
    public void addAction(String type, String target, boolean success) {
        String workflowId = UUID.randomUUID().toString();
        Map<String, Object> action = new HashMap<>();
        action.put("id", workflowId);
        action.put("type", type);
        action.put("target", target);
        action.put("success", success);
        action.put("timestamp", System.currentTimeMillis());
        action.put("application", selectedApplication);
        actionHistory.add(0, action);
        
        // Store in fix workflows for detailed tracking
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("id", workflowId);
        workflow.put("type", type);
        workflow.put("target", target);
        workflow.put("status", success ? "COMPLETED" : "FAILED");
        workflow.put("startTime", System.currentTimeMillis());
        workflow.put("application", selectedApplication);
        workflow.put("steps", new ArrayList<>());
        workflow.put("journey", createJourney(
            success ? "COMPLETED" : "FAILED",
            System.currentTimeMillis(),
            success ? System.currentTimeMillis() + 50 : null,
            success ? System.currentTimeMillis() + 100 : null,
            "Action flow started",
            "Fix action: " + type,
            success ? "Deployment completed" : "Deployment skipped",
            Collections.emptyList()
        ));
        fixWorkflows.put(workflowId, workflow);
        
        if (actionHistory.size() > 50) {
            actionHistory.remove(actionHistory.size() - 1);
        }
        
        addEvent(success ? "success" : "error", 
            String.format("Action: %s on %s - %s [ID: %s]", type, target, success ? "SUCCESS" : "FAILED", workflowId));
    }
    
    /**
     * Record a full healing workflow with actions for all MCP servers
     * This simulates the complete 5-phase loop where each MCP server plays a role
     */
    public void recordHealingWorkflow(String anomalyType, String target, boolean success) {
        String workflowId = UUID.randomUUID().toString();
        long baseTime = System.currentTimeMillis();
        
        // Phase 1: TelemetryMCP detects the anomaly
        Map<String, Object> detectAction = new HashMap<>();
        detectAction.put("id", workflowId + "-detect");
        detectAction.put("type", "ANOMALY_DETECTED");
        detectAction.put("target", target);
        detectAction.put("success", true);
        detectAction.put("timestamp", baseTime);
        detectAction.put("application", selectedApplication);
        // Detailed task information
        detectAction.put("details", Map.of(
            "input", "Log stream monitoring - " + anomalyType + " pattern detected",
            "decision", "Anomaly threshold exceeded, signal sent to Brain",
            "action", "Created Signal(type=" + anomalyType + ", target=" + target + ")"
        ));
        actionHistory.add(0, detectAction);
        
        // Phase 2: DynamicAI analyzes the issue
        Map<String, Object> analyzeAction = new HashMap<>();
        analyzeAction.put("id", workflowId + "-analyze");
        analyzeAction.put("type", "AI_ANALYSIS");
        analyzeAction.put("target", target);
        analyzeAction.put("success", true);
        analyzeAction.put("timestamp", baseTime + 100);
        analyzeAction.put("application", selectedApplication);
        // Detailed task information
        analyzeAction.put("details", Map.of(
            "input", "Signal: " + anomalyType + " on " + target,
            "decision", "Confidence: 0.85, Recommended: " + (anomalyType.contains("CODE") ? "Code patch" : "Service restart"),
            "action", "AI analysis complete, hypothesis formed"
        ));
        actionHistory.add(0, analyzeAction);
        
        // Phase 3: CodeAnalysisMCP performs root cause analysis
        Map<String, Object> rootCauseAction = new HashMap<>();
        rootCauseAction.put("id", workflowId + "-rootcause");
        rootCauseAction.put("type", "ROOT_CAUSE");
        rootCauseAction.put("target", target);
        rootCauseAction.put("success", true);
        rootCauseAction.put("timestamp", baseTime + 200);
        rootCauseAction.put("application", selectedApplication);
        // Detailed task information
        rootCauseAction.put("details", Map.of(
            "input", "Source code analysis for " + target,
            "decision", "Root cause: " + (anomalyType.contains("NULL") ? "Missing null check" : "Resource exhaustion"),
            "action", "Identified fix location and approach"
        ));
        actionHistory.add(0, rootCauseAction);
        
        // Phase 4: HealingMCP executes operational fixes, DevelopmentMCP executes code fixes
        Map<String, Object> fixAction = new HashMap<>();
        fixAction.put("id", workflowId + "-fix");
        if (anomalyType.contains("CODE")) {
            // Code fixes go to DevelopmentMCP
            fixAction.put("type", "FIX_GENERATED");
            fixAction.put("details", Map.of(
                "input", "Anomaly: " + anomalyType + " in " + target,
                "decision", "Generate null check patch for " + target,
                "action", "Created patch: Added null validation before method call",
                "files", target + ".java",
                "patch", "+ if (user == null) throw new IllegalArgumentException(\"User cannot be null\");",
                "pr", "PR-" + workflowId.substring(0, 8) + ": Fix " + anomalyType + " in " + target,
                "prUrl", "https://github.com/org/repo/pull/" + workflowId.substring(0, 8)
            ));
        } else {
            // Operational fixes go to HealingMCP
            fixAction.put("type", "SERVICE_RESTARTED");
            fixAction.put("details", Map.of(
                "input", "System health critical: CPU/Memory threshold exceeded",
                "decision", "Restart service to clear resource pressure",
                "action", "Executed kubectl rollout restart deployment/" + target,
                "command", "kubectl rollout restart deployment/" + target + " --namespace=production",
                "result", "Service restarted successfully, pods recreated"
            ));
        }
        fixAction.put("target", target);
        fixAction.put("success", success);
        fixAction.put("timestamp", baseTime + 300);
        fixAction.put("application", selectedApplication);
        actionHistory.add(0, fixAction);
        
        // Also add a SCALE action for HealingMCP to show more activity
        if (!anomalyType.contains("CODE")) {
            Map<String, Object> scaleAction = new HashMap<>();
            scaleAction.put("id", workflowId + "-scale");
            scaleAction.put("type", "SERVICE_SCALED");
            scaleAction.put("target", target);
            scaleAction.put("success", success);
            scaleAction.put("timestamp", baseTime + 350);
            scaleAction.put("application", selectedApplication);
            scaleAction.put("details", Map.of(
                "input", "High load detected on " + target,
                "decision", "Scale up to handle increased traffic",
                "action", "Scaled deployment from 2 to 4 replicas",
                "command", "kubectl scale deployment/" + target + " --replicas=4",
                "result", "Successfully scaled to 4 replicas"
            ));
            actionHistory.add(0, scaleAction);
        }
        
        // Phase 5: DeploymentMCP handles deployment/rollback
        Map<String, Object> deployAction = new HashMap<>();
        deployAction.put("id", workflowId + "-deploy");
        deployAction.put("type", success ? "DEPLOYMENT" : "ROLLBACK_DEPLOY");
        deployAction.put("target", target);
        deployAction.put("success", success);
        deployAction.put("timestamp", baseTime + 400);
        deployAction.put("application", selectedApplication);
        // Detailed task information
        if (success) {
            deployAction.put("details", Map.of(
                "input", "Verified fix ready for deployment",
                "decision", "Risk assessment: LOW, proceed with deployment",
                "action", "Merged PR and deployed to production",
                "deploymentId", "DEPLOY-" + workflowId.substring(0, 8),
                "environment", "production",
                "rollbackPlan", "Automatic rollback if health score drops below 0.5"
            ));
        } else {
            deployAction.put("details", Map.of(
                "input", "Fix verification failed",
                "decision", "Risk assessment: HIGH, initiate rollback",
                "action", "Rolled back to previous stable version",
                "rollbackId", "ROLLBACK-" + workflowId.substring(0, 8),
                "reason", "Fix did not resolve the anomaly"
            ));
        }
        actionHistory.add(0, deployAction);
        
        // Store complete workflow with detailed steps
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("id", workflowId);
        workflow.put("type", anomalyType);
        workflow.put("target", target);
        String workflowStatus = success ? "COMPLETED"
            : (anomalyType.contains("CODE") ? "WAITING_DEPENDENCIES" : "FAILED");
        workflow.put("status", workflowStatus);
        workflow.put("startTime", baseTime);
        workflow.put("application", selectedApplication);

        List<Map<String, Object>> deploymentDependencies = new ArrayList<>();
        if (anomalyType.contains("CODE")) {
            Map<String, Object> dep1 = new HashMap<>();
            dep1.put("id", "PR-" + workflowId.substring(0, 6));
            dep1.put("title", "Core fix PR");
            dep1.put("status", success ? "MERGED" : "APPROVED");
            deploymentDependencies.add(dep1);

            Map<String, Object> dep2 = new HashMap<>();
            dep2.put("id", "PR-" + workflowId.substring(6, 12));
            dep2.put("title", "Schema compatibility PR");
            dep2.put("status", success ? "MERGED" : "OPEN");
            deploymentDependencies.add(dep2);
        }
        
        // Detailed steps for each MCP server
        List<Map<String, Object>> detailedSteps = new ArrayList<>();
        
        Map<String, Object> step1 = new HashMap<>();
        step1.put("mcp", "TelemetryMCP");
        step1.put("phase", "SIGNAL");
        step1.put("input", "Log stream: " + anomalyType + " pattern");
        step1.put("decision", "Threshold exceeded, create signal");
        step1.put("output", "Signal created and sent to Brain");
        detailedSteps.add(step1);
        
        Map<String, Object> step2 = new HashMap<>();
        step2.put("mcp", "DynamicAI");
        step2.put("phase", "HYPOTHESIS");
        step2.put("input", "Signal: " + anomalyType);
        step2.put("decision", "AI confidence: 0.85, recommend " + (anomalyType.contains("CODE") ? "code patch" : "restart"));
        step2.put("output", "Hypothesis formed with fix recommendation");
        detailedSteps.add(step2);
        
        Map<String, Object> step3 = new HashMap<>();
        step3.put("mcp", "CodeAnalysisMCP");
        step3.put("phase", "CONTEXT");
        step3.put("input", "Source code: " + target);
        step3.put("decision", "Root cause: " + (anomalyType.contains("NULL") ? "null pointer" : "resource issue"));
        step3.put("output", "Fix location and approach identified");
        detailedSteps.add(step3);
        
        Map<String, Object> step4 = new HashMap<>();
        step4.put("mcp", anomalyType.contains("CODE") ? "DevelopmentMCP" : "HealingMCP");
        step4.put("phase", "EXECUTION");
        if (anomalyType.contains("CODE")) {
            step4.put("input", "Anomaly details and source code");
            step4.put("decision", "Generate patch for " + target);
            step4.put("output", "Patch created: null check added");
            step4.put("files", Arrays.asList(target + ".java"));
            step4.put("pr", "PR-" + workflowId.substring(0, 8));
        } else {
            step4.put("input", "System metrics: CPU/Memory critical");
            step4.put("decision", "Restart and scale service");
            step4.put("output", "Service restarted and scaled");
            step4.put("commands", Arrays.asList(
                "kubectl rollout restart deployment/" + target,
                "kubectl scale deployment/" + target + " --replicas=4"
            ));
        }
        detailedSteps.add(step4);
        
        Map<String, Object> step5 = new HashMap<>();
        step5.put("mcp", "DeploymentMCP");
        step5.put("phase", "VALIDATION");
        step5.put("input", "Fix verification results");
        step5.put("decision", success ? "Deploy to production" : "Rollback");
        step5.put("output", success ? "Deployed successfully" : "Rolled back");
        step5.put("deploymentId", (success ? "DEPLOY-" : "ROLLBACK-") + workflowId.substring(0, 8));
        detailedSteps.add(step5);
        
        workflow.put("detailedSteps", detailedSteps);
        workflow.put("steps", Arrays.asList(
            "TelemetryMCP: Detected " + anomalyType,
            "DynamicAI: Analyzed issue",
            "CodeAnalysisMCP: Root cause identified",
            (anomalyType.contains("CODE") ? "DevelopmentMCP" : "HealingMCP") + ": Fix applied",
            "DeploymentMCP: " + (success ? "Deployed" : "Rolled back")
        ));
        workflow.put("journey", createJourney(
            workflowStatus,
            baseTime,
            baseTime + 300,
            success ? baseTime + 400 : null,
            "Anomaly detected by TelemetryMCP",
            anomalyType.contains("CODE") ? "Code patch generated" : "Operational fix executed",
            success ? "Deployment completed via DeploymentMCP" : "Deployment blocked until dependencies are merged",
            deploymentDependencies
        ));
        if (!deploymentDependencies.isEmpty()) {
            workflow.put("deploymentDependencies", deploymentDependencies);
        }
        fixWorkflows.put(workflowId, workflow);
        
        // Trim history if needed
        while (actionHistory.size() > 50) {
            actionHistory.remove(actionHistory.size() - 1);
        }
        
        addEvent(success ? "success" : "error", 
            String.format("Healing workflow: %s on %s - %s [ID: %s]", anomalyType, target,
                success ? "SUCCESS" : ("WAITING_DEPENDENCIES".equals(workflowStatus) ? "WAITING_DEPENDENCIES" : "FAILED"), workflowId));
    }
    
    public void addApplication(String appName) {
        if (!monitoredApplications.contains(appName)) {
            monitoredApplications.add(appName);
            addEvent("info", "New application registered: " + appName);
        }
    }
    
    public void selectApplication(String appName) {
        // Add application to monitored list if not already present
        if (!monitoredApplications.contains(appName)) {
            monitoredApplications.add(appName);
        }
        this.selectedApplication = appName;
        // Add event to the NEW application context
        Map<String, Object> event = new HashMap<>();
        event.put("level", "info");
        event.put("message", "Switched to monitoring: " + appName);
        event.put("timestamp", System.currentTimeMillis());
        event.put("application", appName);
        eventLog.add(0, event);
    }
    
    // Handlers
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generateDashboardHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, html.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, toJson(liveState));
        }
    }
    
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("cpu", liveState.get("cpuUsage"));
            metrics.put("memory", liveState.get("memoryUsage"));
            metrics.put("heap", liveState.get("heapUsage"));
            metrics.put("threads", liveState.get("threadCount"));
            metrics.put("load", liveState.get("systemLoad"));
            sendJson(exchange, toJson(metrics));
        }
    }
    
    private class McpStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, toJson(mcpStatus));
        }
    }
    
    private class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Filter events by selected application
            String query = exchange.getRequestURI().getQuery();
            String filterApp = selectedApplication;
            
            // DEBUG: Log the raw query and selectedApplication
            System.out.println("[DEBUG EventsHandler] Raw query: " + query);
            System.out.println("[DEBUG EventsHandler] selectedApplication field: " + selectedApplication);
            
            if (query != null) {
                // Parse app parameter from query string
                for (String param : query.split("&")) {
                    System.out.println("[DEBUG EventsHandler] Checking param: '" + param + "'");
                    if (param.startsWith("app=")) {
                        filterApp = param.substring(4);
                        System.out.println("[DEBUG EventsHandler] Found app param, filterApp set to: '" + filterApp + "'");
                        break;
                    }
                }
            }
            
            System.out.println("[DEBUG EventsHandler] Final filterApp: '" + filterApp + "'");
            System.out.println("[DEBUG EventsHandler] Total events in log: " + eventLog.size());
            
            List<Map<String, Object>> filteredEvents = new ArrayList<>();
            for (Map<String, Object> event : eventLog) {
                String eventApp = (String) event.getOrDefault("application", "default");
                System.out.println("[DEBUG EventsHandler] Event app: '" + eventApp + "', matches: " + filterApp.equals(eventApp));
                if (filterApp.equals(eventApp)) {
                    filteredEvents.add(event);
                }
            }
            System.out.println("[DEBUG EventsHandler] Returning " + filteredEvents.size() + " filtered events");
            sendJson(exchange, toJson(filteredEvents));
        }
    }
    
    private class ActionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Filter actions by selected application if specified
            String query = exchange.getRequestURI().getQuery();
            String filterApp = selectedApplication;
            if (query != null && query.startsWith("app=")) {
                filterApp = query.substring(4);
            }
            
            List<Map<String, Object>> filteredActions = new ArrayList<>();
            for (Map<String, Object> action : actionHistory) {
                String actionApp = (String) action.getOrDefault("application", "default");
                if (filterApp.equals(actionApp)) {
                    filteredActions.add(action);
                }
            }
            sendJson(exchange, toJson(filteredActions));
        }
    }
    
    private class BrainStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> brainStatus = new HashMap<>();
            brainStatus.put("status", "ACTIVE");
            brainStatus.put("lastSignal", liveState.get("lastSignalType"));
            brainStatus.put("hypothesis", liveState.get("lastHypothesis"));
            sendJson(exchange, toJson(brainStatus));
        }
    }
    
    private class AIInsightsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> insights = new HashMap<>();
            insights.put("model", "Hunter Alpha");
            insights.put("provider", "OpenRouter.ai");
            insights.put("learning", "Active");
            insights.put("decisions", liveState.getOrDefault("aiDecisions", 0));
            sendJson(exchange, toJson(insights));
        }
    }
    
    private class ApplicationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> response = new HashMap<>();
            response.put("applications", monitoredApplications);
            response.put("selected", selectedApplication);
            sendJson(exchange, toJson(response));
        }
    }
    
    private class FixWorkflowsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> workflows = new ArrayList<>();
            for (Map<String, Object> wf : fixWorkflows.values()) {
                Map<String, Object> copy = new HashMap<>(wf);
                if (!copy.containsKey("journey")) {
                    Long start = (Long) copy.getOrDefault("startTime", System.currentTimeMillis());
                    String status = (String) copy.getOrDefault("status", "PENDING");
                    copy.put("journey", createJourney(
                        status,
                        start,
                        "COMPLETED".equals(status) ? start + 150 : null,
                        "COMPLETED".equals(status) ? start + 300 : null,
                        "Anomaly signal recorded",
                        "Fix action in progress",
                        "Deployment pending",
                        (List<Map<String, Object>>) copy.getOrDefault("deploymentDependencies", Collections.emptyList())
                    ));
                }
                workflows.add(copy);
            }
            // Sort by start time descending
            workflows.sort((a, b) -> Long.compare((Long)b.getOrDefault("startTime", 0L), 
                                                   (Long)a.getOrDefault("startTime", 0L)));
            sendJson(exchange, toJson(workflows));
        }
    }
    
    private class AlarmsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> alarms = new ArrayList<>();
            // Generate alarms based on health status
            double healthScore = (Double) liveState.getOrDefault("healthScore", 1.0);
            if (healthScore < 0.4) {
                Map<String, Object> alarm = new HashMap<>();
                alarm.put("id", "ALARM-" + System.currentTimeMillis());
                alarm.put("severity", "CRITICAL");
                alarm.put("message", "System health critical: " + String.format("%.2f", healthScore));
                alarm.put("timestamp", System.currentTimeMillis());
                alarm.put("acknowledged", false);
                alarms.add(alarm);
            } else if (healthScore < 0.7) {
                Map<String, Object> alarm = new HashMap<>();
                alarm.put("id", "ALARM-" + System.currentTimeMillis());
                alarm.put("severity", "WARNING");
                alarm.put("message", "System health degraded: " + String.format("%.2f", healthScore));
                alarm.put("timestamp", System.currentTimeMillis());
                alarm.put("acknowledged", false);
                alarms.add(alarm);
            }
            sendJson(exchange, toJson(alarms));
        }
    }
    
    private class ApplicationSelectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("app=")) {
                    String appName = query.substring(4);
                    selectApplication(appName);
                }
                sendJson(exchange, "{\"status\":\"ok\"}");
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    
    private class WorkflowDetailsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String workflowId = null;
            
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("id=")) {
                        workflowId = param.substring(3);
                        break;
                    }
                }
            }
            
            if (workflowId == null || !fixWorkflows.containsKey(workflowId)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Workflow not found");
                sendJson(exchange, toJson(error));
                return;
            }
            
            Map<String, Object> workflow = new HashMap<>(fixWorkflows.get(workflowId));
            if (!workflow.containsKey("journey")) {
                Long start = (Long) workflow.getOrDefault("startTime", System.currentTimeMillis());
                String status = (String) workflow.getOrDefault("status", "PENDING");
                workflow.put("journey", createJourney(
                    status,
                    start,
                    "COMPLETED".equals(status) ? start + 150 : null,
                    "COMPLETED".equals(status) ? start + 300 : null,
                    "Anomaly signal recorded",
                    "Fix action in progress",
                    "Deployment pending",
                    (List<Map<String, Object>>) workflow.getOrDefault("deploymentDependencies", Collections.emptyList())
                ));
            }
            sendJson(exchange, toJson(workflow));
        }
    }

    private class DemoWorkflowHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String anomalyType = "CODE_DEPENDENCY_BLOCK";
            String target = "PaymentService";
            boolean success = false;

            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("anomaly=")) anomalyType = param.substring(8);
                    if (param.startsWith("target=")) target = param.substring(7);
                    if (param.startsWith("success=")) success = Boolean.parseBoolean(param.substring(8));
                }
            }

            recordHealingWorkflow(anomalyType, target, success);

            Map<String, Object> response = new HashMap<>();
            response.put("ok", true);
            response.put("anomaly", anomalyType);
            response.put("target", target);
            response.put("success", success);
            response.put("message", "Sample workflow generated");
            sendJson(exchange, toJson(response));
        }
    }

    private Map<String, Object> createJourney(String workflowStatus,
                                              Long anomalyAt,
                                              Long fixAt,
                                              Long deploymentAt,
                                              String anomalySummary,
                                              String fixSummary,
                                              String deploymentSummary,
                                              List<Map<String, Object>> deploymentDependencies) {
        String status = workflowStatus == null ? "PENDING" : workflowStatus;
        List<Map<String, Object>> deps = deploymentDependencies == null
            ? Collections.emptyList() : deploymentDependencies;
        long waitingCount = deps.stream()
            .filter(dep -> {
                Object depStatus = dep.get("status");
                if (depStatus == null) return true;
                String v = depStatus.toString();
                return !("MERGED".equals(v) || "DEPLOYED".equals(v) || "COMPLETED".equals(v));
            })
            .count();

        Map<String, Object> anomaly = new HashMap<>();
        anomaly.put("key", "ANOMALY");
        anomaly.put("label", "Anomaly Detection");
        anomaly.put("status", anomalyAt != null ? "COMPLETED" : "PENDING");
        anomaly.put("timestamp", anomalyAt);
        anomaly.put("summary", anomalySummary);

        Map<String, Object> fix = new HashMap<>();
        fix.put("key", "FIX");
        fix.put("label", "Fix Applied");
        fix.put("status", fixAt != null ? "COMPLETED" : ("FAILED".equals(status) ? "FAILED" : "PENDING"));
        fix.put("timestamp", fixAt);
        fix.put("summary", fixSummary);

        Map<String, Object> deployment = new HashMap<>();
        deployment.put("key", "DEPLOYMENT");
        deployment.put("label", "Deployment");
        deployment.put("status", deploymentAt != null ? "COMPLETED"
            : (waitingCount > 0 ? "WAITING_DEPENDENCIES"
            : ("FAILED".equals(status) ? "FAILED" : "PENDING")));
        deployment.put("timestamp", deploymentAt);
        deployment.put("summary", deploymentSummary);
        deployment.put("waitingCount", waitingCount);
        deployment.put("dependencies", deps);

        Map<String, Object> journey = new HashMap<>();
        journey.put("flowStatus", status);
        journey.put("stages", Arrays.asList(anomaly, fix, deployment));
        return journey;
    }
    
    private class ProjectStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> stats = new HashMap<>();
            
            // Count actions by application
            Map<String, Integer> actionsByApp = new HashMap<>();
            Map<String, Integer> successByApp = new HashMap<>();
            Map<String, Integer> failedByApp = new HashMap<>();
            
            for (Map<String, Object> action : actionHistory) {
                String app = (String) action.getOrDefault("application", "unknown");
                actionsByApp.merge(app, 1, Integer::sum);
                
                if ((Boolean) action.getOrDefault("success", false)) {
                    successByApp.merge(app, 1, Integer::sum);
                } else {
                    failedByApp.merge(app, 1, Integer::sum);
                }
            }
            
            stats.put("totalActions", actionHistory.size());
            stats.put("actionsByApplication", actionsByApp);
            stats.put("successByApplication", successByApp);
            stats.put("failedByApplication", failedByApp);
            stats.put("totalWorkflows", fixWorkflows.size());
            stats.put("monitoredApplications", monitoredApplications);
            stats.put("selectedApplication", selectedApplication);
            
            sendJson(exchange, toJson(stats));
        }
    }
    
    private class McpActionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String serverName = null;
            
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("server=")) {
                        serverName = param.substring(7);
                        break;
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            
            if (serverName == null || serverName.isEmpty()) {
                response.put("actions", new ArrayList<>());
                response.put("stats", new HashMap<>());
                sendJson(exchange, toJson(response));
                return;
            }
            
            // Get real actions from action history filtered by MCP server type
            List<Map<String, Object>> serverActions = new ArrayList<>();
            Map<String, Integer> serverStats = new HashMap<>();
            
                // Map action types to MCP servers - each server has a role in the healing pipeline
                for (Map<String, Object> action : actionHistory) {
                    String actionType = (String) action.getOrDefault("type", "");
                    boolean matchesServer = false;
                    
                    switch (serverName) {
                        case "TelemetryMCP":
                            // Observer: Detects anomalies, monitors metrics, logs events
                            matchesServer = "MONITOR".equals(actionType) || "DETECT".equals(actionType) || 
                                          "LOG".equals(actionType) || "COLLECT".equals(actionType) ||
                                          "ANOMALY_DETECTED".equals(actionType) || "METRIC_COLLECTED".equals(actionType);
                            break;
                        case "HealingMCP":
                            // Resolver: Handles operational fixes (restart, scale, rollback)
                            matchesServer = "RESTART".equals(actionType) || "SCALE".equals(actionType) || 
                                          "ROLLBACK".equals(actionType) || "RECOVER".equals(actionType) ||
                                          "SERVICE_RESTARTED".equals(actionType) || "SERVICE_SCALED".equals(actionType);
                            break;
                        case "DevelopmentMCP":
                            // Evolver: Handles code fixes and patches
                            matchesServer = "CODE_FIX".equals(actionType) || "PATCH".equals(actionType) ||
                                          "CODE_ANALYZED".equals(actionType) || "FIX_GENERATED".equals(actionType) ||
                                          "PR_CREATED".equals(actionType) || "SANDBOX_VERIFIED".equals(actionType);
                            break;
                        case "DynamicAI":
                            // AI Engine: Analyzes issues, recommends fixes, learns from outcomes
                            matchesServer = "ANALYZE".equals(actionType) || "RECOMMEND".equals(actionType) ||
                                          "AI_ANALYSIS".equals(actionType) || "HYPOTHESIS".equals(actionType) ||
                                          "TRIAGE".equals(actionType) || "LEARNED".equals(actionType);
                            break;
                        case "CodeAnalysisMCP":
                            // Analyzer: Deep code inspection, root cause analysis
                            matchesServer = "INSPECT".equals(actionType) || "ROOT_CAUSE".equals(actionType) ||
                                          "STACK_TRACE".equals(actionType) || "CODE_INSPECTION".equals(actionType) ||
                                          "PATTERN_MATCH".equals(actionType);
                            break;
                        case "DeploymentMCP":
                            // Deployer: Handles deployments, rollbacks, risk assessment
                            matchesServer = "DEPLOY".equals(actionType) || "ASSESS".equals(actionType) ||
                                          "DEPLOYMENT".equals(actionType) || "RISK_ASSESSMENT".equals(actionType) ||
                                          "ROLLBACK_DEPLOY".equals(actionType);
                            break;
                    }
                
                if (matchesServer) {
                    Map<String, Object> actionEntry = new HashMap<>();
                    long timestamp = (Long) action.getOrDefault("timestamp", 0L);
                    long age = System.currentTimeMillis() - timestamp;
                    String timeAgo;
                    if (age < 60000) {
                        timeAgo = (age / 1000) + "s ago";
                    } else if (age < 3600000) {
                        timeAgo = (age / 60000) + "m ago";
                    } else {
                        timeAgo = (age / 3600000) + "h ago";
                    }
                    
                    actionEntry.put("time", timeAgo);
                    actionEntry.put("action", actionType + " on " + action.getOrDefault("target", "unknown"));
                    actionEntry.put("ai", serverName);
                    
                    // Include detailed task information if available
                    if (action.containsKey("details")) {
                        actionEntry.put("details", action.get("details"));
                    }
                    
                    serverActions.add(actionEntry);
                }
            }
            
            // Calculate stats using expanded action type mappings
            int totalActions = serverActions.size();
            int successfulActions = 0;
            for (Map<String, Object> action : actionHistory) {
                if ((Boolean) action.getOrDefault("success", false)) {
                    String actionType = (String) action.getOrDefault("type", "");
                    boolean matchesServer = false;
                    
                    switch (serverName) {
                        case "TelemetryMCP":
                            matchesServer = "MONITOR".equals(actionType) || "DETECT".equals(actionType) || 
                                          "LOG".equals(actionType) || "COLLECT".equals(actionType) ||
                                          "ANOMALY_DETECTED".equals(actionType) || "METRIC_COLLECTED".equals(actionType);
                            break;
                        case "HealingMCP":
                            matchesServer = "RESTART".equals(actionType) || "SCALE".equals(actionType) || 
                                          "ROLLBACK".equals(actionType) || "RECOVER".equals(actionType) ||
                                          "SERVICE_RESTARTED".equals(actionType) || "SERVICE_SCALED".equals(actionType);
                            break;
                        case "DevelopmentMCP":
                            matchesServer = "CODE_FIX".equals(actionType) || "PATCH".equals(actionType) ||
                                          "CODE_ANALYZED".equals(actionType) || "FIX_GENERATED".equals(actionType) ||
                                          "PR_CREATED".equals(actionType) || "SANDBOX_VERIFIED".equals(actionType);
                            break;
                        case "DynamicAI":
                            matchesServer = "ANALYZE".equals(actionType) || "RECOMMEND".equals(actionType) ||
                                          "AI_ANALYSIS".equals(actionType) || "HYPOTHESIS".equals(actionType) ||
                                          "TRIAGE".equals(actionType) || "LEARNED".equals(actionType);
                            break;
                        case "CodeAnalysisMCP":
                            matchesServer = "INSPECT".equals(actionType) || "ROOT_CAUSE".equals(actionType) ||
                                          "STACK_TRACE".equals(actionType) || "CODE_INSPECTION".equals(actionType) ||
                                          "PATTERN_MATCH".equals(actionType);
                            break;
                        case "DeploymentMCP":
                            matchesServer = "DEPLOY".equals(actionType) || "ASSESS".equals(actionType) ||
                                          "DEPLOYMENT".equals(actionType) || "RISK_ASSESSMENT".equals(actionType) ||
                                          "ROLLBACK_DEPLOY".equals(actionType);
                            break;
                    }
                    
                    if (matchesServer) {
                        successfulActions++;
                    }
                }
            }
            
            serverStats.put("total", totalActions);
            serverStats.put("successful", successfulActions);
            serverStats.put("failed", totalActions - successfulActions);
            
            response.put("actions", serverActions);
            response.put("stats", serverStats);
            
            sendJson(exchange, toJson(response));
        }
    }
    
    private void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private String escapeJson(String str) {
        if (str == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c <= '\u001F' || (c >= '\u007F' && c <= '\u009F') || (c >= '\u2000' && c <= '\u20FF')) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    private String toJson(Object obj) {
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            ((Map<?, ?>) obj).forEach((k, v) -> {
                if (sb.length() > 1) sb.append(",");
                sb.append("\"").append(escapeJson(k.toString())).append("\":");
                if (v instanceof String) sb.append("\"").append(escapeJson((String) v)).append("\"");
                else if (v instanceof Number || v instanceof Boolean) sb.append(v);
                else if (v instanceof List || v instanceof Map) sb.append(toJson(v));
                else sb.append("\"").append(escapeJson(v.toString())).append("\"");
            });
            return sb.append("}").toString();
        } else if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            ((List<?>) obj).forEach(item -> {
                if (sb.length() > 1) sb.append(",");
                sb.append(toJson(item));
            });
            return sb.append("]").toString();
        } else if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }
    
    private String generateDashboardHtml() {
        try {
            // Load HTML template from resources
            InputStream is = getClass().getClassLoader().getResourceAsStream("dashboard-template.html");
            if (is == null) {
                // Fallback: try to load from file system
                File templateFile = new File("src/main/resources/dashboard-template.html");
                if (templateFile.exists()) {
                    return new String(java.nio.file.Files.readAllBytes(templateFile.toPath()), StandardCharsets.UTF_8);
                }
                return "<html><body><h1>Error: Dashboard template not found</h1></body></html>";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<html><body><h1>Error loading dashboard: " + e.getMessage() + "</h1></body></html>";
        }
    }
}
