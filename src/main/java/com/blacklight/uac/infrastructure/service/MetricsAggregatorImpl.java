package com.blacklight.uac.infrastructure.service;

import com.blacklight.uac.domain.metrics.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * MetricsAggregatorImpl - Metrics Aggregation Implementation
 * Calculates health scores and analyzes system metrics
 */
public class MetricsAggregatorImpl implements MetricsAggregator {
    private final Deque<HealthScore> healthHistory = new ConcurrentLinkedDeque<>();
    private final int maxHistorySize = 1000;
    private volatile SystemMetrics currentMetrics;
    
    @Override
    public HealthScore calculateHealthScore(MetricsContext context) {
        SystemMetrics metrics = context.getCurrentMetrics();
        
        // Calculate weighted health score
        double cpuScore = calculateCpuScore(metrics.getCpuUsage());
        double memoryScore = calculateMemoryScore(metrics.getMemoryUsage());
        double errorScore = calculateErrorScore(metrics.getErrorRate());
        double responseScore = calculateResponseScore(metrics.getAvgResponseTime());
        
        // Weighted average: CPU 25%, Memory 25%, Errors 30%, Response 20%
        double healthScore = (cpuScore * 0.25) + 
                            (memoryScore * 0.25) + 
                            (errorScore * 0.30) + 
                            (responseScore * 0.20);
        
        HealthScore score = new HealthScore(healthScore, "system");
        
        // Store in history
        healthHistory.addLast(score);
        if (healthHistory.size() > maxHistorySize) {
            healthHistory.removeFirst();
        }
        
        return score;
    }
    
    @Override
    public List<HealthScore> getHealthTrend(long fromTimestamp, long toTimestamp) {
        return healthHistory.stream()
            .filter(h -> h.getTimestamp() >= fromTimestamp && h.getTimestamp() <= toTimestamp)
            .toList();
    }
    
    @Override
    public List<MetricAnomaly> analyzeMetrics(MetricsContext context) {
        List<MetricAnomaly> anomalies = new ArrayList<>();
        SystemMetrics metrics = context.getCurrentMetrics();
        
        // Check CPU
        if (metrics.hasHighCpu()) {
            anomalies.add(new MetricAnomaly("cpu_usage", metrics.getCpuUsage(), 80.0));
        }
        
        // Check Memory
        if (metrics.hasHighMemory()) {
            anomalies.add(new MetricAnomaly("memory_usage", metrics.getMemoryUsage(), 80.0));
        }
        
        // Check Error Rate
        if (metrics.hasHighErrorRate()) {
            anomalies.add(new MetricAnomaly("error_rate", metrics.getErrorRate(), 5.0));
        }
        
        // Check Response Time
        if (metrics.hasSlowResponse()) {
            anomalies.add(new MetricAnomaly("response_time", metrics.getAvgResponseTime(), 1000.0));
        }
        
        return anomalies;
    }
    
    @Override
    public SystemMetrics getCurrentMetrics() {
        return currentMetrics != null ? currentMetrics : createDefaultMetrics();
    }
    
    @Override
    public HealthPrediction predictHealthTrend(int forecastMinutes) {
        if (healthHistory.size() < 10) {
            return new HealthPrediction(HealthStatus.HEALTHY, 0.8, 
                forecastMinutes, 0.5, "Insufficient data for prediction");
        }
        
        // Simple linear regression on recent scores
        List<HealthScore> recent = new ArrayList<>(healthHistory);
        int windowSize = Math.min(50, recent.size());
        List<HealthScore> window = recent.subList(recent.size() - windowSize, recent.size());
        
        double avgScore = window.stream()
            .mapToDouble(HealthScore::getScore)
            .average()
            .orElse(0.8);
        
        // Calculate trend
        double trend = calculateTrend(window);
        double predictedScore = Math.max(0.0, Math.min(1.0, 
            avgScore + (trend * forecastMinutes / 60.0)));
        
        HealthStatus predictedStatus = HealthStatus.fromScore(predictedScore);
        double confidence = Math.min(0.95, 0.5 + (windowSize / 100.0));
        
        String reason = trend < -0.01 ? "Declining health trend detected" :
                       trend > 0.01 ? "Improving health trend" :
                       "Stable health expected";
        
        return new HealthPrediction(predictedStatus, predictedScore, 
            forecastMinutes, confidence, reason);
    }
    
    public void updateMetrics(SystemMetrics metrics) {
        this.currentMetrics = metrics;
    }
    
    // Private helper methods
    
    private double calculateCpuScore(double cpuUsage) {
        if (cpuUsage < 50) return 1.0;
        if (cpuUsage < 70) return 0.8;
        if (cpuUsage < 85) return 0.5;
        if (cpuUsage < 95) return 0.2;
        return 0.1;
    }
    
    private double calculateMemoryScore(double memoryUsage) {
        if (memoryUsage < 60) return 1.0;
        if (memoryUsage < 75) return 0.8;
        if (memoryUsage < 85) return 0.5;
        if (memoryUsage < 95) return 0.2;
        return 0.1;
    }
    
    private double calculateErrorScore(double errorRate) {
        if (errorRate < 1.0) return 1.0;
        if (errorRate < 3.0) return 0.8;
        if (errorRate < 5.0) return 0.6;
        if (errorRate < 10.0) return 0.3;
        return 0.1;
    }
    
    private double calculateResponseScore(double responseTime) {
        if (responseTime < 200) return 1.0;
        if (responseTime < 500) return 0.8;
        if (responseTime < 1000) return 0.6;
        if (responseTime < 2000) return 0.3;
        return 0.1;
    }
    
    private double calculateTrend(List<HealthScore> scores) {
        if (scores.size() < 2) return 0.0;
        
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = scores.size();
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = scores.get(i).getScore();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }
    
    private SystemMetrics createDefaultMetrics() {
        return new SystemMetrics.Builder()
            .cpuUsage(0.0)
            .memoryUsage(0.0)
            .errorRate(0.0)
            .avgResponseTime(0.0)
            .build();
    }
}

