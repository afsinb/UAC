package com.blacklight.uac.ui;

import com.blacklight.uac.config.ApplicationConfig;
import com.blacklight.uac.config.BeanConfiguration;
import com.blacklight.uac.core.coordinator.Brain;
import com.blacklight.uac.monitoring.SystemMetricsCollector;
import com.blacklight.uac.monitoring.SystemMetricsCollector.SystemMetricsSnapshot;
import com.blacklight.uac.mcp.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * MonitoringDashboard - Web-based monitoring UI for UAC
 * Provides real-time visibility into the self-healing system
 */
public class MonitoringDashboard {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final HttpServer server;
    private final BeanConfiguration beans;
    private final SystemMetricsCollector metricsCollector;
    private final int port;
    
    // Dashboard state
    private final List<Map<String, Object>> eventLog;
    private final Map<String, Object> systemState;
    private final ScheduledExecutorService scheduler;
    
    public MonitoringDashboard(BeanConfiguration beans, int port) throws IOException {
        this.beans = beans;
        this.port = port;
        this.metricsCollector = new SystemMetricsCollector();
        this.eventLog = Collections.synchronizedList(new ArrayList<>());
        this.systemState = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        setupRoutes();
        
        // Start metrics collection
        startMetricsCollection();
    }
    
    private void setupRoutes() {
        server.createContext("/", new DashboardHandler());
        server.createContext("/api/health", new HealthApiHandler());
        server.createContext("/api/metrics", new MetricsApiHandler());
        server.createContext("/api/events", new EventsApiHandler());
        server.createContext("/api/mcp/status", new McpStatusHandler());
        server.createContext("/api/actions", new ActionsApiHandler());
        server.setExecutor(null);
    }
    
    private void startMetricsCollection() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                SystemMetricsSnapshot metrics = metricsCollector.collect();
                double healthScore = metricsCollector.calculateHealthScore(metrics);
                
                systemState.put("healthScore", healthScore);
                systemState.put("cpuUsage", metrics.cpuUsage);
                systemState.put("memoryUsage", metrics.memoryUsage);
                systemState.put("heapUsage", metrics.heapUsage);
                systemState.put("threadCount", metrics.threadCount);
                systemState.put("systemLoad", metrics.systemLoad);
                systemState.put("timestamp", System.currentTimeMillis());
                
