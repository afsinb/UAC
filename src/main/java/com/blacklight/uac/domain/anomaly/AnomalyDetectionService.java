package com.blacklight.uac.domain.anomaly;

import java.util.List;

/**
 * AnomalyDetectionService - Domain Service
 * Responsible for detecting and managing anomalies
 * 
 * Follows: Repository Pattern, Factory Pattern
 */
public interface AnomalyDetectionService {
    
    /**
     * Detect anomalies based on system metrics
     */
    List<Anomaly> detectAnomalies(AnomalyDetectionContext context);
    
    /**
     * Find anomalies by criteria
     */
    List<Anomaly> findAnomaliesBySeverity(AnomalySeverity severity);
    
    /**
     * Find recent unresolved anomalies
     */
    List<Anomaly> findUnresolved();
    
    /**
     * Get anomaly by ID
     */
    Anomaly getAnomaly(String anomalyId);
    
    /**
     * Analyze anomaly and provide diagnosis
     */
    AnomalyDiagnosis diagnoseAnomaly(Anomaly anomaly);
    
    /**
     * Record resolution of anomaly
     */
    void resolveAnomaly(String anomalyId, String rootCause, String recommendation);
}

