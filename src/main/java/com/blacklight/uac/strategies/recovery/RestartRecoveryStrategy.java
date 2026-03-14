package com.blacklight.uac.strategies.recovery;

import com.blacklight.uac.core.events.AnomalyDetectedEvent;

public class RestartRecoveryStrategy {

    public boolean canHandle(AnomalyDetectedEvent event) {
        if (event == null) return false;
        String t = event.getAnomalyType();
        return "LOG_ANOMALY".equals(t) || "CRASH_ANOMALY".equals(t) || "CONNECTIVITY_ANOMALY".equals(t);
    }

    public boolean execute(AnomalyDetectedEvent event) throws Exception {
        if (event == null) return false;
        Object svc = event.getAnomalyData() != null ? event.getAnomalyData().get("service") : null;
        System.out.println("[RestartRecoveryStrategy] Restarting: " + svc + " health=" + event.getHealthScore());
        return true;
    }

    public boolean isIdempotent() {
        return true;
    }

    public int getPriority() {
        return 5;
    }
}
