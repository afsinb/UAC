package com.blacklight.uac.api.rest;

import com.blacklight.uac.api.dto.AnomalyDto;
import com.blacklight.uac.domain.anomaly.Anomaly;
import com.blacklight.uac.domain.anomaly.AnomalyDetectionService;
import com.blacklight.uac.domain.anomaly.AnomalyDetectionContext;
import com.blacklight.uac.domain.metrics.SystemMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AnomalyController - REST API for anomaly management
 * Provides endpoints for detecting and managing anomalies
 */
public class AnomalyController {
    private final AnomalyDetectionService detectionService;
    private final Map<String, AnomalyDto> anomalyHistory;
    
    public AnomalyController(AnomalyDetectionService detectionService) {
        this.detectionService = detectionService;
        this.anomalyHistory = new ConcurrentHashMap<>();
    }
    
    /**
     * GET /api/anomalies
     * Returns list of all detected anomalies
     */
    public List<AnomalyDto> getAllAnomalies() {
        return new ArrayList<>(anomalyHistory.values());
    }
    
    /**
     * GET /api/anomalies/{id}
     * Returns specific anomaly by ID
     */
    public AnomalyDto getAnomaly(String id) {
        return anomalyHistory.get(id);
    }
    
    /**
     * GET /api/anomalies/active
     * Returns list of active (unresolved) anomalies
     */
    public List<AnomalyDto> getActiveAnomalies() {
        return anomalyHistory.values().stream()
            .filter(a -> "DETECTED".equals(a.getStatus()) || "IN_PROGRESS".equals(a.getStatus()))
            .toList();
    }
    
    /**
     * POST /api/anomalies/detect
     * Triggers anomaly detection with current metrics
     */
    public AnomalyDto detectAnomaly(SystemMetrics metrics) {
        AnomalyDetectionContext context = new AnomalyDetectionContext.Builder()
            .withMetric("cpu", 0.0)
            .withMetric("memory", 0.0)
            .build();
        
        // Simulate anomaly detection
        String anomalyId = "ANO-" + System.currentTimeMillis();
        String type = "METRIC_ANOMALY";
        String severity = determineSeverity(metrics);
        String description = "Anomaly detected in system metrics";
        double healthScore = calculateHealthScore(metrics);
        
        AnomalyDto anomaly = new AnomalyDto(anomalyId, type, severity, description, healthScore);
        anomalyHistory.put(anomalyId, anomaly);
        
        return anomaly;
    }
    
    /**
     * PUT /api/anomalies/{id}/resolve
     * Marks an anomaly as resolved
     */
    public AnomalyDto resolveAnomaly(String id) {
        AnomalyDto anomaly = anomalyHistory.get(id);
        if (anomaly != null) {
            anomaly.setStatus("RESOLVED");
        }
        return anomaly;
    }
    
    private String determineSeverity(SystemMetrics metrics) {
        // Simplified severity determination
        return "MEDIUM";
    }
    
    private double calculateHealthScore(SystemMetrics metrics) {
        // Simplified health score calculation
        return 0.75;
    }
}
