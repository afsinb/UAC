package com.blacklight.uac.domain.metrics;

/**
 * HealthPrediction - Domain Value Object
 * Predicts future health degradation
 */
public class HealthPrediction {
    private final HealthStatus predictedStatus;
    private final double predictedScore;
    private final int forecastMinutes;
    private final double confidence;
    private final String reason;
    
    public HealthPrediction(HealthStatus predictedStatus, double predictedScore, 
                           int forecastMinutes, double confidence, String reason) {
        this.predictedStatus = predictedStatus;
        this.predictedScore = Math.max(0.0, Math.min(1.0, predictedScore));
        this.forecastMinutes = forecastMinutes;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.reason = reason;
    }
    
    public HealthStatus getPredictedStatus() { return predictedStatus; }
    public double getPredictedScore() { return predictedScore; }
    public int getForecastMinutes() { return forecastMinutes; }
    public double getConfidence() { return confidence; }
    public String getReason() { return reason; }
    
    public boolean isDegradationExpected() {
        return predictedStatus != HealthStatus.HEALTHY;
    }
    
    public boolean isConfidentPrediction() {
        return confidence >= 0.7;
    }
    
    @Override
    public String toString() {
        return String.format("HealthPrediction{in %d minutes: %s (score: %.2f), confidence: %.0f%%}", 
            forecastMinutes, predictedStatus, predictedScore, confidence * 100);
    }
}

