package com.blacklight.uac.ui;

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
 * SelfHealingDashboard - Modern self-healing system monitoring UI
 * 
 * Features:
 * - Multi-system monitoring with selection
 * - Fix tracking with UUID for each flow
 * - Step-by-step flow visualization (detection → fix → deployment)
 * - MCP server action tracking
 * - AI model decisions and recipes
 * - Summary dashboard with statistics
 * - Health score graphs
 * - Alarm management
 */
public class SelfHealingDashboard {
    
    private final HttpServer server;
    private final int port;
    private final ScheduledExecutorService scheduler;
    
    // Core data structures - PUBLIC for SimpleDashboard access
    public final Map<String, SystemMonitor> systems;
    public String selectedSystem;
    public final List<HealingFlow> allFlows;
    public final List<Alarm> alarms;
    public final SummaryStats stats;
    
    public SelfHealingDashboard(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.scheduler = Executors.newScheduledThreadPool(4);
        
        this.systems = new ConcurrentHashMap<>();
        this.selectedSystem = null;
        this.allFlows = Collections.synchronizedList(new ArrayList<>());
        this.alarms = Collections.synchronizedList(new ArrayList<>());
        this.stats = new SummaryStats();
        
        setupRoutes();
        startMetricsCollection();
    }
    
    private void setupRoutes() {
        try {
            // Static HTML dashboard
            server.createContext("/", new DashboardHandler());
            
            // API endpoints
            server.createContext("/api/systems", new SystemsHandler());
            server.createContext("/api/systems/select", new SelectSystemHandler());
            server.createContext("/api/system/current", new CurrentSystemHandler());
            server.createContext("/api/flows", new FlowsHandler());
            server.createContext("/api/flows/", new FlowDetailHandler());
            server.createContext("/api/alarms", new AlarmsHandler());
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/health-graph", new HealthGraphHandler());
            
            server.setExecutor(null);
        } catch (IllegalArgumentException e) {
            System.out.println("Dashboard routes already initialized");
        }
    }
    
    private void startMetricsCollection() {
        scheduler.scheduleAtFixedRate(() -> {
            // Update system metrics
            for (SystemMonitor system : systems.values()) {
                system.updateMetrics();
            }
            // Update stats
            updateStats();
        }, 0, 2, TimeUnit.SECONDS);
    }
    
    // ========== Data Models ==========
    
    public static class SystemMonitor {
        public String id;
        public String name;
        public double healthScore;
        public long lastUpdate;
        public List<Alarm> systemAlarms;
        public Map<String, Object> metrics;
        
        public SystemMonitor(String name) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.healthScore = 1.0;
            this.lastUpdate = System.currentTimeMillis();
            this.systemAlarms = new ArrayList<>();
            this.metrics = new ConcurrentHashMap<>();
        }
        
