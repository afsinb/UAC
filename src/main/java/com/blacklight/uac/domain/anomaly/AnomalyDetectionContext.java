package com.blacklight.uac.domain.anomaly;

import java.util.HashMap;
import java.util.Map;

/**
 * AnomalyDetectionContext - Domain Value Object
 * Contains context information for anomaly detection
 */
public class AnomalyDetectionContext {
    private final Map<String, Object> metrics;
    private final Map<String, Object> logs;
    private final Map<String, Object> codeChanges;
    private final long timestamp;
    
    private AnomalyDetectionContext(Builder builder) {
        this.metrics = new HashMap<>(builder.metrics);
        this.logs = new HashMap<>(builder.logs);
        this.codeChanges = new HashMap<>(builder.codeChanges);
        this.timestamp = System.currentTimeMillis();
    }
    
    public Map<String, Object> getMetrics() {
        return new HashMap<>(metrics);
    }
    
    public Map<String, Object> getLogs() {
        return new HashMap<>(logs);
    }
    
    public Map<String, Object> getCodeChanges() {
        return new HashMap<>(codeChanges);
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean hasMetrics() {
        return !metrics.isEmpty();
    }
    
    public boolean hasLogs() {
        return !logs.isEmpty();
    }
    
    public boolean hasCodeChanges() {
        return !codeChanges.isEmpty();
    }
    
    // Builder Pattern
    public static class Builder {
        private final Map<String, Object> metrics = new HashMap<>();
        private final Map<String, Object> logs = new HashMap<>();
        private final Map<String, Object> codeChanges = new HashMap<>();
        
        public Builder withMetrics(Map<String, Object> metrics) {
            this.metrics.putAll(metrics);
            return this;
        }
        
        public Builder withMetric(String key, Object value) {
            this.metrics.put(key, value);
            return this;
        }
        
        public Builder withLogs(Map<String, Object> logs) {
            this.logs.putAll(logs);
            return this;
        }
        
        public Builder withLog(String key, Object value) {
            this.logs.put(key, value);
            return this;
        }
        
        public Builder withCodeChanges(Map<String, Object> changes) {
            this.codeChanges.putAll(changes);
            return this;
        }
        
        public Builder withCodeChange(String key, Object value) {
            this.codeChanges.put(key, value);
            return this;
        }
        
        public AnomalyDetectionContext build() {
            return new AnomalyDetectionContext(this);
        }
    }
}

