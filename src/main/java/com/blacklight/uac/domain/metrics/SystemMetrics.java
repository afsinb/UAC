package com.blacklight.uac.domain.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * SystemMetrics - Domain Value Object
 * Represents current system metrics
 */
public class SystemMetrics {
    private final double cpuUsage;
    private final double memoryUsage;
    private final double diskUsage;
    private final int activeConnections;
    private final double errorRate;
    private final double avgResponseTime;
    private final long timestamp;
    private final Map<String, Object> customMetrics;
    
    private SystemMetrics(Builder builder) {
        this.cpuUsage = builder.cpuUsage;
        this.memoryUsage = builder.memoryUsage;
        this.diskUsage = builder.diskUsage;
        this.activeConnections = builder.activeConnections;
        this.errorRate = builder.errorRate;
        this.avgResponseTime = builder.avgResponseTime;
        this.timestamp = builder.timestamp;
        this.customMetrics = new HashMap<>(builder.customMetrics);
    }
    
    public double getCpuUsage() { return cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public double getDiskUsage() { return diskUsage; }
    public int getActiveConnections() { return activeConnections; }
    public double getErrorRate() { return errorRate; }
    public double getAvgResponseTime() { return avgResponseTime; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getCustomMetrics() { return new HashMap<>(customMetrics); }
    
    public boolean hasHighCpu() { return cpuUsage > 80.0; }
    public boolean hasHighMemory() { return memoryUsage > 80.0; }
    public boolean hasHighErrorRate() { return errorRate > 5.0; }
    public boolean hasSlowResponse() { return avgResponseTime > 1000.0; }
    
    public static class Builder {
        private double cpuUsage = 0.0;
        private double memoryUsage = 0.0;
        private double diskUsage = 0.0;
        private int activeConnections = 0;
        private double errorRate = 0.0;
        private double avgResponseTime = 0.0;
        private long timestamp = System.currentTimeMillis();
        private final Map<String, Object> customMetrics = new HashMap<>();
        
        public Builder cpuUsage(double cpu) { this.cpuUsage = cpu; return this; }
        public Builder memoryUsage(double memory) { this.memoryUsage = memory; return this; }
        public Builder diskUsage(double disk) { this.diskUsage = disk; return this; }
        public Builder activeConnections(int connections) { this.activeConnections = connections; return this; }
        public Builder errorRate(double rate) { this.errorRate = rate; return this; }
        public Builder avgResponseTime(double time) { this.avgResponseTime = time; return this; }
        public Builder timestamp(long ts) { this.timestamp = ts; return this; }
        public Builder addCustomMetric(String key, Object value) { 
            this.customMetrics.put(key, value); 
            return this; 
        }
        
        public SystemMetrics build() {
            return new SystemMetrics(this);
        }
    }
}

