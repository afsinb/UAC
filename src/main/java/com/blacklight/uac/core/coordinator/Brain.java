package com.blacklight.uac.core.coordinator;

import com.blacklight.uac.evolver.DevelopmentTask;
import com.blacklight.uac.evolver.Evolver;
import com.blacklight.uac.observer.DNAChange;
import com.blacklight.uac.observer.Observer;
import com.blacklight.uac.resolver.RecoveryAction;
import com.blacklight.uac.resolver.Resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Brain - Signal processor and coordinator
 * Routes signals to appropriate recovery paths based on hypothesis
 * 
 * Implements the 5-phase loop:
 * SIGNAL → CONTEXT → HYPOTHESIS → EXECUTION → VALIDATION
 */
public class Brain {
    private final Observer observer;
    private final Resolver resolver;
    private final Evolver evolver;
    private final HypothesisEngine hypothesisEngine;
    
    public Brain(Observer observer, Resolver resolver, Evolver evolver) {
        this.observer = observer;
        this.resolver = resolver;
        this.evolver = evolver;
        this.hypothesisEngine = new HypothesisEngine();
    }
    
    /**
     * Process incoming signal through the 5-phase loop
     */
    public CompletableFuture<Boolean> processSignal(Signal signal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Processing signal: " + signal.getType());
                
                // 1. SIGNAL: Observer detects anomaly
                double healthScore = observer.mapToHealthScore(
                    observer.getMetricsBuffer(),
                    observer.getLogsBuffer(), 
                    observer.getDnaBuffer()
                );
                
                // 2. CONTEXT: Request last 5 minutes of DNA changes
                List<DNAChange> recentChanges = getRecentDNAChanges(5);
                
                // 3. HYPOTHESIS: Determine if issue is Operational or Logical
                String hypothesis = determineHypothesis(healthScore, signal.getType());
                
                // 4. EXECUTION: Apply fix or create patch
                if ("operational".equals(hypothesis)) {
                    executeRecovery(signal);
                } else {
                    executeDevelopmentFix(signal);
                }
                
                // 5. VALIDATION: Wait for system to stabilize
                return waitForStabilization();
                
            } catch (Exception e) {
                System.err.println("Signal processing failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get DNA changes from the last N minutes
     */
    private List<DNAChange> getRecentDNAChanges(int minutes) {
        long timeWindow = System.currentTimeMillis() - (minutes * 60L * 1000L);
        List<DNAChange> recentChanges = new ArrayList<>();
        
        for (DNAChange change : observer.getDnaBuffer()) {
            if (change.getTimestamp() > timeWindow) {
                recentChanges.add(change);
            }
        }
        
        return recentChanges;
    }
    
    /**
     * Determine hypothesis based on health score and signal type
     * - Health < 0.5 → Operational (infrastructure issue)
     * - Health >= 0.5 with DNA_ANOMALY → Logical (code defect)
     * - Default → Operational
     */
    private String determineHypothesis(double healthScore, String signalType) {
        if (healthScore < 0.5) {
            return "operational";
        } else if (Signal.DNA_CHANGE.equals(signalType) || "DNA_ANOMALY".equals(signalType)) {
            return "logical";
        } else {
            return "operational";
        }
    }
    
    /**
     * Execute infrastructure recovery action
     */
    private void executeRecovery(Signal signal) {
        System.out.println("Executing recovery action for signal: " + signal.getType());
        
        // Determine recovery action type based on signal
        RecoveryAction.ActionType actionType = RecoveryAction.ActionType.RESTART;
        String target = "application_service";
        
        if (signal.getData().containsKey("target")) {
            target = (String) signal.getData().get("target");
        }
        
        RecoveryAction action = new RecoveryAction(actionType, target);
        resolver.executeRecovery(action);
    }
    
    /**
     * Execute development fix through Evolver
     */
    private void executeDevelopmentFix(Signal signal) {
        System.out.println("Executing development fix for signal: " + signal.getType());
        
        DevelopmentTask task = new DevelopmentTask("clone");
        evolver.executeDevelopmentCycle(task);
    }
    
    /**
     * Wait for system to stabilize after action
     */
    private boolean waitForStabilization() {
        try {
            Thread.sleep(10000); // Wait 10 seconds for stabilization
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Shutdown the brain and its dependencies
     */
    public void shutdown() {
        if (resolver != null) {
            resolver.shutdown();
        }
    }
}
