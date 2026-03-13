package com.blacklight.uac.monitoring;

import com.blacklight.uac.core.coordinator.Brain;
import com.blacklight.uac.core.coordinator.Signal;
import com.blacklight.uac.config.ApplicationConfig;
import com.blacklight.uac.monitoring.SystemMetricsCollector.SystemMetricsSnapshot;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LiveMonitor - Continuous system monitoring with autonomous healing
 * Collects real metrics and triggers UAC healing loop when thresholds are breached
 */
public class LiveMonitor {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final SystemMetricsCollector collector;
    private final Brain brain;
    private final ApplicationConfig config;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    
    // Metrics history
    private final Deque<SystemMetricsSnapshot> metricsHistory;
    private final int maxHistorySize;
    
    // Thresholds
    private final double criticalThreshold;
    private final double warningThreshold;
    
    // Statistics
    private long totalChecks;
    private long anomaliesDetected;
    private long healingActionsTriggered;
    private long startTime;
    
    public LiveMonitor(Brain brain, ApplicationConfig config) {
        this.collector = new SystemMetricsCollector();
        this.brain = brain;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.running = new AtomicBoolean(false);
        this.metricsHistory = new ConcurrentLinkedDeque<>();
        this.maxHistorySize = 100;
        
        this.criticalThreshold = config.getCriticalThreshold();
        this.warningThreshold = config.getUnhealthyThreshold();
        
        this.totalChecks = 0;
        this.anomaliesDetected = 0;
        this.healingActionsTriggered = 0;
    }
    
