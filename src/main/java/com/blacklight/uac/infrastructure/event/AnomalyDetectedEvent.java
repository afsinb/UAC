package com.blacklight.uac.infrastructure.event;

import com.blacklight.uac.domain.anomaly.Anomaly;

/**
 * AnomalyDetectedEvent - Domain Event
 * Published when anomaly is detected
 */
public class AnomalyDetectedEvent extends DomainEvent {
    private final String anomalyType;
    private final double healthScore;
    private final String severity;
    
    public AnomalyDetectedEvent(Anomaly anomaly) {
        super(anomaly.getId());
        this.anomalyType = anomaly.getType().toString();
        this.healthScore = anomaly.getHealthScore();
        this.severity = anomaly.getSeverity().toString();
    }
    
    public String getAnomalyType() { return anomalyType; }
    public double getHealthScore() { return healthScore; }
    public String getSeverity() { return severity; }
    
    @Override
    public String getEventType() {
        return "ANOMALY_DETECTED";
    }
}

