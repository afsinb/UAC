package com.blacklight.uac.api.rest;

import com.blacklight.uac.api.dto.HealthDto;
import com.blacklight.uac.observer.Observer;

/**
 * HealthController - REST API for health status
 * Provides endpoints for monitoring system health
 */
public class HealthController {
    private final Observer observer;
    
    public HealthController(Observer observer) {
        this.observer = observer;
    }
    
    /**
     * GET /api/health
     * Returns current system health status
     */
    public HealthDto getHealth() {
        double healthScore = observer.mapToHealthScore(
            observer.getMetricsBuffer(),
            observer.getLogsBuffer(),
            observer.getDnaBuffer()
        );
        
        String status = determineStatus(healthScore);
        String message = generateMessage(healthScore, status);
        
        return new HealthDto(healthScore, status, message);
    }
    
    /**
     * GET /api/health/score
     * Returns just the health score (0.0-1.0)
     */
    public double getHealthScore() {
        return observer.mapToHealthScore(
            observer.getMetricsBuffer(),
            observer.getLogsBuffer(),
            observer.getDnaBuffer()
        );
    }
    
    /**
     * GET /api/health/status
     * Returns health status string
     */
    public String getHealthStatus() {
        double score = getHealthScore();
        return determineStatus(score);
    }
    
    private String determineStatus(double healthScore) {
        if (healthScore >= 0.9) {
            return "HEALTHY";
        } else if (healthScore >= 0.7) {
            return "STABLE";
        } else if (healthScore >= 0.5) {
            return "DEGRADED";
        } else if (healthScore >= 0.3) {
            return "UNHEALTHY";
        } else {
            return "CRITICAL";
        }
    }
    
    private String generateMessage(double healthScore, String status) {
        return switch (status) {
            case "HEALTHY" -> "System operating normally";
            case "STABLE" -> "System stable with minor issues";
            case "DEGRADED" -> "System performance degraded";
            case "UNHEALTHY" -> "System unhealthy, recovery recommended";
            case "CRITICAL" -> "System critical, immediate action required";
            default -> "Unknown status";
        };
    }
}
