package com.blacklight.uac.ui;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * SimpleDashboard - Lightweight working version with all features
 * Uses SelfHealingDashboard data model
 */
public class SimpleDashboard {
    
    private final HttpServer server;
    private final int port;
    private final SelfHealingDashboard dashboard;
    
    public SimpleDashboard(SelfHealingDashboard dashboard, int port) throws IOException {
        this.dashboard = dashboard;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        setupRoutes();
    }

    private SelfHealingDashboard.SystemMonitor getSelectedSystemMonitor() {
        return dashboard.findSystem(dashboard.selectedSystem);
    }
    
    private void setupRoutes() {
        try {
            server.createContext("/", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(generateHTML().getBytes(StandardCharsets.UTF_8));
                }
            });
            
            server.createContext("/api/systems", exchange -> {
                List<Map<String, Object>> systems = new ArrayList<>();
                for (var sys : dashboard.systems.values()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", sys.id);
                    m.put("name", sys.name);
                    m.put("healthScore", sys.healthScore);
                    m.put("alarmCount", sys.systemAlarms.size());
                    systems.add(m);
                }
                sendJson(exchange, systems);
            });
            
            server.createContext("/api/systems/select", exchange -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                
                String json = body.toString();
                String sysId = null;
                if (json.contains("\"systemId\"")) {
                    sysId = json.split("\"systemId\"")[1].split("\"")[1];
                }
                if (sysId != null) {
                    dashboard.selectSystem(sysId);
                }
                sendJson(exchange, Map.of("ok", true));
            });
            
            server.createContext("/api/system/current", exchange -> {
                Map<String, Object> data = new HashMap<>();
                SelfHealingDashboard.SystemMonitor sys = getSelectedSystemMonitor();
                if (sys != null) {
                    int systemFlows = (int) dashboard.allFlows.stream().filter(f -> f.systemId.equals(sys.id)).count();
                    int systemCode = (int) dashboard.allFlows.stream().filter(f -> f.systemId.equals(sys.id) && "CODE_FIX".equals(f.type)).count();
                    int systemOp = (int) dashboard.allFlows.stream().filter(f -> f.systemId.equals(sys.id) && "OPERATIONAL_FIX".equals(f.type)).count();
                    
                    data.put("id", sys.id);
                    data.put("name", sys.name);
                    data.put("healthScore", sys.healthScore);
                    data.put("totalAnomalies", systemFlows);
                    data.put("totalFixes", systemFlows);
                    data.put("successfulFixes", systemFlows);
                    data.put("codeFixes", systemCode);
                    data.put("operationalFixes", systemOp);
                    data.put("deploymentFixes", 0);
                    data.put("alarmCount", sys.systemAlarms.size());
                }
                sendJson(exchange, data);
            });
            
            server.createContext("/api/flows", exchange -> {
                List<Map<String, Object>> flows = new ArrayList<>();
                SelfHealingDashboard.SystemMonitor sys = getSelectedSystemMonitor();
                if (sys == null) {
                    sendJson(exchange, flows);
                    return;
                }
                for (var flow : dashboard.allFlows) {
                    if (flow.systemId.equals(sys.id)) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", flow.id);
                        m.put("type", flow.type);
                        m.put("status", flow.workflowStatus != null ? flow.workflowStatus : flow.status.toString());
                        m.put("createdAt", flow.createdAt);
                        m.put("anomaly", flow.anomaly != null ? flow.anomaly.anomalyType : "Unknown");
                        m.put("stepCount", flow.steps.size());
                        if (flow.journey != null && flow.journey.containsKey("stages")) {
                            m.put("journey", flow.journey);
                        }
                        m.put("deploymentDependencies", flow.deploymentDependencies != null ? flow.deploymentDependencies : List.of());
                        flows.add(m);
                    }
                }
                sendJson(exchange, flows);
            });
            
            server.createContext("/api/flows/", exchange -> {
                String flowId = exchange.getRequestURI().getPath().replace("/api/flows/", "");
                for (var flow : dashboard.allFlows) {
                    if (flow.id.equals(flowId)) {
                        sendJson(exchange, flowToMap(flow));
                        return;
                    }
                }
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            });

            server.createContext("/api/flows/demo", exchange -> {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, 0);
                    exchange.close();
                    return;
                }
                SelfHealingDashboard.SystemMonitor sys = getSelectedSystemMonitor();
                if (sys == null) {
                    sendJson(exchange, Map.of("ok", false, "error", "No selected system"));
                    return;
                }

                SelfHealingDashboard.HealingFlow flow = new SelfHealingDashboard.HealingFlow(sys.id, "CODE_FIX");
                flow.anomaly = new SelfHealingDashboard.AnomalyDetails();
                flow.anomaly.anomalyType = "CODE_DEPENDENCY_BLOCK";
                flow.anomaly.severity = "HIGH";
                flow.anomaly.message = "Deployment is blocked until dependent PRs are merged";
                flow.anomaly.detectedAt = System.currentTimeMillis();

                SelfHealingDashboard.FlowStep s1 = new SelfHealingDashboard.FlowStep("SIGNAL", "Anomaly detected", "TelemetryMCP");
                s1.status = "COMPLETED";
                SelfHealingDashboard.FlowStep s2 = new SelfHealingDashboard.FlowStep("EXECUTION", "Patch generated and PR opened", "DevelopmentMCP");
                s2.status = "COMPLETED";
                SelfHealingDashboard.FlowStep s3 = new SelfHealingDashboard.FlowStep("DEPLOY", "Deployment queued", "DeploymentMCP");
                s3.status = "PENDING";
                flow.steps.add(s1);
                flow.steps.add(s2);
                flow.steps.add(s3);

                List<Map<String, Object>> deps = new ArrayList<>();
                deps.add(new HashMap<>(Map.of("id", "PR-2341", "title", "Core fix PR", "status", "APPROVED")));
                deps.add(new HashMap<>(Map.of("id", "PR-2342", "title", "DB migration PR", "status", "OPEN")));
                flow.deploymentDependencies = deps;
                flow.workflowStatus = "WAITING_DEPENDENCIES";

                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("key", "ANOMALY");
                anomaly.put("label", "Anomaly Detection");
                anomaly.put("status", "COMPLETED");
                anomaly.put("timestamp", flow.createdAt);
                anomaly.put("summary", "Anomaly detected");

                Map<String, Object> fix = new LinkedHashMap<>();
                fix.put("key", "FIX");
                fix.put("label", "Fix Applied");
                fix.put("status", "COMPLETED");
                fix.put("timestamp", flow.createdAt + 150);
                fix.put("summary", "Patch and PR prepared");

                Map<String, Object> deploy = new LinkedHashMap<>();
                deploy.put("key", "DEPLOYMENT");
                deploy.put("label", "Deployment");
                deploy.put("status", "WAITING_DEPENDENCIES");
                deploy.put("timestamp", null);
                deploy.put("summary", "Waiting for PR dependencies");
                deploy.put("waitingCount", 2);
                deploy.put("dependencies", deps);

                flow.journey = new LinkedHashMap<>();
                flow.journey.put("flowStatus", "WAITING_DEPENDENCIES");
                flow.journey.put("stages", List.of(anomaly, fix, deploy));

                dashboard.addHealingFlow(flow);
                sendJson(exchange, Map.of("ok", true, "flowId", flow.id));
            });

            server.createContext("/api/alarms/", exchange -> {
                String alarmId = exchange.getRequestURI().getPath().replace("/api/alarms/", "");
                for (var alarm : dashboard.alarms) {
                    if (alarm.id.equals(alarmId)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id",            alarm.id);
                        m.put("type",          alarm.alarmType);
                        m.put("severity",      alarm.severity);
                        m.put("message",       alarm.message);
                        m.put("createdAt",     alarm.createdAt);
                        m.put("resolved",      alarm.resolved);
                        m.put("relatedFlowId", alarm.relatedFlowId);
                        sendJson(exchange, m);
                        return;
                    }
                }
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            });
            
            server.createContext("/api/alarms", exchange -> {
                List<Map<String, Object>> alarmsList = new ArrayList<>();
                SelfHealingDashboard.SystemMonitor sys = getSelectedSystemMonitor();
                if (Boolean.getBoolean("uac.verbose.dashboard")) {
                    System.out.println("API: /alarms requested. Selected system: " + dashboard.selectedSystem + ", resolvedId=" + (sys != null ? sys.id : "null") + ", Total alarms: " + dashboard.alarms.size());
                }
                if (sys == null) {
                    sendJson(exchange, alarmsList);
                    return;
                }
                
                for (var alarm : dashboard.alarms) {
                    if (Boolean.getBoolean("uac.verbose.dashboard")) {
                        System.out.println("  Checking alarm: sysId=" + alarm.systemId + ", type=" + alarm.alarmType);
                    }
                    if (alarm.systemId.equals(sys.id)) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", alarm.id);
                        m.put("type", alarm.alarmType);
                        m.put("severity", alarm.severity);
                        m.put("message", alarm.message);
                        m.put("createdAt", alarm.createdAt);
                        m.put("resolved", alarm.resolved);
                        alarmsList.add(m);
                    }
                }
                System.out.println("  Returning " + alarmsList.size() + " alarms for system " + sys.id);
                sendJson(exchange, alarmsList);
            });
            
            server.setExecutor(null);
        } catch (Exception e) {
            System.out.println("Route setup error: " + e.getMessage());
        }
    }
    
    private static Map<String, Object> flowToMap(SelfHealingDashboard.HealingFlow flow) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", flow.id);
        m.put("type", flow.type);
        m.put("status", flow.workflowStatus != null ? flow.workflowStatus : (flow.status != null ? flow.status.toString() : "UNKNOWN"));
        m.put("createdAt", flow.createdAt);

        if (flow.anomaly != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("anomalyType", flow.anomaly.anomalyType);
            a.put("severity",    flow.anomaly.severity);
            a.put("message",     flow.anomaly.message);
            a.put("sourceFile",  flow.anomaly.sourceFile);
            a.put("lineNumber",  flow.anomaly.lineNumber);
            a.put("stackTrace",  flow.anomaly.stackTrace);
            m.put("anomaly", a);
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        for (var step : flow.steps) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("phase",     step.phase);
            s.put("action",    step.action);
            s.put("mcpServer", step.mcpServer);
            s.put("status",    step.status);
            s.put("timestamp", step.timestamp);
            s.put("details",   step.details != null ? new HashMap<>(step.details) : new HashMap<>());
            steps.add(s);
        }
        m.put("steps", steps);
        m.put("deploymentDependencies", flow.deploymentDependencies != null ? new ArrayList<>(flow.deploymentDependencies) : new ArrayList<>());
        if (flow.journey != null && !flow.journey.isEmpty()) {
            m.put("journey", flow.journey);
        } else {
            // Backfill for legacy flows that don't include explicit journey metadata.
            List<Map<String, Object>> deps = flow.deploymentDependencies != null ? flow.deploymentDependencies : List.of();
            int waiting = 0;
            for (Map<String, Object> dep : deps) {
                String status = String.valueOf(dep.getOrDefault("status", "OPEN"));
                if (!("MERGED".equals(status) || "DEPLOYED".equals(status) || "COMPLETED".equals(status))) waiting++;
            }
            Map<String, Object> anomaly = new LinkedHashMap<>();
            anomaly.put("key", "ANOMALY");
            anomaly.put("label", "Anomaly Detection");
            anomaly.put("status", "COMPLETED");
            anomaly.put("timestamp", flow.createdAt);
            anomaly.put("summary", "Anomaly detected");

            Map<String, Object> fix = new LinkedHashMap<>();
            fix.put("key", "FIX");
            fix.put("label", "Fix Applied");
            fix.put("status", "COMPLETED");
            fix.put("timestamp", flow.createdAt + 200);
            fix.put("summary", "Fix execution completed");

            Map<String, Object> deploy = new LinkedHashMap<>();
            deploy.put("key", "DEPLOYMENT");
            deploy.put("label", "Deployment");
            deploy.put("status", waiting > 0 ? "WAITING_DEPENDENCIES" : "COMPLETED");
            deploy.put("timestamp", waiting > 0 ? null : flow.createdAt + 400);
            deploy.put("summary", waiting > 0 ? "Waiting for PR dependencies" : "Deployment completed");
            deploy.put("waitingCount", waiting);
            deploy.put("dependencies", deps);

            Map<String, Object> journey = new LinkedHashMap<>();
            journey.put("flowStatus", waiting > 0 ? "WAITING_DEPENDENCIES" : "COMPLETED");
            journey.put("stages", List.of(anomaly, fix, deploy));
            m.put("journey", journey);
        }
        return m;
    }

    private static void sendJson(HttpExchange exchange, Object obj) throws IOException {
        String json = toJson(obj);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private static String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof String) {
            String s = ((String) o)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
            return "\"" + s + "\"";
        }
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            StringBuilder sb = new StringBuilder("{");
            for (var e : m.entrySet()) {
                sb.append("\"").append(e.getKey()).append("\":").append(toJson(e.getValue())).append(",");
            }
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            return sb.append("}").toString();
        }
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            StringBuilder sb = new StringBuilder("[");
            for (var item : l) sb.append(toJson(item)).append(",");
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            return sb.append("]").toString();
        }
        return o.toString();
    }
    
    private String generateHTML() {
        return """
<!DOCTYPE html>
<html><head><title>Self-Healing Dashboard</title><style>
*{margin:0;padding:0;box-sizing:border-box}body{font-family:system-ui;background:#0f1419;color:#e8eaed;line-height:1.6}
.container{display:grid;grid-template-columns:250px 1fr;min-height:100vh}
.sidebar{background:#1a1f26;border-right:1px solid #2d333b;padding:20px;overflow-y:auto}
.sidebar h2{font-size:11px;font-weight:600;text-transform:uppercase;color:#7d8590;margin:20px 0 10px;letter-spacing:.5px}
.system-item{padding:10px 12px;margin:5px 0;border-radius:6px;cursor:pointer;border-left:3px solid transparent;transition:all .2s}
.system-item:hover{background:#262d36;border-left-color:#0969da}
.system-item.active{background:#0969da;color:#fff}
.nav-item{padding:8px 12px;margin:3px 0;border-radius:6px;cursor:pointer;font-size:13px;transition:background .2s}
.nav-item:hover{background:#262d36}
.nav-item.active{background:#0969da;color:#fff}
.main{padding:20px;overflow-y:auto}
.header{margin-bottom:20px}.header h1{font-size:24px;margin-bottom:5px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:20px}
.card{background:#1a1f26;border:1px solid #2d333b;border-radius:8px;padding:12px;cursor:pointer;transition:border-color .2s}
.card:hover{border-color:#0969da}
.card-title{font-size:10px;font-weight:600;color:#7d8590;text-transform:uppercase;margin-bottom:6px}
.card-value{font-size:22px;font-weight:600;margin-bottom:5px}
.card-detail{font-size:11px;color:#7d8590}
.list{display:flex;flex-direction:column;gap:8px}
.list-item{background:#1a1f26;border:1px solid #2d333b;border-radius:6px;padding:10px;cursor:pointer;transition:all .2s}
.list-item:hover{border-color:#0969da;background:#161b22}
.list-title{font-size:12px;font-weight:500;margin-bottom:4px}
.list-meta{font-size:11px;color:#7d8590}
.badge{display:inline-block;padding:2px 6px;border-radius:3px;font-size:9px;font-weight:600;margin-left:6px}
.badge.code-fix{background:#0969da33;color:#0969da}
.badge.operational-fix{background:#d2992233;color:#d29922}
.badge.waiting{background:#f59e0b33;color:#f59e0b}
.modal{display:none;position:fixed;z-index:1000;left:0;top:0;width:100%;height:100%;background:rgba(0,0,0,.5);align-items:center;justify-content:center}
.modal.active{display:flex}
.modal-content{background:#1a1f26;border:1px solid #2d333b;border-radius:8px;padding:20px;width:90%;max-width:800px;max-height:80vh;overflow-y:auto;position:relative}
.modal-close{position:absolute;right:15px;top:15px;font-size:24px;cursor:pointer;color:#7d8590}
.journey-row{display:flex;gap:8px;margin-top:8px}
.journey-stage{flex:1;background:#0f1419;border:1px solid #2d333b;border-radius:6px;padding:8px}
.journey-stage.completed{border-color:#3fb950;background:#3fb95011}
.journey-stage.failed{border-color:#f85149;background:#f8514911}
.journey-stage.waiting{border-color:#f59e0b;background:#f59e0b11}
.journey-stage.pending{border-color:#7d8590;background:#0f1419}
</style></head><body>
<div class="container">
<div class="sidebar">
<h2>Systems</h2>
<div id="systems-list"></div>
<h2>Views</h2>
<div class="nav-item active" data-view="dashboard" onclick="showDashboard()">📊 Dashboard</div>
<div class="nav-item" data-view="flows" onclick="showFlows()">🔧 Fixes</div>
<div class="nav-item" data-view="alarms" onclick="showAlarms()">🚨 Alarms</div>
</div>
<div class="main">
<div class="header">
<h1>Self-Healing System</h1>
<p id="system-info">Select a system</p>
</div>
<div id="dashboard"><div class="grid" id="cards"></div>
<div style="margin-top:20px"><h3 style="margin-bottom:10px">Recent Fixes</h3><div class="list" id="recent-flows"></div></div>
<div style="margin-top:20px"><h3 style="margin-bottom:10px">Active Alarms</h3><div class="list" id="active-alarms"></div></div>
</div>
<div id="flows-view" style="display:none"><div style="margin-top:20px"><h3 style="margin-bottom:10px">All Fixes</h3><div class="list" id="all-flows"></div></div></div>
<div id="alarms-view" style="display:none"><div style="margin-top:20px"><h3 style="margin-bottom:10px">All Alarms</h3><div class="list" id="all-alarms"></div></div></div>
</div>
</div>
<div id="flowModal" class="modal"><div class="modal-content"><span class="modal-close" onclick="document.getElementById('flowModal').classList.remove('active')">&times;</span><div id="flow-details"></div></div></div>
<div id="alarmModal" class="modal"><div class="modal-content"><span class="modal-close" onclick="document.getElementById('alarmModal').classList.remove('active')">&times;</span><div id="alarm-details"></div></div></div>
<script>
let selectedSystem = null;
let flows = [];
let alarms = [];
let flowFilter = 'ALL'; // Filter state for flows
let alarmFilter = 'ALL'; // Filter state for alarms

function setActiveNav(view) {
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    const nav = document.querySelector(`.nav-item[data-view="${view}"]`);
    if (nav) {
        nav.classList.add('active');
    }
}

async function loadData() {
    try {
        const systems = await fetch('/api/systems').then(r => r.json());
        document.getElementById('systems-list').innerHTML = systems.map(s => `
            <div class="system-item ${s.id === selectedSystem ? 'active' : ''}" onclick="selectSystem('${s.id}', '${s.name}', this)">
                <strong>${s.name}</strong><div style="font-size:11px;color:#7d8590">Health: ${(s.healthScore*100).toFixed(0)}% | Alarms: ${s.alarmCount}</div>
            </div>
        `).join('');
        
        if (!selectedSystem && systems.length > 0) {
            console.log('Auto-selecting first system:', systems[0].id);
            await selectSystem(systems[0].id, systems[0].name);
        }
    } catch(e) { console.error('Load systems error:', e); }
}

async function selectSystem(id, name, element = null) {
    console.log('Selecting system:', id, name);
    selectedSystem = id;  // Use UUID as selectedSystem, not name
    
    // Update active system in sidebar
    document.querySelectorAll('.system-item').forEach(el => el.classList.remove('active'));
    if (element) {
        element.classList.add('active');
    }
    
    await fetch('/api/systems/select', {method: 'POST', body: JSON.stringify({systemId: id, systemName: name})});
    
    // Reload all data for this system
    flows = await fetch('/api/flows').then(r => r.json()).catch(e => { console.error('Flows error:', e); return []; });
    alarms = await fetch('/api/alarms').then(r => r.json()).catch(e => { console.error('Alarms error:', e); return []; });
    
    console.log('Loaded flows:', flows.length, 'alarms:', alarms.length);
    await updateDashboard();
}

async function updateDashboard() {
    try {
        const data = await fetch('/api/system/current').then(r => r.json());
        console.log('System data:', data);
        
        document.getElementById('system-info').innerHTML = `${data.name || 'Unknown'} | Health: ${((data.healthScore || 0)*100).toFixed(0)}% | Fixes: ${data.totalFixes || 0} | Alarms: ${alarms.length}`;
        
        // Update stats
        const stats = {
            health: ((data.healthScore || 0)*100),
            anomalies: data.totalAnomalies || flows.length || 0,
            fixes: data.totalFixes || flows.length || 0,
            codeFixes: data.codeFixes || flows.filter(f => f.type === 'CODE_FIX').length || 0,
            opsFixes: data.operationalFixes || flows.filter(f => f.type === 'OPERATIONAL_FIX').length || 0,
            success: data.totalFixes > 0 ? ((data.successfulFixes/data.totalFixes)*100).toFixed(0) : 0
        };
        
        document.getElementById('cards').innerHTML = `
            <div class="card" onclick="setFlowFilter('ALL')" id="card-all" style="cursor:pointer;border-color:#0969da;background:#0969da11"><div class="card-title">All Fixes</div><div class="card-value">${stats.fixes}</div></div>
            <div class="card" onclick="setFlowFilter('CODE')" id="card-code" style="cursor:pointer"><div class="card-title">Code Fixes</div><div class="card-value">${stats.codeFixes}</div></div>
            <div class="card" onclick="setFlowFilter('OPERATIONAL')" id="card-ops" style="cursor:pointer"><div class="card-title">Ops Fixes</div><div class="card-value">${stats.opsFixes}</div></div>
            <div class="card"><div class="card-title">Health</div><div class="card-value" style="color:${stats.health>70?'#3fb950':stats.health>40?'#d29922':'#f85149'}">${stats.health.toFixed(0)}%</div></div>
            <div class="card"><div class="card-title">Anomalies</div><div class="card-value">${stats.anomalies}</div></div>
            <div class="card"><div class="card-title">Success Rate</div><div class="card-value">${stats.success}%</div></div>
        `;
        updateCardHighlight();
        
        // Update views
        updateFlowsView();
        updateAlarmsView();
        
    } catch(e) { console.error('Dashboard update error:', e); }
}

function updateFlowsView() {
    const filtered = getFilteredFlows();

    const journeyStrip = (flow) => {
        const stages = flow && flow.journey && Array.isArray(flow.journey.stages) ? flow.journey.stages : [];
        if (!stages.length) return '';
        return `<div class="journey-row">${stages.map(s => {
            const st = (s.status || 'PENDING').toUpperCase();
            const cls = st === 'COMPLETED' ? 'completed' : (st === 'FAILED' ? 'failed' : (st === 'WAITING_DEPENDENCIES' ? 'waiting' : 'pending'));
            const icon = st === 'COMPLETED' ? '✓' : (st === 'FAILED' ? '✗' : (st === 'WAITING_DEPENDENCIES' ? '⏳' : '○'));
            const waiting = st === 'WAITING_DEPENDENCIES' && (s.waitingCount || 0) > 0 ? ` (${s.waitingCount} PR)` : '';
            return `<div class="journey-stage ${cls}"><div style="font-size:10px;color:#7d8590">${s.label || s.key}</div><div style="font-size:11px">${icon} ${st}${waiting}</div></div>`;
        }).join('')}</div>`;
    };

    const html = filtered.map(f => `
        <div class="list-item" onclick="showFlow('${f.id}')">
            <div class="list-title">${f.id.substring(0,12)}... <span class="badge ${f.type.toLowerCase().replace('_','-')}">${f.type}</span></div>
            <div class="list-meta">${f.anomaly || 'Unknown'} | ${f.stepCount || 0} steps | ${f.status}${f.status==='WAITING_DEPENDENCIES' ? ' <span class="badge waiting">WAITING</span>' : ''}</div>
            ${journeyStrip(f)}
        </div>
    `).join('') || '<p style="color:#7d8590">No fixes matching filter</p>';
    
    document.getElementById('recent-flows').innerHTML = filtered.slice(0, 3).map(f => `
        <div class="list-item" onclick="showFlow('${f.id}')">
            <div class="list-title">${f.id.substring(0,12)}... <span class="badge ${f.type.toLowerCase().replace('_','-')}">${f.type}</span></div>
            <div class="list-meta">${f.anomaly || 'Unknown'} | ${f.stepCount || 0} steps | ${f.status}${f.status==='WAITING_DEPENDENCIES' ? ' <span class="badge waiting">WAITING</span>' : ''}</div>
            ${journeyStrip(f)}
        </div>
    `).join('') || '<p style="color:#7d8590">No fixes</p>';
    
    document.getElementById('all-flows').innerHTML = html;
}

function updateAlarmsView() {
    const html = (alarms || []).map(a => `
        <div class="list-item" onclick="showAlarm('${a.id}')">
            <div class="list-title">${a.type} <span class="badge" style="background:#${a.severity==='CRITICAL'?'f85149':a.severity==='HIGH'?'d29922':a.severity==='LOW'?'3fb950':'0969da'}33;color:#${a.severity==='CRITICAL'?'f85149':a.severity==='HIGH'?'d29922':a.severity==='LOW'?'3fb950':'0969da'}">${a.severity}</span></div>
            <div class="list-meta">${a.message || 'No message'}</div>
        </div>
    `).join('') || '<p style="color:#7d8590">No alarms</p>';

    document.getElementById('active-alarms').innerHTML = (alarms || []).slice(0, 3).map(a => `
        <div class="list-item" onclick="showAlarm('${a.id}')">
            <div class="list-title">${a.type} <span class="badge" style="background:#${a.severity==='CRITICAL'?'f85149':a.severity==='HIGH'?'d29922':a.severity==='LOW'?'3fb950':'0969da'}33;color:#${a.severity==='CRITICAL'?'f85149':a.severity==='HIGH'?'d29922':a.severity==='LOW'?'3fb950':'0969da'}">${a.severity}</span></div>
            <div class="list-meta">${(a.message || '').substring(0, 90)}</div>
        </div>
    `).join('') || '<p style="color:#7d8590">No alarms</p>';

    document.getElementById('all-alarms').innerHTML = html;
}

function updateCardHighlight() {
    document.querySelectorAll('[id^="card-"]').forEach(c => {
        c.style.borderColor = '#2d333b';
        c.style.background = '#1a1f26';
    });
    if (flowFilter === 'ALL') {
        document.getElementById('card-all').style.borderColor = '#0969da';
        document.getElementById('card-all').style.background = '#0969da11';
    } else if (flowFilter === 'CODE') {
        document.getElementById('card-code').style.borderColor = '#0969da';
        document.getElementById('card-code').style.background = '#0969da11';
    } else if (flowFilter === 'OPERATIONAL') {
        document.getElementById('card-ops').style.borderColor = '#0969da';
        document.getElementById('card-ops').style.background = '#0969da11';
    }
}

function setFlowFilter(filter) {
    flowFilter = filter;
    updateCardHighlight();
    updateFlowsView();
}

function getFilteredFlows() {
    if (flowFilter === 'ALL') return flows || [];
    if (flowFilter === 'CODE') return (flows || []).filter(f => f.type === 'CODE_FIX');
    if (flowFilter === 'OPERATIONAL') return (flows || []).filter(f => f.type === 'OPERATIONAL_FIX');
    return flows || [];
}

async function showFlow(flowId) {
    try {
        const flow = await fetch('/api/flows/' + flowId).then(r => r.json());
        const sevColor = s => s==='CRITICAL'?'f85149':s==='HIGH'?'d29922':s==='MEDIUM'?'e3b341':'3fb950';
        const steps = (flow.steps || []).map(s => {
            const detailRows = s.details ? Object.entries(s.details).map(([k,v]) => {
                const isUrl = typeof v === 'string' && (v.startsWith('https://') || v.startsWith('http://'));
                const rendered = isUrl
                    ? `<a href="${v}" target="_blank" rel="noopener noreferrer" style="color:#58a6ff;text-decoration:underline;word-break:break-all">${v}</a>`
                    : `<span style="color:#e8eaed;word-break:break-all">${v}</span>`;
                return `<div style="display:flex;gap:8px"><span style="color:#7d8590;min-width:90px">${k}:</span>${rendered}</div>`;
            }).join('') : '';
            const phaseColor = s.phase === 'DEPLOY' ? '#a371f7'
                             : s.phase === 'EXECUTION' ? '#f78166'
                             : '#0969da';
            return `
            <div style="background:#0f1419;border-left:3px solid ${phaseColor};border-radius:4px;padding:10px;margin:8px 0">
                <div style="display:flex;justify-content:space-between;margin-bottom:5px">
                    <span style="color:${phaseColor};font-weight:700;font-size:11px;letter-spacing:.5px">${s.phase}</span>
                    <span style="font-size:10px;color:#${s.status==='COMPLETED'?'3fb950':s.status==='FAILED'?'f85149':'d29922'}">${s.status||''}</span>
                </div>
                <div style="font-weight:500;margin-bottom:4px">${s.action}</div>
                <div style="font-size:11px;color:#7d8590;margin-bottom:${detailRows?'6':'0'}px">MCP: ${s.mcpServer}</div>
                ${detailRows ? `<div style="background:#161b22;border-radius:4px;padding:7px;font-size:11px;display:flex;flex-direction:column;gap:3px">${detailRows}</div>` : ''}
            </div>`;
        }).join('');

        const anomalyHtml = flow.anomaly ? `
        <div style="background:#0f1419;border-radius:6px;padding:12px;margin-bottom:14px">
            <div style="font-size:10px;color:#7d8590;margin-bottom:8px;font-weight:600;letter-spacing:.5px">ANOMALY DETAILS</div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;font-size:12px;margin-bottom:8px">
                <div><span style="color:#7d8590">Type:</span> <strong>${flow.anomaly.anomalyType||'N/A'}</strong></div>
                <div><span style="color:#7d8590">Severity:</span> <strong style="color:#${sevColor(flow.anomaly.severity||'')}">${flow.anomaly.severity||'N/A'}</strong></div>
                ${flow.anomaly.sourceFile ? `<div><span style="color:#7d8590">File:</span> ${flow.anomaly.sourceFile}</div>` : ''}
                ${flow.anomaly.lineNumber ? `<div><span style="color:#7d8590">Line:</span> ${flow.anomaly.lineNumber}</div>` : ''}
            </div>
            ${flow.anomaly.message ? `<div style="padding:8px;background:#161b22;border-radius:4px;font-size:12px;margin-bottom:6px">${flow.anomaly.message}</div>` : ''}
            ${flow.anomaly.stackTrace ? `<pre style="padding:8px;background:#161b22;border-radius:4px;font-size:10px;white-space:pre-wrap;max-height:110px;overflow-y:auto;margin:0">${flow.anomaly.stackTrace}</pre>` : ''}
        </div>` : '';

        const journeyStages = flow.journey && Array.isArray(flow.journey.stages) ? flow.journey.stages : [];
        const journeyHtml = journeyStages.length ? `<div style="margin-bottom:14px"><strong>Flow Journey (Anomaly -> Fix -> Deployment)</strong><div class="journey-row">${journeyStages.map(s => {
            const st = (s.status || 'PENDING').toUpperCase();
            const cls = st === 'COMPLETED' ? 'completed' : (st === 'FAILED' ? 'failed' : (st === 'WAITING_DEPENDENCIES' ? 'waiting' : 'pending'));
            const icon = st === 'COMPLETED' ? '✓' : (st === 'FAILED' ? '✗' : (st === 'WAITING_DEPENDENCIES' ? '⏳' : '○'));
            const deps = Array.isArray(s.dependencies) ? s.dependencies : [];
            const depsText = st === 'WAITING_DEPENDENCIES' && deps.length ? `<div style="margin-top:4px;color:#f59e0b;font-size:10px">${deps.map(d => `${d.id||'PR'}:${d.status||'UNKNOWN'}`).join(', ')}</div>` : '';
            return `<div class="journey-stage ${cls}"><div style="font-size:10px;color:#7d8590">${s.label || s.key}</div><div style="font-size:11px">${icon} ${st}</div>${depsText}</div>`;
        }).join('')}</div></div>` : '';

        const typeBadge = (flow.type||'').toLowerCase().replaceAll('_','-');
        const date = new Date(flow.createdAt||0).toLocaleString();
        document.getElementById('flow-details').innerHTML = `
            <h2 style="margin-bottom:14px">🔧 Fix Details</h2>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:14px">
                <div style="background:#0f1419;border-radius:6px;padding:10px;grid-column:1/-1">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:3px">ID</div>
                    <div style="font-family:monospace;font-size:11px">${flow.id}</div>
                </div>
                <div style="background:#0f1419;border-radius:6px;padding:10px">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:3px">TYPE</div>
                    <div><span class="badge ${typeBadge}">${flow.type}</span></div>
                </div>
                <div style="background:#0f1419;border-radius:6px;padding:10px">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:3px">STATUS</div>
                    <div style="color:${flow.status==='WAITING_DEPENDENCIES'?'#f59e0b':'#3fb950'}">${flow.status}</div>
                </div>
                <div style="background:#0f1419;border-radius:6px;padding:10px;grid-column:1/-1">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:3px">DETECTED</div>
                    <div>${date}</div>
                </div>
            </div>
            ${anomalyHtml}
            ${journeyHtml}
            <div><strong>Execution Steps (${(flow.steps||[]).length})</strong>${steps}</div>
        `;
        document.getElementById('flowModal').classList.add('active');
    } catch(e) { console.error('Flow modal error:', e); }
}

async function showAlarm(alarmId) {
    try {
        const a = await fetch('/api/alarms/' + alarmId).then(r => r.json());
        const sevColor = a.severity==='CRITICAL'?'f85149':a.severity==='HIGH'?'d29922':a.severity==='LOW'?'3fb950':'0969da';
        const date = new Date(a.createdAt||0).toLocaleString();
        document.getElementById('alarm-details').innerHTML = `
            <h2 style="margin-bottom:14px">🚨 Alarm Details</h2>
            <div style="display:flex;flex-direction:column;gap:10px">
                <div style="background:#0f1419;border-radius:6px;padding:12px">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:4px">TYPE</div>
                    <div style="font-weight:600;font-size:14px">${a.type}</div>
                </div>
                <div style="background:#0f1419;border-radius:6px;padding:12px">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:4px">SEVERITY</div>
                    <div style="font-weight:700;color:#${sevColor};font-size:14px">${a.severity}</div>
                </div>
                <div style="background:#0f1419;border-radius:6px;padding:12px">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:4px">MESSAGE</div>
                    <div style="line-height:1.5">${a.message||'No message'}</div>
                </div>
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
                    <div style="background:#0f1419;border-radius:6px;padding:12px">
                        <div style="font-size:10px;color:#7d8590;margin-bottom:4px">DETECTED</div>
                        <div style="font-size:12px">${date}</div>
                    </div>
                    <div style="background:#0f1419;border-radius:6px;padding:12px">
                        <div style="font-size:10px;color:#7d8590;margin-bottom:4px">STATUS</div>
                        <div style="color:${a.resolved?'#3fb950':'#f85149'}">${a.resolved?'✅ Resolved':'🔴 Active'}</div>
                    </div>
                </div>
                ${a.relatedFlowId ? `
                <div style="background:#0f1419;border-radius:6px;padding:12px">
                    <div style="font-size:10px;color:#7d8590;margin-bottom:4px">RELATED FIX</div>
                    <div style="color:#0969da;cursor:pointer;font-family:monospace" onclick="document.getElementById('alarmModal').classList.remove('active');showFlow('${a.relatedFlowId}')">${a.relatedFlowId}</div>
                </div>` : ''}
            </div>
        `;
        document.getElementById('alarmModal').classList.add('active');
    } catch(e) { console.error('Alarm modal error:', e); }
}

function showDashboard() {
    document.getElementById('dashboard').style.display='block';
    document.getElementById('flows-view').style.display='none';
    document.getElementById('alarms-view').style.display='none';
    setActiveNav('dashboard');
}

function showFlows() {
    document.getElementById('dashboard').style.display='none';
    document.getElementById('flows-view').style.display='block';
    document.getElementById('alarms-view').style.display='none';
    setActiveNav('flows');
}

function showAlarms() {
    document.getElementById('dashboard').style.display='none';
    document.getElementById('flows-view').style.display='none';
    document.getElementById('alarms-view').style.display='block';
    setActiveNav('alarms');
}

loadData();
setInterval(loadData, 3000);
</script>
</body></html>
""";
    }
    
    public void start() {
        server.start();
        System.out.println("\n╔════════════════════════════════════════════════╗");
        System.out.println("║  SELF-HEALING DASHBOARD                    ║");
        System.out.println("║  http://localhost:" + port + "                         ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
    }
    
    public void stop() {
        server.stop(0);
    }
}

