package com.blacklight.uac.mcp;

import com.blacklight.uac.observer.Observer;
import com.blacklight.uac.observer.Metric;
import com.blacklight.uac.observer.LogEntry;
import com.blacklight.uac.observer.DNAChange;

import java.util.*;

/**
 * ObserverMCPServer - Telemetry MCP Server
 * Wraps Observer as an MCP-compliant server for agnostic data ingestion
 */
public class ObserverMCPServer implements MCPServer {
    
    private final Observer observer;
    
    public ObserverMCPServer(Observer observer) {
        this.observer = observer;
    }
    
    @Override
    public String getName() {
        return "TelemetryMCP";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "map_to_health_score",
            "ingest_metrics",
            "ingest_logs",
            "ingest_dna",
            "get_health_score",
            "get_metrics_buffer",
            "get_logs_buffer",
            "get_dna_buffer",
            "generate_standardized_output"
        );
    }
    
    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "map_to_health_score" -> mapToHealthScore(request);
            case "ingest_metrics" -> ingestMetrics(request);
            case "ingest_logs" -> ingestLogs(request);
            case "ingest_dna" -> ingestDNA(request);
            case "get_health_score" -> getHealthScore(request);
            case "get_metrics_buffer" -> getMetricsBuffer(request);
            case "get_logs_buffer" -> getLogsBuffer(request);
            case "get_dna_buffer" -> getDNABuffer(request);
            case "generate_standardized_output" -> generateStandardizedOutput(request);
            default -> MCPResponse.error(request.getId(), "Unknown method: " + request.getMethod());
        };
    }
    
    @Override
    public boolean isHealthy() {
        return observer != null;
    }
    
    private MCPResponse mapToHealthScore(MCPRequest request) {
        double healthScore = observer.mapToHealthScore(
            observer.getMetricsBuffer(),
            observer.getLogsBuffer(),
            observer.getDnaBuffer()
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("healthScore", healthScore);
        result.put("status", getHealthStatus(healthScore));
        result.put("timestamp", System.currentTimeMillis());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse ingestMetrics(MCPRequest request) {
        List<Map<String, Object>> metricsData = (List<Map<String, Object>>) request.getParam("metrics");
        
        if (metricsData != null) {
            for (Map<String, Object> m : metricsData) {
                String name = (String) m.get("name");
                double value = ((Number) m.get("value")).doubleValue();
                observer.getMetricsBuffer().add(new Metric(name, value));
            }
        }
        
        return MCPResponse.success(request.getId(), Map.of("ingested", metricsData != null ? metricsData.size() : 0));
    }
    
    private MCPResponse ingestLogs(MCPRequest request) {
        List<Map<String, Object>> logsData = (List<Map<String, Object>>) request.getParam("logs");
        
        if (logsData != null) {
            for (Map<String, Object> l : logsData) {
                String level = (String) l.get("level");
                String message = (String) l.get("message");
                String source = (String) l.getOrDefault("source", "unknown");
                observer.getLogsBuffer().add(new LogEntry(level, message, source));
            }
        }
        
        return MCPResponse.success(request.getId(), Map.of("ingested", logsData != null ? logsData.size() : 0));
    }
    
    private MCPResponse ingestDNA(MCPRequest request) {
        List<Map<String, Object>> dnaData = (List<Map<String, Object>>) request.getParam("dna");
        
        if (dnaData != null) {
            for (Map<String, Object> d : dnaData) {
                String commitHash = (String) d.get("commitHash");
                String author = (String) d.get("author");
                String message = (String) d.get("message");
                observer.getDnaBuffer().add(new DNAChange(commitHash, author, message));
            }
        }
        
        return MCPResponse.success(request.getId(), Map.of("ingested", dnaData != null ? dnaData.size() : 0));
    }
    
    private MCPResponse getHealthScore(MCPRequest request) {
        double score = observer.mapToHealthScore(
            observer.getMetricsBuffer(),
            observer.getLogsBuffer(),
            observer.getDnaBuffer()
        );
        
        return MCPResponse.success(request.getId(), Map.of("healthScore", score));
    }
    
    private MCPResponse getMetricsBuffer(MCPRequest request) {
        return MCPResponse.success(request.getId(), Map.of(
            "size", observer.getMetricsBuffer().size(),
            "metrics", observer.getMetricsBuffer()
        ));
    }
    
    private MCPResponse getLogsBuffer(MCPRequest request) {
        return MCPResponse.success(request.getId(), Map.of(
            "size", observer.getLogsBuffer().size(),
            "logs", observer.getLogsBuffer()
        ));
    }
    
    private MCPResponse getDNABuffer(MCPRequest request) {
        return MCPResponse.success(request.getId(), Map.of(
            "size", observer.getDnaBuffer().size(),
            "dna", observer.getDnaBuffer()
        ));
    }
    
    private MCPResponse generateStandardizedOutput(MCPRequest request) {
        String error = request.getStringParam("error");
        String output = observer.generateStandardizedOutput(
            new Exception(error != null ? error : "Unknown error")
        );
        
        return MCPResponse.success(request.getId(), Map.of("output", output));
    }
    
    private String getHealthStatus(double score) {
        if (score >= 0.9) return "HEALTHY";
        if (score >= 0.7) return "STABLE";
        if (score >= 0.5) return "DEGRADED";
        if (score >= 0.3) return "UNHEALTHY";
        return "CRITICAL";
    }
}
