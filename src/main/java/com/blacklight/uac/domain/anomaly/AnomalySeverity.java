package com.blacklight.uac.domain.anomaly;

/**
 * Anomaly Severity - Domain Value Object
 * Defines severity levels of detected anomalies
 */
public enum AnomalySeverity {
    CRITICAL(0.0, 0.2, "Immediate action required"),
    HIGH(0.2, 0.4, "Action needed soon"),
    MEDIUM(0.4, 0.6, "Monitor closely"),
    LOW(0.6, 0.8, "Minor issue"),
    INFO(0.8, 1.0, "Informational");
    
    private final double minHealth;
    private final double maxHealth;
    private final String description;
    
    AnomalySeverity(double minHealth, double maxHealth, String description) {
        this.minHealth = minHealth;
        this.maxHealth = maxHealth;
        this.description = description;
    }
    
    public static AnomalySeverity fromHealthScore(double healthScore) {
        for (AnomalySeverity severity : values()) {
            if (healthScore >= severity.minHealth && healthScore <= severity.maxHealth) {
                return severity;
            }
        }
        return INFO;
    }
    
    public double getMinHealth() {
        return minHealth;
    }
    
    public double getMaxHealth() {
        return maxHealth;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean requiresImmediate() {
        return this == CRITICAL || this == HIGH;
    }
}

