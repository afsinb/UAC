package com.blacklight.uac.observer;

import java.util.*;

public class Observer {
    private double healthScore = 0.0;
    private List<Metric> metricsBuffer = new ArrayList<>();
    private List<LogEntry> logsBuffer = new ArrayList<>();
    private List<DNAChange> dnaBuffer = new ArrayList<>();
    
    
    public double mapToHealthScore(List<Metric> metrics, List<LogEntry> logs, List<DNAChange> dnaChanges) {
        // Calculate health score based on multiple factors
        double score = 0.0;
        double totalWeight = 0.0;
        
        // Metric-based scoring (40% weight when all factors present)
        if (!metrics.isEmpty()) {
            double metricScore = calculateMetricScore(metrics);
            score += metricScore * 0.4;
            totalWeight += 0.4;
        }
        
        // Log-based scoring (35% weight when all factors present)
        if (!logs.isEmpty()) {
            double logScore = calculateLogScore(logs);
            score += logScore * 0.35;
            totalWeight += 0.35;
        }
        
        // DNA change scoring (25% weight when all factors present)
        if (!dnaChanges.isEmpty()) {
            double dnaScore = calculateDNAScore(dnaChanges);
            score += dnaScore * 0.25;
            totalWeight += 0.25;
        }
        
        // Normalize by actual weight used
        if (totalWeight > 0.0) {
            score = score / totalWeight;
        }
        
        return Math.min(1.0, Math.max(0.0, score));
    }
    
    private double calculateMetricScore(List<Metric> metrics) {
        // Implementation for calculating health based on metrics
        double total = 0.0;
        int count = 0;
        
        for (Metric metric : metrics) {
            if (metric.getName().contains("error")) {
                total += Math.max(0.0, 1.0 - (metric.getValue() / 100.0));
            } else if (metric.getName().contains("cpu")) {
                total += Math.max(0.0, 1.0 - (metric.getValue() / 80.0));
            } else if (metric.getName().contains("memory")) {
                total += Math.max(0.0, 1.0 - (metric.getValue() / 70.0));
            } else {
                total += metric.getValue() / 100.0;
            }
            count++;
        }
        
        return count > 0 ? total / count : 0.0;
    }
    
    private double calculateLogScore(List<LogEntry> logs) {
        // Implementation for calculating health based on logs
        int errorCount = 0;
        int warningCount = 0;
        int infoCount = 0;
        
        for (LogEntry log : logs) {
            switch (log.getLevel().toLowerCase()) {
                case "error":
                    errorCount++;
                    break;
                case "warning":
                    warningCount++;
                    break;
                case "info":
                    infoCount++;
                    break;
            }
        }
        
        // Calculate score based on log severity
        double score = 1.0 - (errorCount * 0.3 + warningCount * 0.1) / 
                      Math.max(1, errorCount + warningCount + infoCount);
        return Math.max(0.0, score);
    }
    
    private double calculateDNAScore(List<DNAChange> dnaChanges) {
        // Implementation for calculating health based on code changes
        int recentChanges = 0;
        int problematicChanges = 0;
        
        long timeWindow = System.currentTimeMillis() - (5 * 60 * 1000); // 5 minutes
        
        for (DNAChange change : dnaChanges) {
            if (change.getTimestamp() > timeWindow) {
                recentChanges++;
                if (change.getMessage().toLowerCase().contains("bug") || 
                    change.getMessage().toLowerCase().contains("fix")) {
                    problematicChanges++;
                }
            }
        }
        
        if (recentChanges == 0) return 1.0;
        
        double score = 1.0 - (problematicChanges * 0.5) / recentChanges;
        return Math.max(0.0, score);
    }
    
    public String generateStandardizedOutput(Exception e) {
        Map<String, Object> output = new HashMap<>();
        output.put("error_type", e.getClass().getSimpleName());
        output.put("message", e.getMessage());
        output.put("timestamp", System.currentTimeMillis());
        output.put("stack_trace", Arrays.toString(e.getStackTrace()));
        
        return toJson(output);
    }
    
    private String toJson(Map<String, Object> map) {
        // Simple JSON serialization
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                sb.append("\"").append(entry.getValue()).append("\"");
            } else {
                sb.append(entry.getValue());
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    // Getters for buffers
    public List<Metric> getMetricsBuffer() {
        return metricsBuffer;
    }
    
    public List<LogEntry> getLogsBuffer() {
        return logsBuffer;
    }
    
    public List<DNAChange> getDnaBuffer() {
        return dnaBuffer;
    }
}
