package com.blacklight.uac.application.usecase;

import java.util.Map;

/**
 * AnomalyDetectionRequest - Application DTO
 * Input for anomaly detection use case
 */
public class AnomalyDetectionRequest {
    private final Map<String, Object> metrics;
    private final Map<String, Object> logs;
    private final Map<String, Object> codeChanges;
    
    private AnomalyDetectionRequest(Builder builder) {
        this.metrics = builder.metrics;
        this.logs = builder.logs;
        this.codeChanges = builder.codeChanges;
    }
    
    public Map<String, Object> getMetrics() { return metrics; }
    public Map<String, Object> getLogs() { return logs; }
    public Map<String, Object> getCodeChanges() { return codeChanges; }
    
    public static class Builder {
        private Map<String, Object> metrics;
        private Map<String, Object> logs;
        private Map<String, Object> codeChanges;
        
        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder logs(Map<String, Object> logs) {
            this.logs = logs;
            return this;
        }
        
        public Builder codeChanges(Map<String, Object> changes) {
            this.codeChanges = changes;
            return this;
        }
        
        public AnomalyDetectionRequest build() {
            return new AnomalyDetectionRequest(this);
        }
    }
}

