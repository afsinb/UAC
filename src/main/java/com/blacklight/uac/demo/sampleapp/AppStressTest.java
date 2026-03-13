package com.blacklight.uac.demo.sampleapp;

import java.util.*;
import java.util.concurrent.*;

/**
 * AppStressTest - Triggers bugs in SampleApplication to generate anomalies
 * UAC will detect these anomalies and attempt to fix them
 */
public class AppStressTest {
    
    private final SampleApplication app;
    private final List<String> anomalyLog;
    private final ExecutorService executor;
    private volatile boolean running;
    
    public AppStressTest(SampleApplication app) {
        this.app = app;
        this.anomalyLog = Collections.synchronizedList(new ArrayList<>());
        this.executor = Executors.newFixedThreadPool(5);
        this.running = true;
    }
    
    /**
     * Run all stress tests to trigger anomalies
     */
    public void runAllTests() {
        System.out.println("🔥 Starting stress tests to trigger anomalies...");
        
        // Trigger BUG #1: NullPointerException
        triggerNullPointerException();
        
        // Trigger BUG #2: ArrayIndexOutOfBoundsException
        triggerArrayIndexOutOfBounds();
        
        // Trigger BUG #3: ClassCastException
        triggerClassCastException();
        
        // Trigger BUG #4: Connection leak
        triggerConnectionLeak();
        
        // Trigger BUG #5: Thread safety issue
        triggerThreadSafetyIssue();
        
        // Trigger BUG #6: Memory leak
        triggerMemoryLeak();
        
        // Trigger BUG #7: ConcurrentModificationException
        triggerConcurrentModification();
        
        // Trigger BUG #8: Resource exhaustion
        triggerResourceExhaustion();
        
        // Trigger BUG #9: SQL Injection
        triggerSQLInjection();
        
        // Trigger BUG #10: ArithmeticException
        triggerArithmeticException();
    }
    
    private void triggerNullPointerException() {
        try {
            System.out.println("  → Triggering NullPointerException...");
            // User doesn't exist
            app.getUserEmail("nonexistent_user");
            anomalyLog.add("NPE: getUserEmail with nonexistent user");
        } catch (NullPointerException e) {
            anomalyLog.add("NPE caught: " + e.getMessage());
        }
        
        try {
            // User has null email
            app.getUserEmail("user3");
            anomalyLog.add("NPE: getUserEmail with null email");
        } catch (NullPointerException e) {
            anomalyLog.add("NPE caught: " + e.getMessage());
        }
    }
    
    private void triggerArrayIndexOutOfBounds() {
        try {
            System.out.println("  → Triggering ArrayIndexOutOfBoundsException...");
            app.getRecentOrder(100); // Out of bounds
            anomalyLog.add("AIOOBE: getRecentOrder with invalid index");
        } catch (ArrayIndexOutOfBoundsException e) {
            anomalyLog.add("AIOOBE caught: " + e.getMessage());
        }
    }
    
    private void triggerClassCastException() {
        try {
            System.out.println("  → Triggering ClassCastException...");
            app.processRequest(12345); // Pass Integer instead of String
            anomalyLog.add("CCE: processRequest with Integer");
        } catch (ClassCastException e) {
            anomalyLog.add("CCE caught: " + e.getMessage());
        }
    }
    
    private void triggerConnectionLeak() {
        System.out.println("  → Triggering Connection leak...");
        for (int i = 0; i < 10; i++) {
            app.queryDatabase("SELECT * FROM users");
        }
        anomalyLog.add("Connection leak: 10 connections opened, 0 closed");
    }
    
    private void triggerThreadSafetyIssue() {
        System.out.println("  → Triggering Thread safety issue...");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(CompletableFuture.runAsync(() -> app.incrementCounter()));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        anomalyLog.add("Thread safety: 100 concurrent increments");
    }
    
    private void triggerMemoryLeak() {
        System.out.println("  → Triggering Memory leak...");
        for (int i = 0; i < 1000; i++) {
            app.createSession("user" + i);
        }
        anomalyLog.add("Memory leak: 1000 sessions created, none cleaned");
    }
    
    private void triggerConcurrentModification() {
        try {
            System.out.println("  → Triggering ConcurrentModificationException...");
            app.searchUsers("admin");
            anomalyLog.add("CME: searchUsers with admin pattern");
        } catch (Exception e) {
            anomalyLog.add("CME caught: " + e.getMessage());
        }
    }
    
    private void triggerResourceExhaustion() {
        System.out.println("  → Triggering Resource exhaustion...");
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(app.handleRequestAsync("request" + i));
        }
        anomalyLog.add("Resource exhaustion: 100 async requests");
    }
    
    private void triggerSQLInjection() {
        System.out.println("  → Triggering SQL Injection...");
        app.findUserByName("'; DROP TABLE users; --");
        anomalyLog.add("SQL Injection: malicious input passed");
    }
    
    private void triggerArithmeticException() {
        try {
            System.out.println("  → Triggering ArithmeticException...");
            app.calculateAverage(new ArrayList<>()); // Empty list
            anomalyLog.add("ArithmeticException: division by zero");
        } catch (ArithmeticException e) {
            anomalyLog.add("ArithmeticException caught: " + e.getMessage());
        }
    }
    
    /**
     * Run continuous stress test
     */
    public void runContinuous() {
        System.out.println("🔄 Starting continuous stress test...");
        
        while (running) {
            try {
                // Randomly trigger different bugs
                int bug = (int) (Math.random() * 10);
                
                switch (bug) {
                    case 0 -> triggerNullPointerException();
                    case 1 -> triggerArrayIndexOutOfBounds();
                    case 2 -> triggerClassCastException();
                    case 3 -> triggerConnectionLeak();
                    case 4 -> triggerThreadSafetyIssue();
                    case 5 -> triggerMemoryLeak();
                    case 6 -> triggerConcurrentModification();
                    case 7 -> triggerResourceExhaustion();
                    case 8 -> triggerSQLInjection();
                    case 9 -> triggerArithmeticException();
                }
                
                Thread.sleep(2000); // Wait 2 seconds between triggers
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public void stop() {
        running = false;
        executor.shutdownNow();
    }
    
    public List<String> getAnomalyLog() {
        return new ArrayList<>(anomalyLog);
    }
    
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = app.getMetrics();
        metrics.put("anomaliesTriggered", anomalyLog.size());
        return metrics;
    }
}
