package com.blacklight.uac.domain.metrics;

import java.util.List;

/**
 * MetricsAggregator - Domain Service Interface
 * Aggregates and analyzes system metrics
 */
public interface MetricsAggregator {
    
    /**
     * Calculate health score from raw metrics
     */
    HealthScore calculateHealthScore(MetricsContext context);
    
    /**
     * Get health score trend
     */
    List<HealthScore> getHealthTrend(long fromTimestamp, long toTimestamp);
    
    /**
     * Analyze metric anomalies
     */
    List<MetricAnomaly> analyzeMetrics(MetricsContext context);
    
    /**
     * Get current system metrics
     */
    SystemMetrics getCurrentMetrics();
    
    /**
     * Predict health degradation
     */
    HealthPrediction predictHealthTrend(int forecastMinutes);
}

