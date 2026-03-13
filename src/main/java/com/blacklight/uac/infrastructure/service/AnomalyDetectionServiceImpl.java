package com.blacklight.uac.infrastructure.service;

import com.blacklight.uac.domain.anomaly.*;
import com.blacklight.uac.domain.metrics.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * AnomalyDetectionServiceImpl - Domain Service Implementation
 * Implements anomaly detection logic
 */
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {
    private final MetricsAggregator metricsAggregator;
    private final Map<String, Anomaly> anomalyMap = new ConcurrentHashMap<>();
    
    public AnomalyDetectionServiceImpl(MetricsAggregator metricsAggregator) {
        this.metricsAggregator = metricsAggregator;
    }
    
    @Override
    public List<Anomaly> detectAnomalies(AnomalyDetectionContext context) {
        List<Anomaly> detectedAnomalies = new ArrayList<>();
        
        // Analyze metrics for anomalies
        if (context.hasMetrics()) {
            SystemMetrics metrics = new SystemMetrics.Builder()
                .cpuUsage((double) context.getMetrics().getOrDefault("cpu", 0.0))
                .memoryUsage((double) context.getMetrics().getOrDefault("memory", 0.0))
                .errorRate((double) context.getMetrics().getOrDefault("errorRate", 0.0))
                .build();
            
            detectedAnomalies.addAll(analyzeMetricsForAnomalies(metrics));
        }
        
        // Analyze logs for anomalies
        if (context.hasLogs()) {
            detectedAnomalies.addAll(analyzeLogsForAnomalies(context.getLogs()));
        }
        
        // Analyze code changes
        if (context.hasCodeChanges()) {
            detectedAnomalies.addAll(analyzeCodeChangesForAnomalies(context.getCodeChanges()));
        }
        
        // Store detected anomalies
        detectedAnomalies.forEach(a -> anomalyMap.put(a.getId(), a));
        
        return detectedAnomalies;
    }
    
    private List<Anomaly> analyzeMetricsForAnomalies(SystemMetrics metrics) {
        List<Anomaly> anomalies = new ArrayList<>();
        
        if (metrics.hasHighCpu()) {
            anomalies.add(new Anomaly(
                "system",
                AnomalyType.METRIC_THRESHOLD_EXCEEDED,
                0.3,
                Map.of("metric", "cpu", "value", metrics.getCpuUsage())
            ));
        }
        
        if (metrics.hasHighMemory()) {
            anomalies.add(new Anomaly(
                "system",
                AnomalyType.METRIC_THRESHOLD_EXCEEDED,
                0.35,
                Map.of("metric", "memory", "value", metrics.getMemoryUsage())
            ));
        }
        
        if (metrics.hasHighErrorRate()) {
            anomalies.add(new Anomaly(
                "application",
                AnomalyType.ERROR_RATE_SPIKE,
                0.4,
                Map.of("errorRate", metrics.getErrorRate())
            ));
        }
        
        return anomalies;
    }
    
    private List<Anomaly> analyzeLogsForAnomalies(Map<String, Object> logs) {
        List<Anomaly> anomalies = new ArrayList<>();
        
        if (logs.containsKey("exceptions") && logs.get("exceptions") instanceof Integer) {
            int exceptionCount = (Integer) logs.get("exceptions");
            if (exceptionCount > 10) {
                anomalies.add(new Anomaly(
                    "logs",
                    AnomalyType.ERROR_RATE_SPIKE,
                    0.45,
                    Map.of("exceptions", exceptionCount)
                ));
            }
        }
        
        return anomalies;
    }
    
    private List<Anomaly> analyzeCodeChangesForAnomalies(Map<String, Object> changes) {
        List<Anomaly> anomalies = new ArrayList<>();
        // Analyze code changes for potential issues
        return anomalies;
    }
    
    @Override
    public List<Anomaly> findAnomaliesBySeverity(AnomalySeverity severity) {
        return anomalyMap.values().stream()
            .filter(a -> a.getSeverity() == severity)
            .toList();
    }
    
    @Override
    public List<Anomaly> findUnresolved() {
        return anomalyMap.values().stream()
            .filter(a -> !a.isResolved())
            .toList();
    }
    
    @Override
    public Anomaly getAnomaly(String anomalyId) {
        return anomalyMap.get(anomalyId);
    }
    
    @Override
    public AnomalyDiagnosis diagnoseAnomaly(Anomaly anomaly) {
        AnomalyDiagnosis.DiagnosisType type = anomaly.isOperational() ?
            AnomalyDiagnosis.DiagnosisType.OPERATIONAL_ISSUE :
            AnomalyDiagnosis.DiagnosisType.LOGICAL_ISSUE;
        
        String action = anomaly.isOperational() ?
            "Scale resources or restart service" :
            "Apply code patch and redeploy";
        
        return new AnomalyDiagnosis(
            anomaly.getId(),
            type,
            anomaly.getType().getDescription(),
            action,
            0.85
        );
    }
    
    @Override
    public void resolveAnomaly(String anomalyId, String rootCause, String recommendation) {
        Anomaly anomaly = anomalyMap.get(anomalyId);
        if (anomaly != null) {
            anomaly.resolveWith(rootCause, recommendation);
        }
    }
}

