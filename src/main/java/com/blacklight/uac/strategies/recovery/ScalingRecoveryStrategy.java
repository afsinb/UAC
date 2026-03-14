package com.blacklight.uac.strategies.recovery;

import com.blacklight.uac.core.events.AnomalyDetectedEvent;

/**
 * ScalingRecoveryStrategy - Recovery strategy that scales up resources to address
 * high-load operational issues (high CPU, increased traffic, etc.).
 *
 * This is a thin adapter over the core scaling logic, exposing the
 * event-based API consumed by the strategy tests and the coordinator.
 */
public class ScalingRecoveryStrategy {

    /**
     * Returns true when the anomaly event warrants a scale-up response.
     * Metric anomalies (CPU/memory/throughput) are the primary trigger.
     */
    public boolean canHandle(AnomalyDetectedEvent event) {
        if (event == null) return false;
        String type = event.getAnomalyType();
        // Scaling is most relevant for metric-level resource pressure.
        return "METRIC_ANOMALY".equals(type) || "RESOURCE_ANOMALY".equals(type);
    }

    /**
     * Executes the scale-up action for the given anomaly event.
     *
     * @return true if the action was applied (or successfully simulated)
     */
    public boolean execute(AnomalyDetectedEvent event) throws Exception {
        if (event == null) return false;
        // Log the intended scale-up (real implementation would call k8s / Docker API).
        Object service = event.getAnomalyData() != null ? event.getAnomalyData().get("service") : null;
        System.out.println("[ScalingRecoveryStrategy] Scaling up resources for service: " + service
                + " | healthScore=" + event.getHealthScore());
        return true;
    }

    /**
     * Scaling up is idempotent: applying it twice only adds more headroom.
     */
    public boolean isIdempotent() {
        return true;
    }

    /**
     * Priority relative to other recovery strategies.
     * Scaling is preferred over restart when resources are the bottleneck
     * (higher number = higher priority).
     */
    public int getPriority() {
        return 8; // Higher than RestartRecoveryStrategy (5)
    }
}

