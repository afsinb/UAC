package com.blacklight.uac.domain.metrics;

/**
 * HealthStatus - Domain Value Object
 * Defines the status of system health
 */
public enum HealthStatus {
    HEALTHY("System operating normally", 0.8, 1.0),
    DEGRADED("System degraded but operational", 0.5, 0.8),
    CRITICAL("System in critical state", 0.0, 0.5);
    
    private final String description;
    private final double minScore;
    private final double maxScore;
    
    HealthStatus(String description, double minScore, double maxScore) {
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }
    
    public static HealthStatus fromScore(double score) {
        if (score >= 0.8) return HEALTHY;
        if (score >= 0.5) return DEGRADED;
        return CRITICAL;
    }
    
    public String getDescription() {
        return description;
    }
    
    public double getMinScore() {
        return minScore;
    }
    
    public double getMaxScore() {
        return maxScore;
    }
    
    public boolean isHealthy() {
        return this == HEALTHY;
    }
    
    public boolean isCritical() {
        return this == CRITICAL;
    }
}

