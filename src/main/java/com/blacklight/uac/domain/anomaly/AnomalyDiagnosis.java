package com.blacklight.uac.domain.anomaly;

/**
 * AnomalyDiagnosis - Domain Value Object
 * Result of analyzing an anomaly
 */
public class AnomalyDiagnosis {
    private final String anomalyId;
    private final DiagnosisType type;
    private final String description;
    private final String suggestedAction;
    private final double confidence;
    
    public enum DiagnosisType {
        OPERATIONAL_ISSUE("Infrastructure problem requiring scale/restart"),
        LOGICAL_ISSUE("Code defect requiring patch"),
        UNKNOWN("Cannot determine issue type");
        
        private final String description;
        
        DiagnosisType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public AnomalyDiagnosis(String anomalyId, DiagnosisType type, 
                            String description, String suggestedAction, 
                            double confidence) {
        this.anomalyId = anomalyId;
        this.type = type;
        this.description = description;
        this.suggestedAction = suggestedAction;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    }
    
    public String getAnomalyId() {
        return anomalyId;
    }
    
    public DiagnosisType getType() {
        return type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getSuggestedAction() {
        return suggestedAction;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public boolean isOperational() {
        return type == DiagnosisType.OPERATIONAL_ISSUE;
    }
    
    public boolean isLogical() {
        return type == DiagnosisType.LOGICAL_ISSUE;
    }
    
    public boolean isConfident() {
        return confidence >= 0.7;
    }
}

