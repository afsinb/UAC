package com.blacklight.uac.core.coordinator;

import java.util.HashMap;
import java.util.Map;

/**
 * Signal - Data carrier for inter-component communication
 * All communication between Observer, Brain, Resolver, and Evolver uses Signals
 */
public class Signal {
    private String source;
    private String type;
    private Map<String, Object> data;
    private long timestamp;
    
    // Standard signal types
    public static final String METRIC_UPDATE = "METRIC_UPDATE";
    public static final String ERROR_DETECTED = "ERROR_DETECTED";
    public static final String EVOLUTION_REQUEST = "EVOLUTION_REQUEST";
    public static final String HEALTH_THRESHOLD = "HEALTH_THRESHOLD";
    public static final String DNA_CHANGE = "DNA_CHANGE";
    
    public Signal(String source, String type) {
        this.source = source;
        this.type = type;
        this.data = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and setters
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return String.format("Signal{source='%s', type='%s', timestamp=%d}", 
            source, type, timestamp);
    }
}
