package com.blacklight.uac.observer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ObserverTest {
    private Observer observer;
    
    @BeforeEach
    public void setUp() {
        observer = new Observer();
    }
    
    @Test
    public void testHealthScoreCalculationWithMetrics() {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("cpu_usage", 45.0));
        metrics.add(new Metric("memory_usage", 30.0));
        
        List<LogEntry> logs = new ArrayList<>();
        List<DNAChange> dnaChanges = new ArrayList<>();
        
        double healthScore = observer.mapToHealthScore(metrics, logs, dnaChanges);
        
        // Health score should be normalized between 0.0 and 1.0
        assertTrue(healthScore >= 0.0 && healthScore <= 1.0, "Health score should be between 0.0 and 1.0");
        // Good metrics (45% CPU, 30% memory) should yield healthy score
        // CPU score: 1.0 - (45/80) = 0.4375, Memory: 1.0 - (30/70) = 0.571, avg = 0.504
        assertTrue(healthScore >= 0.4, "Health score should be acceptable for moderate metrics");
    }
    
    @Test
    public void testHealthScoreWithErrors() {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("error_rate", 95.0));
        
        List<LogEntry> logs = new ArrayList<>();
        List<DNAChange> dnaChanges = new ArrayList<>();
        
        double healthScore = observer.mapToHealthScore(metrics, logs, dnaChanges);
        
        assertTrue(healthScore < 0.5, "Health score should be low for high error rates");
    }
    
    @Test
    public void testHealthScoreWithLogs() {
        List<Metric> metrics = new ArrayList<>();
        List<LogEntry> logs = new ArrayList<>();
        logs.add(new LogEntry("error", "Database connection failed", "database"));
        logs.add(new LogEntry("error", "Timeout occurred", "network"));
        logs.add(new LogEntry("warning", "Memory pressure high", "system"));
        
        List<DNAChange> dnaChanges = new ArrayList<>();
        
        double healthScore = observer.mapToHealthScore(metrics, logs, dnaChanges);
        
        assertTrue(healthScore < 1.0, "Health score should decrease with errors");
    }
    
    @Test
    public void testHealthScoreWithDNAChanges() {
        List<Metric> metrics = new ArrayList<>();
        List<LogEntry> logs = new ArrayList<>();
        List<DNAChange> dnaChanges = new ArrayList<>();
        
        DNAChange goodChange = new DNAChange("abc123", "john.doe", "feat: add new feature");
        goodChange.setTimestamp(System.currentTimeMillis());
        dnaChanges.add(goodChange);
        
        double healthScore = observer.mapToHealthScore(metrics, logs, dnaChanges);
        
        assertTrue(healthScore >= 0.0 && healthScore <= 1.0);
    }
    
    @Test
    public void testHealthScoreWithProblematicDNA() {
        List<Metric> metrics = new ArrayList<>();
        List<LogEntry> logs = new ArrayList<>();
        List<DNAChange> dnaChanges = new ArrayList<>();
        
        DNAChange buggyChange = new DNAChange("def456", "jane.doe", "bug: critical issue introduced");
        buggyChange.setTimestamp(System.currentTimeMillis());
        dnaChanges.add(buggyChange);
        
        double healthScore = observer.mapToHealthScore(metrics, logs, dnaChanges);
        
        assertTrue(healthScore < 1.0, "Health score should decrease with buggy changes");
    }
    
    @Test
    public void testMetricsBufferRetrieval() {
        List<Metric> metrics = observer.getMetricsBuffer();
        
        assertNotNull(metrics);
        assertTrue(metrics.isEmpty());
    }
    
    @Test
    public void testLogsBufferRetrieval() {
        List<LogEntry> logs = observer.getLogsBuffer();
        
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }
    
    @Test
    public void testDNABufferRetrieval() {
        List<DNAChange> dnaChanges = observer.getDnaBuffer();
        
        assertNotNull(dnaChanges);
        assertTrue(dnaChanges.isEmpty());
    }
    
    @Test
    public void testStandardizedErrorOutput() {
        Exception testException = new RuntimeException("Test error message");
        String output = observer.generateStandardizedOutput(testException);
        
        assertNotNull(output);
        assertTrue(output.contains("RuntimeException"));
        assertTrue(output.contains("Test error message"));
        assertTrue(output.contains("timestamp"));
    }
}

