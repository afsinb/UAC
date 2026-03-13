package com.blacklight.uac.domain.anomaly;

import com.blacklight.uac.domain.common.DomainEntity;
import java.util.HashMap;
import java.util.Map;

/**
 * Anomaly - Domain Entity
 * Represents a detected anomaly in the system
 */
public class Anomaly extends DomainEntity {
    private final String source;
    private final AnomalyType type;
    private final double healthScore;
    private final AnomalySeverity severity;
    private final Map<String, Object> context;
    private final long detectedAt;
    private String rootCause;
    private String recommendation;
    private boolean resolved;
    
    public Anomaly(String source, AnomalyType type, double healthScore, Map<String, Object> context) {
        super();
        this.source = source;
        this.type = type;
        this.healthScore = Math.max(0.0, Math.min(1.0, healthScore));
        this.severity = AnomalySeverity.fromHealthScore(this.healthScore);
        this.context = new HashMap<>(context);
        this.detectedAt = System.currentTimeMillis();
        this.resolved = false;
    }
    
    public void resolveWith(String rootCause, String recommendation) {
        this.rootCause = rootCause;
        this.recommendation = recommendation;
        this.resolved = true;
        markUpdated();
    }
    
    // Getters
    public String getSource() {
        return source;
    }
    
    public AnomalyType getType() {
        return type;
    }
    
    public double getHealthScore() {
        return healthScore;
    }
    
    public AnomalySeverity getSeverity() {
        return severity;
    }
    
    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }
    
    public long getDetectedAt() {
        return detectedAt;
    }
    
    public String getRootCause() {
        return rootCause;
    }
    
    public String getRecommendation() {
        return recommendation;
    }
    
    public boolean isResolved() {
        return resolved;
    }
    
    public boolean isOperational() {
        return type.isOperational();
    }
    
    public boolean isLogical() {
        return type.isLogical();
    }
}

