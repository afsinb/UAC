package com.blacklight.uac.core.events;

/**
 * Represents an anomaly detected in system telemetry.
 * This event triggers the 5-phase UAC loop.
 */
public class AnomalyDetectedEvent extends UAEvent {
    private final double healthScore;
    private final String anomalyType;  // METRIC_ANOMALY, LOG_ANOMALY, DNA_ANOMALY
    private final java.util.Map<String, Object> anomalyData;
    
    public AnomalyDetectedEvent(String source, double healthScore, 
                                String anomalyType, java.util.Map<String, Object> data) {
        super(source, EventPriority.fromHealthScore(healthScore));
        this.healthScore = Math.max(0.0, Math.min(1.0, healthScore));
        this.anomalyType = anomalyType;
        this.anomalyData = new java.util.HashMap<>(data);
    }
    
    @Override
    public String getEventType() { return "ANOMALY_DETECTED"; }
    
    public double getHealthScore() { return healthScore; }
    public String getAnomalyType() { return anomalyType; }
    public java.util.Map<String, Object> getAnomalyData() { return anomalyData; }
}

