package com.blacklight.uac.application.usecase;

import com.blacklight.uac.domain.anomaly.Anomaly;
import com.blacklight.uac.domain.anomaly.AnomalyDiagnosis;
import java.util.List;

/**
 * AnomalyDetectionResult - Application DTO
 * Result of anomaly detection use case
 */
public class AnomalyDetectionResult {
    private final List<Anomaly> anomalies;
    private final List<AnomalyDiagnosis> diagnoses;
    private final boolean hasAnomalies;
    
    public AnomalyDetectionResult(List<Anomaly> anomalies, 
                                 List<AnomalyDiagnosis> diagnoses, 
                                 boolean hasAnomalies) {
        this.anomalies = anomalies;
        this.diagnoses = diagnoses;
        this.hasAnomalies = hasAnomalies;
    }
    
    public List<Anomaly> getAnomalies() { return anomalies; }
    public List<AnomalyDiagnosis> getDiagnoses() { return diagnoses; }
    public boolean hasAnomalies() { return hasAnomalies; }
    public int getAnomalyCount() { return anomalies.size(); }
}

