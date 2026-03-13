package com.blacklight.uac.domain.metrics;

/**
 * MetricAnomaly - Domain Value Object
 * Represents an anomaly in a specific metric
 */
public class MetricAnomaly {
    private final String metricName;
    private final double value;
    private final double threshold;
    private final double deviation;
    private final String severity;
    
    public MetricAnomaly(String metricName, double value, double threshold) {
        this.metricName = metricName;
        this.value = value;
        this.threshold = threshold;
        this.deviation = value - threshold;
        this.severity = determineSeverity(this.deviation, threshold);
    }
    
    private String determineSeverity(double deviation, double threshold) {
        double deviationPercent = (deviation / threshold) * 100;
        if (deviationPercent > 50) return "CRITICAL";
        if (deviationPercent > 25) return "HIGH";
        if (deviationPercent > 10) return "MEDIUM";
        return "LOW";
    }
    
    public String getMetricName() { return metricName; }
    public double getValue() { return value; }
    public double getThreshold() { return threshold; }
    public double getDeviation() { return deviation; }
    public String getSeverity() { return severity; }
    
    public boolean isAnomaly() {
        return deviation > 0;
    }
    
    public boolean isCritical() {
        return severity.equals("CRITICAL");
    }
    
    @Override
    public String toString() {
        return String.format("MetricAnomaly{%s: %.2f (threshold: %.2f), severity: %s}", 
            metricName, value, threshold, severity);
    }
}

