package com.blacklight.uac.api.dto;

/**
 * AnomalyDto - Data Transfer Object for anomaly information
 */
public class AnomalyDto {
    private String id;
    private String type;
    private String severity;
    private String description;
    private double healthScore;
    private long detectedAt;
    private String status;
    
    public AnomalyDto() {
        this.detectedAt = System.currentTimeMillis();
    }
    
    public AnomalyDto(String id, String type, String severity, String description, double healthScore) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.healthScore = healthScore;
        this.detectedAt = System.currentTimeMillis();
        this.status = "DETECTED";
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public double getHealthScore() { return healthScore; }
    public void setHealthScore(double healthScore) { this.healthScore = healthScore; }
    
    public long getDetectedAt() { return detectedAt; }
    public void setDetectedAt(long detectedAt) { this.detectedAt = detectedAt; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    @Override
    public String toString() {
        return String.format("AnomalyDto{id='%s', type='%s', severity='%s', health=%.2f}", 
            id, type, severity, healthScore);
    }
}
