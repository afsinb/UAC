package com.blacklight.uac.domain.metrics;

/**
 * MetricsContext - Domain Value Object
 * Contains context for metric analysis
 */
public class MetricsContext {
    private final SystemMetrics currentMetrics;
    private final SystemMetrics previousMetrics;
    private final long analysisWindow;
    
    public MetricsContext(SystemMetrics currentMetrics, SystemMetrics previousMetrics, long analysisWindow) {
        this.currentMetrics = currentMetrics;
        this.previousMetrics = previousMetrics;
        this.analysisWindow = analysisWindow;
    }
    
    public SystemMetrics getCurrentMetrics() {
        return currentMetrics;
    }
    
    public SystemMetrics getPreviousMetrics() {
        return previousMetrics;
    }
    
    public long getAnalysisWindow() {
        return analysisWindow;
    }
    
    public boolean hasMetricsChange() {
        if (previousMetrics == null) return false;
        return currentMetrics.getCpuUsage() != previousMetrics.getCpuUsage() ||
               currentMetrics.getMemoryUsage() != previousMetrics.getMemoryUsage() ||
               currentMetrics.getErrorRate() != previousMetrics.getErrorRate();
    }
    
    public double getCpuChange() {
        if (previousMetrics == null) return 0;
        return currentMetrics.getCpuUsage() - previousMetrics.getCpuUsage();
    }
    
    public double getMemoryChange() {
        if (previousMetrics == null) return 0;
        return currentMetrics.getMemoryUsage() - previousMetrics.getMemoryUsage();
    }
}

