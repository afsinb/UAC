package com.blacklight.uac.core.coordinator;

import java.util.Map;
import java.util.HashMap;

/**
 * ContextSnapshot - Captures system context at a point in time
 * Used by HypothesisEngine to analyze anomalies
 */
public class ContextSnapshot {
    private final long timestamp;
    private final Map<String, Object> metrics;
    private final Map<String, Object> metadata;
    
    public ContextSnapshot() {
        this.timestamp = System.currentTimeMillis();
        this.metrics = new HashMap<>();
        this.metadata = new HashMap<>();
    }
    
    public ContextSnapshot(Map<String, Object> metrics, Map<String, Object> metadata) {
        this.timestamp = System.currentTimeMillis();
        this.metrics = metrics != null ? new HashMap<>(metrics) : new HashMap<>();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public Map<String, Object> getMetrics() {
        return metrics;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public Object getMetric(String key) {
        return metrics.get(key);
    }
    
    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }
    
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    @Override
    public String toString() {
        return String.format("ContextSnapshot{timestamp=%d, metrics=%d, metadata=%d}", 
            timestamp, metrics.size(), metadata.size());
    }
}

