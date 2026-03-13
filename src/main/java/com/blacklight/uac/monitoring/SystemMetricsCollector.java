package com.blacklight.uac.monitoring;

import java.lang.management.*;
import java.util.*;

/**
 * SystemMetricsCollector - Collects real system metrics via JMX
 * Provides actual CPU, memory, disk, and thread metrics
 */
public class SystemMetricsCollector {
    
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    public SystemMetricsCollector() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }
    
    /**
     * Collect all system metrics
     */
    public SystemMetricsSnapshot collect() {
        return new SystemMetricsSnapshot(
            getCpuUsage(),
            getMemoryUsage(),
            getHeapUsage(),
            getNonHeapUsage(),
            getThreadCount(),
            getPeakThreadCount(),
            getDaemonThreadCount(),
            getGcCount(),
            getGcTime(),
            getSystemLoadAverage(),
            getAvailableProcessors(),
            getUptime()
        );
    }
    
    /**
     * Get CPU usage (0.0 - 1.0)
     */
    public double getCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return sunBean.getProcessCpuLoad();
        }
        // Fallback: estimate from system load
        double load = osBean.getSystemLoadAverage();
        int cores = osBean.getAvailableProcessors();
        return Math.min(1.0, load / cores);
    }
    
    /**
     * Get memory usage (0.0 - 1.0)
     */
    public double getMemoryUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            long total = sunBean.getTotalPhysicalMemorySize();
            long free = sunBean.getFreePhysicalMemorySize();
            if (total > 0) {
                return (double) (total - free) / total;
            }
        }
        // Fallback: use heap
        return getHeapUsage();
    }
    
    /**
     * Get heap memory usage (0.0 - 1.0)
     */
    public double getHeapUsage() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        if (max > 0) {
            return (double) used / max;
        }
        return (double) used / heapUsage.getCommitted();
    }
    
    /**
     * Get non-heap memory usage (0.0 - 1.0)
     */
    public double getNonHeapUsage() {
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        long used = nonHeapUsage.getUsed();
        long max = nonHeapUsage.getMax();
        if (max > 0) {
            return (double) used / max;
        }
        return 0.5; // Default if max not defined
    }
    
    /**
     * Get current thread count
     */
    public int getThreadCount() {
        return threadBean.getThreadCount();
    }
    
    /**
     * Get peak thread count
     */
    public int getPeakThreadCount() {
        return threadBean.getPeakThreadCount();
    }
    
    /**
     * Get daemon thread count
     */
    public int getDaemonThreadCount() {
        return threadBean.getDaemonThreadCount();
    }
    
    /**
     * Get total GC count
     */
    public long getGcCount() {
        return gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
    }
    
    /**
     * Get total GC time in milliseconds
     */
    public long getGcTime() {
        return gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
    
    /**
     * Get system load average
     */
    public double getSystemLoadAverage() {
        return osBean.getSystemLoadAverage();
    }
    
    /**
     * Get available processors
     */
    public int getAvailableProcessors() {
        return osBean.getAvailableProcessors();
    }
    
    /**
     * Get JVM uptime in milliseconds
     */
    public long getUptime() {
        return runtimeBean.getUptime();
    }
    
    /**
     * Calculate health score from metrics (0.0 - 1.0)
     */
    public double calculateHealthScore(SystemMetricsSnapshot metrics) {
        // Weighted health calculation
        double cpuScore = 1.0 - metrics.cpuUsage;
        double memoryScore = 1.0 - metrics.memoryUsage;
        double heapScore = 1.0 - metrics.heapUsage;
        double threadScore = calculateThreadScore(metrics.threadCount, metrics.peakThreadCount);
        
        // Weights: CPU (30%), Memory (30%), Heap (25%), Threads (15%)
        double healthScore = (cpuScore * 0.30) + 
                            (memoryScore * 0.30) + 
                            (heapScore * 0.25) + 
                            (threadScore * 0.15);
        
        // Clamp to 0.0 - 1.0
        return Math.max(0.0, Math.min(1.0, healthScore));
    }
    
    private double calculateThreadScore(int current, int peak) {
        if (peak == 0) return 1.0;
        double ratio = (double) current / peak;
        // Good if current is less than 80% of peak
        if (ratio < 0.8) return 1.0;
        if (ratio < 0.9) return 0.7;
        if (ratio < 1.0) return 0.4;
        return 0.2; // At or above peak
    }
    
    /**
     * Snapshot of system metrics at a point in time
     */
    public static class SystemMetricsSnapshot {
        public final double cpuUsage;
        public final double memoryUsage;
        public final double heapUsage;
        public final double nonHeapUsage;
        public final int threadCount;
        public final int peakThreadCount;
        public final int daemonThreadCount;
        public final long gcCount;
        public final long gcTime;
        public final double systemLoad;
        public final int availableProcessors;
        public final long uptime;
        public final long timestamp;
        
        public SystemMetricsSnapshot(double cpuUsage, double memoryUsage, double heapUsage,
                                    double nonHeapUsage, int threadCount, int peakThreadCount,
                                    int daemonThreadCount, long gcCount, long gcTime,
                                    double systemLoad, int availableProcessors, long uptime) {
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.heapUsage = heapUsage;
            this.nonHeapUsage = nonHeapUsage;
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.daemonThreadCount = daemonThreadCount;
            this.gcCount = gcCount;
            this.gcTime = gcTime;
            this.systemLoad = systemLoad;
            this.availableProcessors = availableProcessors;
            this.uptime = uptime;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format(
                "Metrics{cpu=%.1f%%, mem=%.1f%%, heap=%.1f%%, threads=%d, load=%.2f}",
                cpuUsage * 100, memoryUsage * 100, heapUsage * 100, threadCount, systemLoad
            );
        }
    }
}