    /**
     * Start continuous monitoring
     */
    public void start(long intervalMs) {
        if (running.getAndSet(true)) {
            System.out.println(YELLOW + "⚠ Monitor already running" + RESET);
            return;
        }
        
        this.startTime = System.currentTimeMillis();
        
        System.out.println(CYAN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║              LIVE SYSTEM MONITOR - STARTED                    ║" + RESET);
        System.out.println(CYAN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
        System.out.println(GREEN + "  ✓ Monitoring interval: " + intervalMs + "ms" + RESET);
        System.out.println(GREEN + "  ✓ Critical threshold: " + (criticalThreshold * 100) + "%" + RESET);
        System.out.println(GREEN + "  ✓ Warning threshold: " + (warningThreshold * 100) + "%" + RESET);
        System.out.println();
        
        // Schedule monitoring task
        scheduler.scheduleAtFixedRate(
            this::monitoringCycle,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        );
        
        // Schedule statistics report every 30 seconds
        scheduler.scheduleAtFixedRate(
            this::printStatistics,
            30,
            30,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop monitoring
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        System.out.println();
        System.out.println(CYAN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║              LIVE SYSTEM MONITOR - STOPPED                    ║" + RESET);
        System.out.println(CYAN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
        
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        printFinalReport();
    }
    
    /**
     * Single monitoring cycle
     */
    private void monitoringCycle() {
        try {
            totalChecks++;
            
            // Collect metrics
            SystemMetricsSnapshot metrics = collector.collect();
            double healthScore = collector.calculateHealthScore(metrics);
            
            // Store in history
            metricsHistory.addLast(metrics);
            if (metricsHistory.size() > maxHistorySize) {
                metricsHistory.removeFirst();
            }
            
            // Check thresholds
            if (healthScore < criticalThreshold) {
                handleCritical(metrics, healthScore);
            } else if (healthScore < warningThreshold) {
                handleWarning(metrics, healthScore);
            } else {
                handleHealthy(metrics, healthScore);
            }
            
        } catch (Exception e) {
            System.err.println(RED + "✗ Monitoring cycle error: " + e.getMessage() + RESET);
        }
    }
    
    /**
     * Handle critical health state - trigger immediate healing
     */
    private void handleCritical(SystemMetricsSnapshot metrics, double healthScore) {
        anomaliesDetected++;
        healingActionsTriggered++;
        
        System.out.println();
        System.out.println(RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(RED + "🚨 CRITICAL: Health score " + String.format("%.2f", healthScore) + " below threshold " + criticalThreshold + RESET);
        System.out.println(RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(YELLOW + "  Metrics: " + metrics + RESET);
        
        // Determine issue type and create signal
        String issueType = determineIssueType(metrics);
        Signal signal = createSignal(metrics, healthScore, issueType);
        
        System.out.println(CYAN + "  → Triggering autonomous healing..." + RESET);
        
        // Process through Brain (async)
        brain.processSignal(signal).thenAccept(success -> {
            if (success) {
                System.out.println(GREEN + "  ✓ Healing action completed successfully" + RESET);
            } else {
                System.out.println(RED + "  ✗ Healing action failed" + RESET);
            }
        });
    }
    
    /**
     * Handle warning state - log but don't trigger healing yet
     */
    private void handleWarning(SystemMetricsSnapshot metrics, double healthScore) {
        System.out.println(YELLOW + "⚠ WARNING: Health score " + String.format("%.2f", healthScore) + 
                          " - " + metrics + RESET);
    }
    
    /**
     * Handle healthy state - minimal logging
     */
    private void handleHealthy(SystemMetricsSnapshot metrics, double healthScore) {
        // Only log every 10th check to reduce noise
        if (totalChecks % 10 == 0) {
            System.out.println(GREEN + "✓ HEALTHY: " + String.format("%.2f", healthScore) + 
                              " - " + metrics + RESET);
        }
    }
    
    /**
     * Determine issue type from metrics
     */
    private String determineIssueType(SystemMetricsSnapshot metrics) {
        if (metrics.cpuUsage > 0.9) {
            return "HIGH_CPU";
        } else if (metrics.memoryUsage > 0.9) {
            return "HIGH_MEMORY";
        } else if (metrics.heapUsage > 0.9) {
            return "HEAP_EXHAUSTION";
        } else if (metrics.threadCount > metrics.peakThreadCount * 0.95) {
            return "THREAD_EXHAUSTION";
        }
        return "SYSTEM_DEGRADED";
    }
    
    /**
     * Create signal for Brain processing
     */
    private Signal createSignal(SystemMetricsSnapshot metrics, double healthScore, String issueType) {
        Signal signal = new Signal("LiveMonitor", Signal.METRIC_UPDATE);
        signal.getData().put("health_score", healthScore);
        signal.getData().put("issue_type", issueType);
        signal.getData().put("cpu_usage", metrics.cpuUsage);
        signal.getData().put("memory_usage", metrics.memoryUsage);
        signal.getData().put("heap_usage", metrics.heapUsage);
        signal.getData().put("thread_count", metrics.threadCount);
        signal.getData().put("timestamp", metrics.timestamp);
        return signal;
    }
    
    /**
     * Print monitoring statistics
     */
    private void printStatistics() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println();
        System.out.println(CYAN + "┌─────────────────────────────────────────────────────────────┐" + RESET);
        System.out.println(CYAN + "│                   MONITORING STATISTICS                     │" + RESET);
        System.out.println(CYAN + "├─────────────────────────────────────────────────────────────┤" + RESET);
        System.out.printf(CYAN + "│" + RESET + "  Uptime:              %d seconds" + 
                         "                              " + CYAN + "│" + RESET + "%n", uptime);
        System.out.printf(CYAN + "│" + RESET + "  Total Checks:        %d" + 
                         "                                      " + CYAN + "│" + RESET + "%n", totalChecks);
        System.out.printf(CYAN + "│" + RESET + "  Anomalies Detected:  %d" + 
                         "                                      " + CYAN + "│" + RESET + "%n", anomaliesDetected);
        System.out.printf(CYAN + "│" + RESET + "  Healing Actions:     %d" + 
                         "                                      " + CYAN + "│" + RESET + "%n", healingActionsTriggered);
        
        if (!metricsHistory.isEmpty()) {
            SystemMetricsSnapshot latest = metricsHistory.getLast();
            double healthScore = collector.calculateHealthScore(latest);
            System.out.printf(CYAN + "│" + RESET + "  Current Health:      %.2f" + 
                             "                                      " + CYAN + "│" + RESET + "%n", healthScore);
        }
        
        System.out.println(CYAN + "└─────────────────────────────────────────────────────────────┘" + RESET);
        System.out.println();
    }
    
    /**
     * Print final report when stopping
     */
    private void printFinalReport() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println();
        System.out.println(CYAN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║                      FINAL REPORT                             ║" + RESET);
        System.out.println(CYAN + "╠═══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.printf(CYAN + "║" + RESET + "  Total Uptime:           %d seconds" + 
                         "                         " + CYAN + "║" + RESET + "%n", uptime);
        System.out.printf(CYAN + "║" + RESET + "  Total Checks:           %d" + 
                         "                                 " + CYAN + "║" + RESET + "%n", totalChecks);
        System.out.printf(CYAN + "║" + RESET + "  Anomalies Detected:     %d" + 
                         "                                 " + CYAN + "║" + RESET + "%n", anomaliesDetected);
        System.out.printf(CYAN + "║" + RESET + "  Healing Actions:        %d" + 
                         "                                 " + CYAN + "║" + RESET + "%n", healingActionsTriggered);
        
        if (totalChecks > 0) {
            double anomalyRate = (double) anomaliesDetected / totalChecks * 100;
            System.out.printf(CYAN + "║" + RESET + "  Anomaly Rate:           %.2f%%" + 
                             "                                " + CYAN + "║" + RESET + "%n", anomalyRate);
        }
        
        System.out.println(CYAN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
    }
    
    /**
     * Get current metrics snapshot
     */
    public SystemMetricsSnapshot getCurrentMetrics() {
        return collector.collect();
    }
    
    /**
     * Get metrics history
     */
    public List<SystemMetricsSnapshot> getMetricsHistory() {
        return new ArrayList<>(metricsHistory);
    }
    
    /**
     * Check if monitor is running
     */
    public boolean isRunning() {
        return running.get();
    }
}
