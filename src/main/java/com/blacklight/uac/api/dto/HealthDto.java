package com.blacklight.uac.api.dto;

/**
 * HealthDto - Data Transfer Object for health status
 */
public class HealthDto {
    private double healthScore;
    private String status;
    private long timestamp;
    private String message;
    
    public HealthDto() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public HealthDto(double healthScore, String status, String message) {
        this.healthScore = healthScore;
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public double getHealthScore() { return healthScore; }
    public void setHealthScore(double healthScore) { this.healthScore = healthScore; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    @Override
    public String toString() {
        return String.format("HealthDto{score=%.2f, status='%s', message='%s'}", 
            healthScore, status, message);
    }
}
