package com.blacklight.uac.core.coordinator;

import com.blacklight.uac.core.events.AnomalyDetectedEvent;

/**
 * Hypothesis engine for Phase 3 of the 5-phase loop.
 * Determines whether an issue is Operational or Logical.
 */
public class HypothesisEngine {
    
    /**
     * Analyze anomaly and context to determine issue type.
     */
    public IssueHypothesis analyze(AnomalyDetectedEvent event, ContextSnapshot context) {
        double healthScore = event.getHealthScore();
        String anomalyType = event.getAnomalyType();
        
        // Decision logic:
        // - Health < 0.5 with METRIC_ANOMALY or LOG_ANOMALY → Operational
        // - Health ≥ 0.5 with DNA_ANOMALY → Logical
        
        if (healthScore < 0.5) {
            return new IssueHypothesis(
                IssueHypothesis.IssueType.OPERATIONAL,
                Math.abs(healthScore - 0.5),
                "Low health score (" + healthScore + ") indicates infrastructure issue"
            );
        } else if ("DNA_ANOMALY".equals(anomalyType)) {
            return new IssueHypothesis(
                IssueHypothesis.IssueType.LOGICAL,
                Math.abs(healthScore - 0.75),
                "Recent code changes detected with health score " + healthScore
            );
        } else {
            // Default to operational for borderline cases
            return new IssueHypothesis(
                IssueHypothesis.IssueType.OPERATIONAL,
                0.5,
                "Inconclusive signal - treating as operational"
            );
        }
    }
}