        public void updateMetrics() {
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    
    public static class HealingFlow {
        public String id; // UUID for tracking
        public String systemId;
        public long createdAt;
        public String type; // CODE_FIX, OPERATIONAL_FIX, DEPLOYMENT_FIX
        public AnomalyDetails anomaly;
        public List<FlowStep> steps;
        public FlowStatus status;
        public String health; // CRITICAL, WARNING, HEALTHY
        // Optional UI metadata used by SimpleDashboard for dependency-aware visualization.
        public String workflowStatus; // COMPLETED, WAITING_DEPENDENCIES, FAILED, RUNNING
        public List<Map<String, Object>> deploymentDependencies;
        public Map<String, Object> journey;
        public Map<String, Object> ticket;
        
        public HealingFlow(String systemId, String type) {
            this.id = UUID.randomUUID().toString();
            this.systemId = systemId;
            this.type = type;
            this.createdAt = System.currentTimeMillis();
            this.steps = new ArrayList<>();
            this.status = FlowStatus.INITIATED;
            this.workflowStatus = "RUNNING";
            this.deploymentDependencies = new ArrayList<>();
            this.journey = new HashMap<>();
            this.ticket = new HashMap<>();
        }
    }
    
    public static class AnomalyDetails {
        public String anomalyId;
        public String anomalyType; // NULL_POINTER_EXCEPTION, OUT_OF_MEMORY_ERROR, etc.
        public String severity;
        public long detectedAt;
        public String message;
        public String sourceFile;
        public int lineNumber;
        public String stackTrace;
    }
    
    public static class FlowStep {
        public String stepId;
        public String phase; // SIGNAL, CONTEXT, HYPOTHESIS, EXECUTION, VALIDATION
        public String action; // Detection, Analysis, AI Decision, PR Creation, Deployment, etc.
        public long timestamp;
        public String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED
        public String mcpServer; // TelemetryMCP, DevelopmentMCP, DeploymentMCP, etc.
        public Map<String, Object> details; // AI model, decision, recipe, etc.
        
        public FlowStep(String phase, String action, String mcpServer) {
            this.stepId = UUID.randomUUID().toString();
            this.phase = phase;
            this.action = action;
            this.mcpServer = mcpServer;
            this.timestamp = System.currentTimeMillis();
            this.status = "PENDING";
            this.details = new ConcurrentHashMap<>();
        }
    }
    
    public static class Alarm {
        public String id;
        public String systemId;
        public String alarmType; // ANOMALY_DETECTED, RECOVERY_STARTED, RECOVERY_SUCCESS, RECOVERY_FAILED
        public long createdAt;
        public String message;
        public String severity; // LOW, MEDIUM, HIGH, CRITICAL
        public boolean resolved;
        public String relatedFlowId; // Link to healing flow
        
        public Alarm(String systemId, String alarmType, String severity, String message) {
            this.id = UUID.randomUUID().toString();
            this.systemId = systemId;
            this.alarmType = alarmType;
            this.severity = severity;
            this.message = message;
            this.createdAt = System.currentTimeMillis();
            this.resolved = false;
        }
    }
    
    public static class SummaryStats {
        public int totalAnomaliesDetected = 0;
        public int totalFixesApplied = 0;
        public int codeFixesApplied = 0;
        public int operationalFixesApplied = 0;
        public int deploymentFixesApplied = 0;
        public int successfulHealing = 0;
        public int failedHealing = 0;
        public double averageHealthScore = 1.0;
        public Map<String, Integer> anomalyTypeCount = new HashMap<>();
        public List<Double> healthHistory = Collections.synchronizedList(new ArrayList<>());
    }
    
    public enum FlowStatus {
        INITIATED, CONTEXT_GATHERED, HYPOTHESIS_CREATED, EXECUTION_STARTED, VALIDATION_COMPLETE, FAILED
    }
    
    // ========== Public Methods ==========
    
    public void registerSystem(String systemName) {
        SystemMonitor system = new SystemMonitor(systemName);
        systems.put(systemName, system);
        if (this.selectedSystem == null) {
            this.selectedSystem = system.id;
        }
    }
    
    public String getSystemId(String systemName) {
        SystemMonitor system = systems.get(systemName);
        return system != null ? system.id : systemName;
    }

    public SystemMonitor findSystem(String selection) {
        if (selection == null || selection.isBlank()) {
            return null;
        }

        SystemMonitor byName = systems.get(selection);
        if (byName != null) {
            return byName;
        }

        for (SystemMonitor system : systems.values()) {
            if (selection.equals(system.id)) {
                return system;
            }
        }

        return null;
    }
    
    public void selectSystem(String systemName) {
        SystemMonitor system = findSystem(systemName);
        if (system != null) {
            this.selectedSystem = system.id;
        }
    }
    
    public String getSelectedSystem() {
        return selectedSystem;
    }
    
    public void addHealingFlow(HealingFlow flow) {
        allFlows.add(flow);
        stats.totalFixesApplied++;
        
        if ("CODE_FIX".equals(flow.type)) {
            stats.codeFixesApplied++;
        } else if ("OPERATIONAL_FIX".equals(flow.type)) {
            stats.operationalFixesApplied++;
        } else if ("DEPLOYMENT_FIX".equals(flow.type)) {
            stats.deploymentFixesApplied++;
        }
    }
    
    public void addAlarm(Alarm alarm) {
        alarms.add(alarm);
        SystemMonitor system = findSystem(alarm.systemId);
        if (system != null) {
            system.systemAlarms.add(alarm);
        }
    }
    
    public void recordAnomalyDetected(String systemId, String anomalyType, String severity) {
        stats.totalAnomaliesDetected++;
        stats.anomalyTypeCount.merge(anomalyType, 1, Integer::sum);
    }
    
    public void updateHealthScore(String systemId, double score) {
        SystemMonitor system = findSystem(systemId);
        if (system != null) {
            system.healthScore = Math.max(0, Math.min(1, score));
            stats.healthHistory.add(score);
            if (stats.healthHistory.size() > 100) {
                stats.healthHistory.remove(0);
            }
        }
    }
    
    public void recordHealingSuccess(String systemId) {
        stats.successfulHealing++;
    }
    
    public void recordHealingFailure(String systemId) {
        stats.failedHealing++;
    }
    
    private void updateStats() {
        if (!systems.isEmpty()) {
            double totalHealth = systems.values().stream()
                .mapToDouble(s -> s.healthScore)
                .average()
                .orElse(1.0);
            stats.averageHealthScore = totalHealth;
        }
    }
    
    public void start() {
        server.start();
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           SELF-HEALING SYSTEM DASHBOARD                       ║");
        System.out.println("║           URL: http://localhost:" + port + "                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
    }
    
    public void stop() {
        server.stop(0);
        scheduler.shutdown();
    }
    
    // ========== HTTP Handlers ==========
    
    class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, 0);
            
            String html = generateDashboardHTML();
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        private String generateDashboardHTML() {
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Self-Healing System Dashboard</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            background: #0f1419;
                            color: #e8eaed;
                            line-height: 1.6;
                        }
                        
                        .container {
                            display: grid;
                            grid-template-columns: 250px 1fr;
                            min-height: 100vh;
                        }
                        
                        .sidebar {
                            background: #1a1f26;
                            border-right: 1px solid #2d333b;
                            padding: 20px;
                            overflow-y: auto;
                        }
                        
                        .sidebar h2 {
                            font-size: 12px;
                            font-weight: 600;
                            text-transform: uppercase;
                            color: #7d8590;
                            margin: 20px 0 10px;
                            letter-spacing: 0.5px;
                        }
                        
                        .system-item {
                            padding: 10px 12px;
                            margin: 5px 0;
                            border-radius: 6px;
                            cursor: pointer;
                            transition: all 0.2s;
                            font-size: 13px;
                            border-left: 3px solid transparent;
                        }
                        
                        .system-item:hover {
                            background: #262d36;
                            border-left-color: #0969da;
                        }
                        
                        .system-item.active {
                            background: #0969da;
                            color: white;
                            border-left-color: white;
                        }
                        
                        .nav-item {
                            padding: 8px 12px;
                            margin: 3px 0;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 13px;
                            transition: background 0.2s;
                        }
                        
                        .nav-item:hover {
                            background: #262d36;
                        }
                        
                        .nav-item.active {
                            background: #0969da;
                            color: white;
                        }
                        
                        .main {
                            padding: 20px;
                            overflow-y: auto;
                        }
                        
                        .header {
                            margin-bottom: 20px;
                        }
                        
                        .header h1 {
                            font-size: 24px;
                            margin-bottom: 5px;
                        }
                        
                        .header p {
                            font-size: 13px;
                            color: #7d8590;
                        }
                        
                        .grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                            gap: 12px;
                            margin-bottom: 20px;
                        }
                        
