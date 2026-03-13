package com.blacklight.uac.application.usecase;

import com.blacklight.uac.domain.anomaly.*;
import com.blacklight.uac.domain.metrics.HealthScore;
import java.util.List;

/**
 * AnomalyDetectionUseCase - Application Service
 * Use case for detecting system anomalies
 * 
 * Implements: Executor Pattern, Facade Pattern
 */
public class AnomalyDetectionUseCase {
    private final AnomalyDetectionService anomalyDetectionService;
    
    public AnomalyDetectionUseCase(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }
    
    /**
     * Execute anomaly detection
     */
    public AnomalyDetectionResult execute(AnomalyDetectionRequest request) {
        // Build detection context
        AnomalyDetectionContext context = new AnomalyDetectionContext.Builder()
            .withMetrics(request.getMetrics())
            .withLogs(request.getLogs())
            .withCodeChanges(request.getCodeChanges())
            .build();
        
        // Detect anomalies
        List<Anomaly> detectedAnomalies = anomalyDetectionService.detectAnomalies(context);
        
        // Diagnose each anomaly
        List<AnomalyDiagnosis> diagnoses = detectedAnomalies.stream()
            .map(anomalyDetectionService::diagnoseAnomaly)
            .toList();
        
        return new AnomalyDetectionResult(detectedAnomalies, diagnoses, 
            !detectedAnomalies.isEmpty());
    }
    
    /**
     * Get unresolved anomalies
     */
    public List<Anomaly> getUnresolvedAnomalies() {
        return anomalyDetectionService.findUnresolved();
    }
    
    /**
     * Get anomalies by severity
     */
    public List<Anomaly> getAnomaliesBySeverity(AnomalySeverity severity) {
        return anomalyDetectionService.findAnomaliesBySeverity(severity);
    }
}