                // Log event if health is critical
                if (healthScore < 0.5) {
                    logEvent("WARNING", "Health score dropped to " + String.format("%.2f", healthScore));
                }
                
            } catch (Exception e) {
                logEvent("ERROR", "Metrics collection failed: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
    }
    
    public void start() {
        server.start();
        System.out.println(GREEN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(GREEN + "║              MONITORING DASHBOARD STARTED                     ║" + RESET);
        System.out.println(GREEN + "╠═══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.println(GREEN + "║" + RESET + "  URL: http://localhost:" + port + "                              " + GREEN + "║" + RESET);
        System.out.println(GREEN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }
    
    public void stop() {
        server.stop(0);
        scheduler.shutdown();
    }
    
    public void logEvent(String level, String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("level", level);
        event.put("message", message);
        event.put("timestamp", System.currentTimeMillis());
        eventLog.add(event);
        
        // Keep only last 100 events
        if (eventLog.size() > 100) {
            eventLog.remove(0);
        }
    }
    
    // Dashboard HTML Handler
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generateDashboardHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    // API Handlers
    private class HealthApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = generateHealthJson();
            sendJsonResponse(exchange, json);
        }
    }
    
    private class MetricsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = generateMetricsJson();
            sendJsonResponse(exchange, json);
        }
    }
    
    private class EventsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = generateEventsJson();
            sendJsonResponse(exchange, json);
        }
    }
    
    private class McpStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = generateMcpStatusJson();
            sendJsonResponse(exchange, json);
        }
    }
    
    private class ActionsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = generateActionsJson();
            sendJsonResponse(exchange, json);
        }
    }
    
    private void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, json.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private String generateDashboardHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>UAC Monitoring Dashboard</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: #0f172a; color: #e2e8f0; min-height: 100vh;
                    }
                    .header {
                        background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
                        padding: 20px 30px; border-bottom: 1px solid #334155;
                    }
                    .header h1 { font-size: 24px; color: #38bdf8; }
                    .header p { color: #94a3b8; margin-top: 5px; }
                    .container { padding: 20px; max-width: 1400px; margin: 0 auto; }
                    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
                    .card {
                        background: #1e293b; border-radius: 12px; padding: 20px;
                        border: 1px solid #334155;
                    }
                    .card h2 { font-size: 16px; color: #94a3b8; margin-bottom: 15px; }
                    .metric { font-size: 36px; font-weight: bold; }
                    .metric.healthy { color: #22c55e; }
                    .metric.warning { color: #eab308; }
                    .metric.critical { color: #ef4444; }
                    .status-badge {
                        display: inline-block; padding: 4px 12px; border-radius: 20px;
                        font-size: 12px; font-weight: 600;
                    }
                    .status-healthy { background: #22c55e20; color: #22c55e; }
                    .status-warning { background: #eab30820; color: #eab308; }
                    .status-critical { background: #ef444420; color: #ef4444; }
                    .progress-bar {
                        height: 8px; background: #334155; border-radius: 4px; overflow: hidden;
                    }
                    .progress-fill { height: 100%; transition: width 0.3s; }
                    .progress-green { background: #22c55e; }
                    .progress-yellow { background: #eab308; }
                    .progress-red { background: #ef4444; }
                    .event-log {
                        max-height: 300px; overflow-y: auto; font-family: monospace; font-size: 12px;
                    }
                    .event { padding: 8px; border-bottom: 1px solid #334155; }
                    .event-time { color: #64748b; }
                    .event-info { color: #38bdf8; }
                    .event-warning { color: #eab308; }
                    .event-error { color: #ef4444; }
                    .mcp-server {
                        display: flex; align-items: center; gap: 10px; padding: 10px;
                        background: #0f172a; border-radius: 8px; margin-bottom: 10px;
                    }
                    .mcp-status { width: 10px; height: 10px; border-radius: 50%; }
                    .mcp-online { background: #22c55e; }
                    .mcp-offline { background: #ef4444; }
                    .refresh-btn {
                        background: #3b82f6; color: white; border: none; padding: 10px 20px;
                        border-radius: 8px; cursor: pointer; font-weight: 600;
                    }
                    .refresh-btn:hover { background: #2563eb; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>🛡️ Universal Autonomous Core - Monitoring Dashboard</h1>
                    <p>Real-time self-healing system monitoring</p>
                </div>
                
                <div class="container">
                    <div class="grid">
                        <!-- Health Score Card -->
                        <div class="card">
                            <h2>System Health Score</h2>
                            <div class="metric healthy" id="healthScore">0.85</div>
                            <div id="healthStatus"><span class="status-badge status-healthy">HEALTHY</span></div>
                            <div class="progress-bar" style="margin-top: 15px;">
                                <div class="progress-fill progress-green" id="healthBar" style="width: 85%;"></div>
                            </div>
                        </div>
                        
                        <!-- CPU Usage Card -->
                        <div class="card">
                            <h2>CPU Usage</h2>
                            <div class="metric" id="cpuUsage">45%</div>
                            <div class="progress-bar" style="margin-top: 15px;">
                                <div class="progress-fill progress-green" id="cpuBar" style="width: 45%;"></div>
                            </div>
                        </div>
                        
                        <!-- Memory Usage Card -->
                        <div class="card">
                            <h2>Memory Usage</h2>
                            <div class="metric" id="memoryUsage">62%</div>
                            <div class="progress-bar" style="margin-top: 15px;">
                                <div class="progress-fill progress-yellow" id="memoryBar" style="width: 62%;"></div>
                            </div>
                        </div>
                        
                        <!-- MCP Servers Card -->
                        <div class="card">
                            <h2>MCP Servers</h2>
                            <div id="mcpServers">
                                <div class="mcp-server">
                                    <div class="mcp-status mcp-online"></div>
                                    <span>TelemetryMCP (Observer)</span>
                                </div>
                                <div class="mcp-server">
                                    <div class="mcp-status mcp-online"></div>
                                    <span>HealingMCP (Resolver)</span>
                                </div>
                                <div class="mcp-server">
                                    <div class="mcp-status mcp-online"></div>
                                    <span>DevelopmentMCP (Evolver)</span>
                                </div>
                                <div class="mcp-server">
                                    <div class="mcp-status mcp-online"></div>
                                    <span>DynamicAI (AI Engine)</span>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Event Log Card -->
                        <div class="card" style="grid-column: span 2;">
                            <h2>Event Log</h2>
                            <div class="event-log" id="eventLog">
                                <div class="event">
                                    <span class="event-time">[System]</span>
                                    <span class="event-info">Dashboard initialized</span>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Actions Card -->
                        <div class="card">
                            <h2>Recent Actions</h2>
                            <div id="recentActions">
                                <div class="event">
                                    <span class="event-time">No actions yet</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <div style="margin-top: 20px; text-align: center;">
                        <button class="refresh-btn" onclick="refreshAll()">🔄 Refresh Now</button>
                        <span style="margin-left: 20px; color: #64748b;">Auto-refresh: 2 seconds</span>
                    </div>
                </div>
                
                <script>
                    function refreshAll() {
                        fetch('/api/health').then(r => r.json()).then(updateHealth);
                        fetch('/api/metrics').then(r => r.json()).then(updateMetrics);
                        fetch('/api/events').then(r => r.json()).then(updateEvents);
                        fetch('/api/mcp/status').then(r => r.json()).then(updateMcp);
                    }
                    
                    function updateHealth(data) {
                        const score = data.healthScore || 0;
                        document.getElementById('healthScore').textContent = score.toFixed(2);
                        
                        const bar = document.getElementById('healthBar');
                        bar.style.width = (score * 100) + '%';
                        bar.className = 'progress-fill ' + 
                            (score > 0.7 ? 'progress-green' : (score > 0.4 ? 'progress-yellow' : 'progress-red'));
                        
                        const status = document.getElementById('healthStatus');
                        const badgeClass = score > 0.7 ? 'status-healthy' : (score > 0.4 ? 'status-warning' : 'status-critical');
                        const statusText = score > 0.7 ? 'HEALTHY' : (score > 0.4 ? 'DEGRADED' : 'CRITICAL');
                        status.innerHTML = '<span class="status-badge ' + badgeClass + '">' + statusText + '</span>';
                    }
                    
                    function updateMetrics(data) {
                        const cpu = (data.cpuUsage * 100).toFixed(0);
                        const mem = (data.memoryUsage * 100).toFixed(0);
                        
                        document.getElementById('cpuUsage').textContent = cpu + '%';
                        document.getElementById('memoryUsage').textContent = mem + '%';
                        
                        document.getElementById('cpuBar').style.width = cpu + '%';
                        document.getElementById('memoryBar').style.width = mem + '%';
                    }
                    
                    function updateEvents(data) {
                        const log = document.getElementById('eventLog');
                        log.innerHTML = data.events.map(e => 
                            '<div class="event">' +
                            '<span class="event-time">[' + new Date(e.timestamp).toLocaleTimeString() + ']</span> ' +
                            '<span class="event-' + e.level.toLowerCase() + '">' + e.message + '</span>' +
                            '</div>'
                        ).join('');
                    }
                    
                    function updateMcp(data) {
                        // MCP status updates
                    }
                    
                    // Auto-refresh every 2 seconds
                    setInterval(refreshAll, 2000);
                    refreshAll();
                </script>
            </body>
            </html>
            """;
    }
    
    private String generateHealthJson() {
        double healthScore = (double) systemState.getOrDefault("healthScore", 0.85);
        return String.format("{\"healthScore\": %.2f, \"status\": \"%s\", \"timestamp\": %d}",
            healthScore,
            healthScore > 0.7 ? "HEALTHY" : (healthScore > 0.4 ? "DEGRADED" : "CRITICAL"),
            System.currentTimeMillis()
        );
    }
    
    private String generateMetricsJson() {
        return String.format(
            "{\"cpuUsage\": %.2f, \"memoryUsage\": %.2f, \"heapUsage\": %.2f, \"threadCount\": %d, \"systemLoad\": %.2f}",
            systemState.getOrDefault("cpuUsage", 0.0),
            systemState.getOrDefault("memoryUsage", 0.0),
            systemState.getOrDefault("heapUsage", 0.0),
            systemState.getOrDefault("threadCount", 0),
            systemState.getOrDefault("systemLoad", 0.0)
        );
    }
    
    private String generateEventsJson() {
        StringBuilder sb = new StringBuilder("{\"events\": [");
        for (int i = 0; i < eventLog.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> event = eventLog.get(i);
            sb.append(String.format("{\"level\": \"%s\", \"message\": \"%s\", \"timestamp\": %d}",
                event.get("level"), event.get("message"), event.get("timestamp")));
        }
        sb.append("]}");
        return sb.toString();
    }
    
    private String generateMcpStatusJson() {
        return """
            {
                "servers": [
                    {"name": "TelemetryMCP", "status": "online", "capabilities": 9},
                    {"name": "HealingMCP", "status": "online", "capabilities": 8},
                    {"name": "DevelopmentMCP", "status": "online", "capabilities": 8},
                    {"name": "DynamicAI", "status": "online", "capabilities": 8}
                ]
            }
            """;
    }
    
    private String generateActionsJson() {
        return "{\"actions\": []}";
    }
}