                        .stat-card {
                            background: #1a1f26;
                            border: 1px solid #2d333b;
                            border-radius: 8px;
                            padding: 12px;
                            cursor: pointer;
                            transition: border-color 0.2s;
                        }
                        
                        .stat-card:hover {
                            border-color: #0969da;
                        }
                        
                        .stat-title {
                            font-size: 11px;
                            font-weight: 600;
                            color: #7d8590;
                            text-transform: uppercase;
                            margin-bottom: 6px;
                        }
                        
                        .stat-value {
                            font-size: 24px;
                            font-weight: 600;
                        }
                        
                        .stat-detail {
                            font-size: 11px;
                            color: #7d8590;
                            margin-top: 4px;
                        }
                        
                        .section {
                            margin-bottom: 25px;
                        }
                        
                        .section-title {
                            font-size: 14px;
                            font-weight: 600;
                            margin-bottom: 12px;
                            padding-bottom: 8px;
                            border-bottom: 1px solid #2d333b;
                        }
                        
                        .list {
                            display: flex;
                            flex-direction: column;
                            gap: 8px;
                        }
                        
                        .list-item {
                            background: #1a1f26;
                            border: 1px solid #2d333b;
                            border-radius: 6px;
                            padding: 10px;
                            cursor: pointer;
                            transition: all 0.2s;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        
                        .list-item:hover {
                            border-color: #0969da;
                            background: #161b22;
                        }
                        
                        .list-item-main {
                            flex: 1;
                        }
                        
                        .list-item-title {
                            font-size: 12px;
                            font-weight: 500;
                            margin-bottom: 4px;
                        }
                        
                        .list-item-meta {
                            font-size: 11px;
                            color: #7d8590;
                        }
                        
                        .badge {
                            display: inline-block;
                            padding: 2px 6px;
                            border-radius: 3px;
                            font-size: 10px;
                            font-weight: 600;
                            margin-left: 8px;
                        }
                        
                        .badge.code { background: #0969da33; color: #0969da; }
                        .badge.operational { background: #d29922ff; color: #ffd700; }
                        .badge.success { background: #3fb95033; color: #3fb950; }
                        .badge.warning { background: #d2992233; color: #d29922; }
                        .badge.error { background: #f8514933; color: #f85149; }
                        
                        .modal {
                            display: none;
                            position: fixed;
                            z-index: 1000;
                            left: 0;
                            top: 0;
                            width: 100%;
                            height: 100%;
                            background-color: rgba(0, 0, 0, 0.5);
                            animation: fadeIn 0.3s;
                        }
                        
                        @keyframes fadeIn {
                            from { opacity: 0; }
                            to { opacity: 1; }
                        }
                        
                        .modal.active {
                            display: flex;
                        }
                        
                        .modal-content {
                            background: #1a1f26;
                            margin: auto;
                            padding: 20px;
                            border: 1px solid #2d333b;
                            border-radius: 8px;
                            width: 90%;
                            max-width: 900px;
                            max-height: 80vh;
                            overflow-y: auto;
                            position: relative;
                        }
                        
                        .modal-close {
                            position: absolute;
                            right: 15px;
                            top: 15px;
                            font-size: 24px;
                            cursor: pointer;
                            color: #7d8590;
                        }
                        
                        .modal-close:hover {
                            color: #e8eaed;
                        }
                        
                        .modal-header {
                            font-size: 16px;
                            font-weight: 600;
                            margin-bottom: 15px;
                            padding-bottom: 10px;
                            border-bottom: 1px solid #2d333b;
                        }
                        
                        .detail-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                            gap: 12px;
                            margin-bottom: 15px;
                        }
                        
                        .detail-item {
                            background: #0f1419;
                            border: 1px solid #2d333b;
                            border-radius: 6px;
                            padding: 10px;
                        }
                        
                        .detail-label {
                            font-size: 10px;
                            font-weight: 600;
                            color: #7d8590;
                            text-transform: uppercase;
                            margin-bottom: 4px;
                        }
                        
                        .detail-value {
                            font-size: 12px;
                            word-break: break-all;
                            font-family: monospace;
                        }
                        
                        .flow-step {
                            background: #0f1419;
                            border-left: 3px solid #0969da;
                            border-radius: 4px;
                            padding: 10px;
                            margin-bottom: 8px;
                        }
                        
                        .step-phase {
                            display: inline-block;
                            background: #0969da;
                            color: white;
                            padding: 2px 6px;
                            border-radius: 3px;
                            font-size: 10px;
                            font-weight: 600;
                            margin-bottom: 6px;
                        }
                        
                        .step-title {
                            font-size: 12px;
                            font-weight: 500;
                            margin-bottom: 4px;
                        }
                        
                        .step-mcp {
                            font-size: 10px;
                            color: #7d8590;
                            margin-bottom: 4px;
                        }
                        
                        .step-details {
                            font-size: 11px;
                            color: #7d8590;
                            margin-top: 6px;
                            padding-top: 6px;
                            border-top: 1px solid #2d333b;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="sidebar">
                            <h2>Systems</h2>
                            <div id="systems-list"></div>
                            
                            <h2>Views</h2>
                            <div class="nav-item active" onclick="showDashboard()">📊 Dashboard</div>
                            <div class="nav-item" onclick="showFlows()">🔧 Fixes</div>
                            <div class="nav-item" onclick="showAlarms()">🚨 Alarms</div>
                        </div>
                        
                        <div class="main">
                            <div class="header">
                                <h1>Self-Healing System</h1>
                                <p id="system-info">Select a system to monitor</p>
                            </div>
                            
                            <div id="dashboard-section">
                                <div class="grid" id="summary-cards"></div>
                                
                                <div class="section">
                                    <div class="section-title">Recent Fixes</div>
                                    <div class="list" id="recent-flows"></div>
                                </div>
                                
                                <div class="section">
                                    <div class="section-title">Active Alarms</div>
                                    <div class="list" id="active-alarms"></div>
                                </div>
                            </div>
                            
                            <div id="flows-section" style="display:none;">
                                <div class="section">
                                    <div class="section-title">All Healing Flows</div>
                                    <div class="list" id="all-flows"></div>
                                </div>
                            </div>
                            
                            <div id="alarms-section" style="display:none;">
                                <div class="section">
                                    <div class="section-title">All Alarms</div>
                                    <div class="list" id="all-alarms"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <!-- Flow Detail Modal -->
                    <div id="flowModal" class="modal">
                        <div class="modal-content">
                            <span class="modal-close" onclick="closeFlowModal()">&times;</span>
                            <div class="modal-header">Healing Flow Details</div>
                            <div id="flowDetails"></div>
                        </div>
                    </div>
                    
                    <!-- System Health Modal -->
                    <div id="systemModal" class="modal">
                        <div class="modal-content">
                            <span class="modal-close" onclick="closeSystemModal()">&times;</span>
                            <div class="modal-header">System Health Details</div>
                            <div id="systemDetails"></div>
                        </div>
                    </div>
                    
                    <!-- Alarm Detail Modal -->
                    <div id="alarmModal" class="modal">
                        <div class="modal-content">
                            <span class="modal-close" onclick="closeAlarmModal()">&times;</span>
                            <div class="modal-header">Alarm Details</div>
                            <div id="alarmDetails"></div>
                        </div>
                    </div>
                    
                    <script>
                        let selectedSystem = null;
                        let flows = [];
                        let alarms = [];
                        
                        async function loadData() {
                            const systems = await fetch('/api/systems').then(r => r.json());
                            renderSystems(systems);
                            
                            // Auto-select first system if none selected
                            if (!selectedSystem && systems.length > 0) {
                                await selectSystem(systems[0].id, systems[0].name);
                            } else if (selectedSystem) {
                                flows = await fetch('/api/flows').then(r => r.json());
                                alarms = await fetch('/api/alarms').then(r => r.json());
                                updateDashboard();
                            }
                        }
                        
                        function renderSystems(systems) {
                            const html = systems.map(sys => `
                                <div class="system-item ${sys.id === selectedSystem ? 'active' : ''}" 
                                     onclick="selectSystem('${sys.id}', '${sys.name}')">
                                    <strong>${sys.name}</strong>
                                    <div style="font-size: 11px; color: #7d8590;">
                                        Health: ${(sys.healthScore * 100).toFixed(0)}% | Alarms: ${sys.alarmCount}
                                    </div>
                                </div>
                            `).join('');
                            document.getElementById('systems-list').innerHTML = html;
                        }
                        
                        async function selectSystem(sysId, sysName) {
                            selectedSystem = sysId;
                            await fetch('/api/systems/select', {
                                method: 'POST',
                                body: JSON.stringify({systemId: sysId, systemName: sysName})
                            });
                            flows = await fetch('/api/flows').then(r => r.json());
                            alarms = await fetch('/api/alarms').then(r => r.json());
                            await updateDashboard();
                        }
                        
                        async function updateDashboard() {
                            const data = await fetch('/api/system/current').then(r => r.json());
                            document.getElementById('system-info').innerHTML = 
                                `<strong>${data.name}</strong> | Health: ${(data.healthScore * 100).toFixed(0)}% | Alarms: ${data.alarmCount} | Fixes: ${data.totalFixes}`;
                            
                            renderSummaryCards(data);
                            renderRecentFlows();
                            renderActiveAlarms();
                        }
                        
                        function renderSummaryCards(data) {
                            const cards = `
                                <div class="stat-card" onclick="showSystemModal()">
                                    <div class="stat-title">Health Score</div>
                                    <div class="stat-value" style="color: ${data.healthScore > 0.7 ? '#3fb950' : data.healthScore > 0.4 ? '#d29922' : '#f85149'}">
                                        ${(data.healthScore * 100).toFixed(0)}%
                                    </div>
                                    <div class="stat-detail">Click for details</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-title">Anomalies</div>
                                    <div class="stat-value">${data.totalAnomalies}</div>
                                    <div class="stat-detail">This period</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-title">Fixes Applied</div>
                                    <div class="stat-value">${data.totalFixes}</div>
                                    <div class="stat-detail">${data.successfulFixes} successful</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-title">Code Fixes</div>
                                    <div class="stat-value">${data.codeFixes}</div>
                                    <div class="stat-detail">Via Git PRs</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-title">System Fixes</div>
                                    <div class="stat-value">${data.operationalFixes}</div>
                                    <div class="stat-detail">Infrastructure</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-title">Success Rate</div>
                                    <div class="stat-value">${data.totalFixes > 0 ? ((data.successfulFixes/data.totalFixes)*100).toFixed(0) : 'N/A'}%</div>
                                    <div class="stat-detail">Healing success</div>
                                </div>
                            `;
                            document.getElementById('summary-cards').innerHTML = cards;
                        }
                        
                        function renderRecentFlows() {
                            const recent = flows.slice(0, 5);
                            const html = recent.map(flow => `
                                <div class="list-item" onclick="showFlowModal('${flow.id}')">
                                    <div class="list-item-main">
                                        <div class="list-item-title">
                                            Flow: ${flow.id.substring(0, 8)}...
                                            <span class="badge ${flow.type.toLowerCase().replace('_', '')}">${flow.type}</span>
                                        </div>
                                        <div class="list-item-meta">
                                            ${flow.anomaly} | ${flow.stepCount} steps | ${flow.status}
                                        </div>
                                    </div>
                                </div>
                            `).join('');
                            document.getElementById('recent-flows').innerHTML = html || '<p style="color: #7d8590;">No recent flows</p>';
                        }
                        
                        function renderActiveAlarms() {
                            const active = alarms.filter(a => !a.resolved).slice(0, 5);
                            const html = active.map(alarm => `
                                <div class="list-item" onclick="showAlarmModal('${alarm.id}')">
                                    <div class="list-item-main">
                                        <div class="list-item-title">
                                            ${alarm.type}
                                            <span class="badge ${alarm.severity.toLowerCase()}">${alarm.severity}</span>
                                        </div>
                                        <div class="list-item-meta">${alarm.message.substring(0, 60)}...</div>
                                    </div>
                                </div>
                            `).join('');
                            document.getElementById('active-alarms').innerHTML = html || '<p style="color: #7d8590;">No active alarms</p>';
                        }
                        
                        function showFlowModal(flowId) {
                            fetch('/api/flows/' + flowId).then(r => r.json()).then(flow => {
                                const stepsHtml = flow.steps.map(step => `
                                    <div class="flow-step">
                                        <div class="step-phase">${step.phase}</div>
                                        <div class="step-title">${step.action}</div>
                                        <div class="step-mcp">MCP Server: ${step.mcpServer}</div>
                                        <div class="step-details">
                                            <strong>Status:</strong> ${step.status}<br>
                                            <strong>Details:</strong> ${JSON.stringify(step.details, null, 2)}
                                        </div>
                                    </div>
                                `).join('');
                                
                                const content = `
                                    <div class="detail-grid">
                                        <div class="detail-item">
                                            <div class="detail-label">Flow ID</div>
                                            <div class="detail-value">${flow.id}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Type</div>
                                            <div class="detail-value">${flow.type}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Status</div>
                                            <div class="detail-value">${flow.status}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Created</div>
                                            <div class="detail-value">${new Date(flow.createdAt).toLocaleString()}</div>
                                        </div>
                                    </div>
                                    <div class="detail-grid">
                                        <div class="detail-item">
                                            <div class="detail-label">Anomaly Type</div>
                                            <div class="detail-value">${flow.anomaly?.anomalyType || 'N/A'}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Severity</div>
                                            <div class="detail-value">${flow.anomaly?.severity || 'N/A'}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Source File</div>
                                            <div class="detail-value">${flow.anomaly?.sourceFile || 'N/A'}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Line Number</div>
                                            <div class="detail-value">${flow.anomaly?.lineNumber || 'N/A'}</div>
                                        </div>
                                    </div>
                                    <div style="margin-top: 15px;">
                                        <strong style="font-size: 12px;">Healing Steps:</strong>
                                        <div style="margin-top: 10px;">${stepsHtml}</div>
                                    </div>
                                `;
                                document.getElementById('flowDetails').innerHTML = content;
                                document.getElementById('flowModal').classList.add('active');
                            });
                        }
                        
                        function showSystemModal() {
                            fetch('/api/system/current').then(r => r.json()).then(data => {
                                const content = `
                                    <div class="detail-grid">
                                        <div class="detail-item">
                                            <div class="detail-label">System</div>
                                            <div class="detail-value">${data.name}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Health Score</div>
                                            <div class="detail-value">${(data.healthScore * 100).toFixed(1)}%</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Alarms</div>
                                            <div class="detail-value">${data.alarmCount}</div>
                                        </div>
                                    </div>
                                    <div class="detail-grid">
                                        <div class="detail-item">
                                            <div class="detail-label">Total Anomalies</div>
                                            <div class="detail-value">${data.totalAnomalies}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Total Fixes</div>
                                            <div class="detail-value">${data.totalFixes}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Successful Fixes</div>
                                            <div class="detail-value">${data.successfulFixes}</div>
                                        </div>
                                    </div>
                                    <div class="detail-grid">
                                        <div class="detail-item">
                                            <div class="detail-label">Code Fixes</div>
                                            <div class="detail-value">${data.codeFixes}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Operational Fixes</div>
                                            <div class="detail-value">${data.operationalFixes}</div>
                                        </div>
                                        <div class="detail-item">
                                            <div class="detail-label">Deployment Fixes</div>
                                            <div class="detail-value">${data.deploymentFixes}</div>
                                        </div>
                                    </div>
                                `;
                                document.getElementById('systemDetails').innerHTML = content;
                                document.getElementById('systemModal').classList.add('active');
                            });
                        }
                        
                        function showAlarmModal(alarmId) {
                            const alarm = alarms.find(a => a.id === alarmId);
                            const content = `
                                <div class="detail-grid">
                                    <div class="detail-item">
                                        <div class="detail-label">Alarm ID</div>
                                        <div class="detail-value">${alarm.id}</div>
                                    </div>
                                    <div class="detail-item">
                                        <div class="detail-label">Type</div>
                                        <div class="detail-value">${alarm.type}</div>
                                    </div>
                                    <div class="detail-item">
                                        <div class="detail-label">Severity</div>
                                        <div class="detail-value">${alarm.severity}</div>
                                    </div>
                                    <div class="detail-item">
                                        <div class="detail-label">Created</div>
                                        <div class="detail-value">${new Date(alarm.createdAt).toLocaleString()}</div>
                                    </div>
                                </div>
                                <div style="background: #0f1419; border: 1px solid #2d333b; border-radius: 6px; padding: 10px; margin-top: 10px;">
                                    <div style="font-size: 11px; color: #7d8590; margin-bottom: 6px;">MESSAGE</div>
                                    <div style="font-size: 12px;">${alarm.message}</div>
                                </div>
                            `;
                            document.getElementById('alarmDetails').innerHTML = content;
                            document.getElementById('alarmModal').classList.add('active');
                        }
                        
                        function closeFlowModal() { document.getElementById('flowModal').classList.remove('active'); }
                        function closeSystemModal() { document.getElementById('systemModal').classList.remove('active'); }
                        function closeAlarmModal() { document.getElementById('alarmModal').classList.remove('active'); }
                        
                        function showDashboard() {
                            document.getElementById('dashboard-section').style.display = 'block';
                            document.getElementById('flows-section').style.display = 'none';
                            document.getElementById('alarms-section').style.display = 'none';
                            document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
                            event.target.classList.add('active');
                        }
                        
                        function showFlows() {
                            document.getElementById('dashboard-section').style.display = 'none';
                            document.getElementById('flows-section').style.display = 'block';
                            document.getElementById('alarms-section').style.display = 'none';
                            const html = flows.map(flow => `
                                <div class="list-item" onclick="showFlowModal('${flow.id}')">
                                    <div class="list-item-main">
                                        <div class="list-item-title">
                                            ${flow.id.substring(0, 12)}...
                                            <span class="badge ${flow.type.toLowerCase().replace('_', '')}">${flow.type}</span>
                                        </div>
                                        <div class="list-item-meta">
                                            ${flow.anomaly} | ${flow.stepCount} steps | ${flow.status}
                                        </div>
                                    </div>
                                </div>
                            `).join('');
                            document.getElementById('all-flows').innerHTML = html;
                            document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
                            event.target.classList.add('active');
                        }
                        
                        function showAlarms() {
                            document.getElementById('dashboard-section').style.display = 'none';
                            document.getElementById('flows-section').style.display = 'none';
                            document.getElementById('alarms-section').style.display = 'block';
                            const html = alarms.map(alarm => `
                                <div class="list-item" onclick="showAlarmModal('${alarm.id}')">
                                    <div class="list-item-main">
                                        <div class="list-item-title">
                                            ${alarm.type}
                                            <span class="badge ${alarm.severity.toLowerCase()}">${alarm.severity}</span>
                                        </div>
                                        <div class="list-item-meta">${alarm.message}</div>
                                    </div>
                                </div>
                            `).join('');
                            document.getElementById('all-alarms').innerHTML = html;
                            document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
                            event.target.classList.add('active');
                        }
                        
                        // Auto-refresh every 2 seconds
                        loadData();
                        setInterval(loadData, 2000);
                    </script>
                </body>
                </html>
                """;
        }
    }
    
    class SystemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> systemsJson = new ArrayList<>();
            for (SystemMonitor sys : systems.values()) {
                Map<String, Object> obj = new HashMap<>();
                obj.put("id", sys.id);
                obj.put("name", sys.name);
                obj.put("healthScore", sys.healthScore);
                obj.put("lastUpdate", sys.lastUpdate);
                obj.put("alarmCount", sys.systemAlarms.size());
                systemsJson.add(obj);
            }
            
            sendJson(exchange, systemsJson);
        }
    }
    
    class SelectSystemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            
            // Parse JSON to get systemId
            String json = body.toString();
            String systemId = null;
            
            // Extract systemId from JSON: {"systemId":"UUID","systemName":"name"}
            if (json.contains("\"systemId\"")) {
                String[] parts = json.split("\"systemId\"")[1].split("\"");
                if (parts.length >= 2) {
                    systemId = parts[1];
                }
            }
            
            // If we found the ID, select that system
            if (systemId != null && !systemId.isEmpty()) {
                selectedSystem = systemId;
            }
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("{\"ok\":true}".getBytes());
            }
        }
    }
    
    class CurrentSystemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SystemMonitor sys = systems.get(selectedSystem);
            Map<String, Object> obj = new HashMap<>();
            
            if (sys != null) {
                // Count system-specific flows
                int systemFlows = (int) allFlows.stream().filter(f -> f.systemId.equals(selectedSystem)).count();
                int systemCodeFixes = (int) allFlows.stream().filter(f -> f.systemId.equals(selectedSystem) && "CODE_FIX".equals(f.type)).count();
                int systemOpFixes = (int) allFlows.stream().filter(f -> f.systemId.equals(selectedSystem) && "OPERATIONAL_FIX".equals(f.type)).count();
                int systemDeployFixes = (int) allFlows.stream().filter(f -> f.systemId.equals(selectedSystem) && "DEPLOYMENT_FIX".equals(f.type)).count();
                int systemFeatureFlows = (int) allFlows.stream().filter(f -> f.systemId.equals(selectedSystem) && "FEATURE_DELIVERY".equals(f.type)).count();
                int systemAnomalies = (int) allFlows.stream().filter(f -> f.systemId.equals(selectedSystem)).count();
                
                obj.put("id", sys.id);
                obj.put("name", sys.name);
                obj.put("healthScore", sys.healthScore);
                obj.put("totalAnomalies", systemAnomalies);
                obj.put("totalFixes", systemFlows);
                obj.put("successfulFixes", systemFlows);
                obj.put("codeFixes", systemCodeFixes);
                obj.put("operationalFixes", systemOpFixes);
                obj.put("deploymentFixes", systemDeployFixes);
                obj.put("featureFlows", systemFeatureFlows);
                obj.put("alarmCount", sys.systemAlarms.size());
                obj.put("metrics", new HashMap<>(sys.metrics));
            }
            
            sendJson(exchange, obj);
        }
    }
    
    class FlowsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> flowsJson = new ArrayList<>();
            for (HealingFlow flow : allFlows) {
                if (flow.systemId.equals(selectedSystem)) {
                    Map<String, Object> obj = new HashMap<>();
                    obj.put("id", flow.id);
                    obj.put("type", flow.type);
                    obj.put("status", flow.status.toString());
                    obj.put("createdAt", flow.createdAt);
                    obj.put("anomaly", flow.anomaly != null ? flow.anomaly.anomalyType : "Unknown");
                    obj.put("stepCount", flow.steps.size());
                    flowsJson.add(obj);
                }
            }
            
            sendJson(exchange, flowsJson);
        }
    }
    
    class FlowDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String flowId = exchange.getRequestURI().getPath().replace("/api/flows/", "");
            
            HealingFlow flow = allFlows.stream()
                .filter(f -> f.id.equals(flowId))
                .findFirst()
                .orElse(null);
            
            if (flow != null) {
                Map<String, Object> obj = new HashMap<>();
                obj.put("id", flow.id);
                obj.put("type", flow.type);
                obj.put("status", flow.status.toString());
                obj.put("createdAt", flow.createdAt);
                obj.put("anomaly", flow.anomaly);
                obj.put("steps", flow.steps);
                
                sendJson(exchange, obj);
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            }
        }
    }
    
    class AlarmsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> alarmsJson = new ArrayList<>();
            for (Alarm alarm : alarms) {
                if (alarm.systemId.equals(selectedSystem) && !alarm.resolved) {
                    Map<String, Object> obj = new HashMap<>();
                    obj.put("id", alarm.id);
                    obj.put("type", alarm.alarmType);
                    obj.put("severity", alarm.severity);
                    obj.put("message", alarm.message);
                    obj.put("createdAt", alarm.createdAt);
                    alarmsJson.add(obj);
                }
            }
            
            sendJson(exchange, alarmsJson);
        }
    }
    
    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> obj = new HashMap<>();
            obj.put("totalAnomalies", stats.totalAnomaliesDetected);
            obj.put("totalFixes", stats.totalFixesApplied);
            obj.put("successfulHealing", stats.successfulHealing);
            obj.put("failedHealing", stats.failedHealing);
            obj.put("codeFixes", stats.codeFixesApplied);
            obj.put("operationalFixes", stats.operationalFixesApplied);
            obj.put("deploymentFixes", stats.deploymentFixesApplied);
            obj.put("averageHealth", stats.averageHealthScore);
            obj.put("anomalyTypes", stats.anomalyTypeCount);
            obj.put("featureFlows", (int) allFlows.stream().filter(f -> "FEATURE_DELIVERY".equals(f.type)).count());
            obj.put("featureFlowsPausedByIncident", (int) allFlows.stream()
                    .filter(f -> "FEATURE_DELIVERY".equals(f.type) && "PAUSED_BY_INCIDENT".equals(f.workflowStatus))
                    .count());
            
            sendJson(exchange, obj);
        }
    }
    
    class HealthGraphHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> obj = new HashMap<>();
            obj.put("healthHistory", stats.healthHistory);
            obj.put("currentHealth", stats.averageHealthScore);
            
            sendJson(exchange, obj);
        }
    }
    
    // ========== Utility Methods ==========
    
    private static void sendJson(HttpExchange exchange, Object obj) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        
        String json = objectToJson(obj);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private static String objectToJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + obj + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            for (var entry : map.entrySet()) {
                sb.append("\"").append(entry.getKey()).append("\":")
                  .append(objectToJson(entry.getValue())).append(",");
            }
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (var item : list) {
                sb.append(objectToJson(item)).append(",");
            }
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            sb.append("]");
            return sb.toString();
        }
        return obj.toString();
    }
}

