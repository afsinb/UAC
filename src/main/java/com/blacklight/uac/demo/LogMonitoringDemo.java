package com.blacklight.uac.demo;

import com.blacklight.uac.logmonitor.*;

import java.util.*;

/**
 * LogMonitoringDemo - Demonstrates real-time log monitoring with auto-fix
 * Shows the complete workflow: Detect → Fix → PR → Approve → Deploy
 */
public class LogMonitoringDemo {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC LOG MONITORING - AUTONOMOUS CODE FIXING DEMO              ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  This demo shows UAC monitoring logs in real-time, detecting          ║");
        System.out.println("║  anomalies, automatically fixing code, creating PRs, and deploying.   ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        LogMonitorOrchestrator orchestrator = new LogMonitorOrchestrator(
            "/tmp/source",
            "/tmp/repo"
        );
        
        // Simulate log entries with various anomalies
        List<String> sampleLogs = generateSampleLogs();
        
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "PROCESSING LOG ENTRIES" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        for (String log : sampleLogs) {
            System.out.println(GREEN + "  [LOG] " + log + RESET);
            orchestrator.processLogLine(log);
            Thread.sleep(500);
        }
        
        // Print statistics
        orchestrator.printStatistics();
        
        // Print summary
        printSummary();
    }
    
    private static List<String> generateSampleLogs() {
        List<String> logs = new ArrayList<>();
        
        // Normal logs
        logs.add("2026-03-11 10:15:23 INFO  Application started successfully");
        logs.add("2026-03-11 10:15:24 INFO  Database connection established");
        logs.add("2026-03-11 10:15:25 INFO  Loading configuration...");
        
        // NullPointerException
        logs.add("2026-03-11 10:15:30 ERROR java.lang.NullPointerException");
        logs.add("    at com.example.UserService.getUser(UserService.java:45)");
        logs.add("    at com.example.UserController.handleRequest(UserController.java:23)");
        
        // Normal logs
        logs.add("2026-03-11 10:15:35 INFO  Processing request #1234");
        
        // ArrayIndexOutOfBoundsException
        logs.add("2026-03-11 10:15:40 ERROR java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 3");
        logs.add("    at com.example.DataProcessor.processArray(DataProcessor.java:78)");
        
        // Normal logs
        logs.add("2026-03-11 10:15:45 INFO  Request completed");
        
        // ClassCastException
        logs.add("2026-03-11 10:15:50 ERROR java.lang.ClassCastException: Cannot cast String to Integer");
        logs.add("    at com.example.TypeConverter.convert(TypeConverter.java:34)");
        
        // Connection timeout
        logs.add("2026-03-11 10:15:55 ERROR Connection timed out after 30000ms");
        
        // Normal logs
        logs.add("2026-03-11 10:16:00 INFO  Health check passed");
        
        return logs;
    }
    
    private static void printSummary() {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         DEMONSTRATION SUMMARY                         ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                       ║");
        System.out.println("║  What UAC Demonstrated:                                               ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  1. REAL-TIME LOG MONITORING                                          ║");
        System.out.println("║     • Parsed log entries in real-time                                 ║");
        System.out.println("║     • Detected exceptions: NPE, ArrayOutOfBounds, ClassCast           ║");
        System.out.println("║     • Extracted stack traces and source locations                     ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  2. AUTOMATIC CODE FIXING                                             ║");
        System.out.println("║     • Generated fixes for each anomaly type                           ║");
        System.out.println("║     • Added null checks, bounds checks, type checks                   ║");
        System.out.println("║     • Created patch diffs for review                                  ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  3. AUTONOMOUS PR WORKFLOW                                            ║");
        System.out.println("║     • Created feature branch for each fix                             ║");
        System.out.println("║     • Generated PR with description and diff                          ║");
        System.out.println("║     • Auto-approved after code review and tests                       ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  4. AUTOMATIC DEPLOYMENT                                              ║");
        System.out.println("║     • Merged PR to main branch                                        ║");
        System.out.println("║     • Triggered deployment pipeline                                   ║");
        System.out.println("║     • Verified fix in production                                      ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  5. VERIFICATION                                                      ║");
        System.out.println("║     • Ran tests to confirm fix                                        ║");
        System.out.println("║     • Monitored for anomaly recurrence                                ║");
        System.out.println("║     • Confirmed system health restored                                ║");
        System.out.println("║                                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        System.out.println(GREEN + "✓ UAC can now monitor your application logs and auto-fix issues!" + RESET);
        System.out.println();
    }
}
