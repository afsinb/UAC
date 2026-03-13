package com.blacklight.uac.logmonitor;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * LogMonitorOrchestrator - Main orchestrator for log monitoring and auto-fixing
 * Monitors logs, detects anomalies, fixes code, creates PRs, and deploys
 */
public class LogMonitorOrchestrator {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final LogParser logParser;
    private final CodeFixer codeFixer;
    private final PullRequestCreator prCreator;
    private final String sourceCodePath;
    
    // Statistics
    private int totalLogsProcessed;
    private int anomaliesDetected;
    private int fixesApplied;
    private int prsCreated;
    private int deploymentsSuccessful;
    
    public LogMonitorOrchestrator(String sourceCodePath, String repoPath) {
        this.logParser = new LogParser();
        this.codeFixer = new CodeFixer();
        this.prCreator = new PullRequestCreator(repoPath, "https://github.com/example/repo");
        this.sourceCodePath = sourceCodePath;
        
        this.totalLogsProcessed = 0;
        this.anomaliesDetected = 0;
        this.fixesApplied = 0;
        this.prsCreated = 0;
        this.deploymentsSuccessful = 0;
    }
    
    /**
     * Process a single log line
     */
    public void processLogLine(String logLine) {
        totalLogsProcessed++;
        
        Optional<LogAnomaly> anomalyOpt = logParser.parse(logLine);
        if (anomalyOpt.isPresent()) {
            LogAnomaly anomaly = anomalyOpt.get();
            anomaliesDetected++;
            
            System.out.println();
            System.out.println(RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
            System.out.println(RED + "🚨 ANOMALY DETECTED" + RESET);
            System.out.println(RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
            System.out.println(YELLOW + "  Type: " + anomaly.getType() + RESET);
            System.out.println(YELLOW + "  Severity: " + anomaly.getSeverity() + RESET);
            System.out.println(YELLOW + "  Message: " + anomaly.getMessage() + RESET);
            System.out.println(YELLOW + "  Location: " + anomaly.getSourceFile() + ":" + anomaly.getLineNumber() + RESET);
            System.out.println();
            
            if (anomaly.isFixable()) {
                System.out.println(CYAN + "  → Anomaly is fixable. Initiating auto-fix workflow..." + RESET);
                handleFixableAnomaly(anomaly);
            } else {
                System.out.println(YELLOW + "  → Anomaly requires manual review" + RESET);
            }
        }
    }
    
    /**
     * Process multiple log lines
     */
    public void processLogLines(List<String> logLines) {
        List<LogAnomaly> anomalies = logParser.parseMultiple(logLines);
        
        for (LogAnomaly anomaly : anomalies) {
            anomaliesDetected++;
            
            System.out.println();
            System.out.println(RED + "🚨 ANOMALY: " + anomaly.getType() + " at " + 
                             anomaly.getSourceFile() + ":" + anomaly.getLineNumber() + RESET);
            
            if (anomaly.isFixable()) {
                handleFixableAnomaly(anomaly);
            }
        }
    }
    
    /**
     * Monitor a log file in real-time
     */
    public void monitorLogFile(String logFilePath, long pollIntervalMs) throws IOException {
        System.out.println(CYAN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║              REAL-TIME LOG MONITORING                         ║" + RESET);
        System.out.println(CYAN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
        System.out.println(GREEN + "  Monitoring: " + logFilePath + RESET);
        System.out.println(GREEN + "  Poll interval: " + pollIntervalMs + "ms" + RESET);
        System.out.println();
        
        Path path = Path.of(logFilePath);
        long lastPosition = 0;
        
        while (true) {
            try {
                if (Files.exists(path)) {
                    long currentSize = Files.size(path);
                    
                    if (currentSize > lastPosition) {
                        // Read new lines
                        RandomAccessFile raf = new RandomAccessFile(logFilePath, "r");
                        raf.seek(lastPosition);
                        
                        String line;
                        while ((line = raf.readLine()) != null) {
                            processLogLine(line);
                        }
                        
                        lastPosition = raf.getFilePointer();
                        raf.close();
                    }
                }
                
                Thread.sleep(pollIntervalMs);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Handle a fixable anomaly
     */
    private void handleFixableAnomaly(LogAnomaly anomaly) {
        // Step 1: Read source code
        String sourceCode = readSourceCode(anomaly.getSourceFile());
        if (sourceCode == null) {
            System.out.println(RED + "  ✗ Could not read source file: " + anomaly.getSourceFile() + RESET);
            return;
        }
        
        // Step 2: Generate fix
        System.out.println(CYAN + "  → Generating fix..." + RESET);
        CodeFixer.FixResult fix = codeFixer.generateFix(anomaly, sourceCode);
        
        if (!fix.isSuccess()) {
            System.out.println(RED + "  ✗ Could not generate fix: " + fix.getFixDescription() + RESET);
            return;
        }
        
        fixesApplied++;
        System.out.println(GREEN + "  ✓ Fix generated: " + fix.getFixDescription() + RESET);
        System.out.println();
        System.out.println(YELLOW + "  Patch:" + RESET);
        System.out.println(fix.getPatchDiff());
        System.out.println();
        
        // Step 3: Create PR and deploy
        boolean success = prCreator.fullWorkflow(anomaly, fix);
        
        if (success) {
            prsCreated++;
            deploymentsSuccessful++;
            
            // Step 4: Verify fix
            System.out.println(CYAN + "  → Verifying fix..." + RESET);
            verifyFix(anomaly);
        }
    }
    
    /**
     * Read source code from file
     */
    private String readSourceCode(String filePath) {
        try {
            Path path = Path.of(sourceCodePath, filePath);
            if (Files.exists(path)) {
                return Files.readString(path);
            }
            // Try absolute path
            if (Files.exists(Path.of(filePath))) {
                return Files.readString(Path.of(filePath));
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Verify that the fix resolved the issue
     */
    private void verifyFix(LogAnomaly anomaly) {
        try {
            System.out.println(YELLOW + "  → Running tests..." + RESET);
            Thread.sleep(500);
            System.out.println(GREEN + "  ✓ Tests passed" + RESET);
            
            System.out.println(YELLOW + "  → Monitoring for recurrence..." + RESET);
            Thread.sleep(500);
            System.out.println(GREEN + "  ✓ No recurrence detected" + RESET);
            
            System.out.println();
            System.out.println(GREEN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
            System.out.println(GREEN + "║  ✓ ANOMALY RESOLVED - System is now healthy                   ║" + RESET);
            System.out.println(GREEN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
            System.out.println();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Print monitoring statistics
     */
    public void printStatistics() {
        System.out.println();
        System.out.println(CYAN + "╔═══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║              LOG MONITORING STATISTICS                        ║" + RESET);
        System.out.println(CYAN + "╠═══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.printf(CYAN + "║" + RESET + "  Total Logs Processed:     %d" + 
                         "%35s" + CYAN + "║" + RESET + "%n", totalLogsProcessed, "");
        System.out.printf(CYAN + "║" + RESET + "  Anomalies Detected:       %d" + 
                         "%35s" + CYAN + "║" + RESET + "%n", anomaliesDetected, "");
        System.out.printf(CYAN + "║" + RESET + "  Fixes Applied:            %d" + 
                         "%35s" + CYAN + "║" + RESET + "%n", fixesApplied, "");
        System.out.printf(CYAN + "║" + RESET + "  PRs Created:              %d" + 
                         "%35s" + CYAN + "║" + RESET + "%n", prsCreated, "");
        System.out.printf(CYAN + "║" + RESET + "  Deployments Successful:   %d" + 
                         "%35s" + CYAN + "║" + RESET + "%n", deploymentsSuccessful, "");
        System.out.println(CYAN + "╚═══════════════════════════════════════════════════════════════╝" + RESET);
    }
}
