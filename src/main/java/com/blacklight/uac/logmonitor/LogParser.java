package com.blacklight.uac.logmonitor;

import java.util.*;
import java.util.regex.*;

/**
 * LogParser - Parses log lines and detects anomalies
 * Supports common log formats and exception patterns
 */
public class LogParser {
    
    // Exception patterns
    private static final Pattern NULL_POINTER_PATTERN = Pattern.compile(
        "java\\.lang\\.NullPointerException(?:\\s*:\\s*(.*))?"
    );
    
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile(
        "java\\.lang\\.ArrayIndexOutOfBoundsException(?:\\s*:\\s*(.*))?"
    );
    
    private static final Pattern CLASS_CAST_PATTERN = Pattern.compile(
        "java\\.lang\\.ClassCastException(?:\\s*:\\s*(.*))?"
    );
    
    private static final Pattern ILLEGAL_ARGUMENT_PATTERN = Pattern.compile(
        "java\\.lang\\.IllegalArgumentException(?:\\s*:\\s*(.*))?"
    );
    
    private static final Pattern OUT_OF_MEMORY_PATTERN = Pattern.compile(
        "java\\.lang\\.OutOfMemoryError(?:\\s*:\\s*(.*))?"
    );
    
    private static final Pattern STACK_OVERFLOW_PATTERN = Pattern.compile(
        "java\\.lang\\.StackOverflowError(?:\\s*:\\s*(.*))?"
    );
    
    private static final Pattern CONNECTION_TIMEOUT_PATTERN = Pattern.compile(
        "(?:Connection|Socket|Read)\\s*(?:timed?\\s*out|timeout|TimeoutException)"
    );
    
    private static final Pattern DATABASE_ERROR_PATTERN = Pattern.compile(
        "(?:SQLException|DatabaseException|DB\\s*error)"
    );
    
    // Stack trace pattern
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
        "\\s*at\\s+([\\w.$]+)\\.([\\w<>]+)\\(([\\w.]+):(\\d+)\\)"
    );
    
    // Log level pattern
    private static final Pattern LOG_LEVEL_PATTERN = Pattern.compile(
        "\\b(DEBUG|INFO|WARN|ERROR|FATAL)\\b"
    );
    
    private int anomalyCounter = 0;
    
    /**
     * Parse a log line and detect anomalies
     */
    public Optional<LogAnomaly> parse(String logLine) {
        if (logLine == null || logLine.trim().isEmpty()) {
            return Optional.empty();
        }
        
        // Check for exception patterns
        LogAnomaly anomaly = detectException(logLine);
        if (anomaly != null) {
            return Optional.of(anomaly);
        }
        
        // Check for error-level logs
        if (isErrorLog(logLine)) {
            return Optional.of(createGenericError(logLine));
        }
        
        return Optional.empty();
    }
    
    /**
     * Parse multiple log lines and return all anomalies
     */
    public List<LogAnomaly> parseMultiple(List<String> logLines) {
        List<LogAnomaly> anomalies = new ArrayList<>();
        StringBuilder stackTraceBuilder = new StringBuilder();
        LogAnomaly pendingAnomaly = null;
        
        for (String line : logLines) {
            // Check if this is a stack trace continuation
            if (pendingAnomaly != null && STACK_TRACE_PATTERN.matcher(line).find()) {
                stackTraceBuilder.append(line).append("\n");
                continue;
            }
            
            // Check for new exception
            Optional<LogAnomaly> anomaly = parse(line);
            if (anomaly.isPresent()) {
                // If we had a pending anomaly, add it with accumulated stack trace
                if (pendingAnomaly != null) {
                    anomalies.add(createAnomalyWithStackTrace(pendingAnomaly, stackTraceBuilder.toString()));
                    stackTraceBuilder.setLength(0);
                }
                pendingAnomaly = anomaly.get();
            }
        }
        
        // Add final pending anomaly
        if (pendingAnomaly != null) {
            anomalies.add(createAnomalyWithStackTrace(pendingAnomaly, stackTraceBuilder.toString()));
        }
        
        return anomalies;
    }
    
    private LogAnomaly detectException(String logLine) {
        // Check for NullPointerException
        Matcher npeMatcher = NULL_POINTER_PATTERN.matcher(logLine);
        if (npeMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.NULL_POINTER_EXCEPTION, 
                LogAnomaly.Severity.HIGH, npeMatcher.group(1), logLine);
        }
        
        // Check for ArrayIndexOutOfBoundsException
        Matcher arrayMatcher = ARRAY_INDEX_PATTERN.matcher(logLine);
        if (arrayMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.ARRAY_INDEX_OUT_OF_BOUNDS,
                LogAnomaly.Severity.HIGH, arrayMatcher.group(1), logLine);
        }
        
        // Check for ClassCastException
        Matcher castMatcher = CLASS_CAST_PATTERN.matcher(logLine);
        if (castMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.CLASS_CAST_EXCEPTION,
                LogAnomaly.Severity.MEDIUM, castMatcher.group(1), logLine);
        }
        
        // Check for IllegalArgumentException
        Matcher argMatcher = ILLEGAL_ARGUMENT_PATTERN.matcher(logLine);
        if (argMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.ILLEGAL_ARGUMENT,
                LogAnomaly.Severity.MEDIUM, argMatcher.group(1), logLine);
        }
        
        // Check for OutOfMemoryError
        Matcher oomMatcher = OUT_OF_MEMORY_PATTERN.matcher(logLine);
        if (oomMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.OUT_OF_MEMORY_ERROR,
                LogAnomaly.Severity.CRITICAL, oomMatcher.group(1), logLine);
        }
        
        // Check for StackOverflowError
        Matcher stackMatcher = STACK_OVERFLOW_PATTERN.matcher(logLine);
        if (stackMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.STACK_OVERFLOW_ERROR,
                LogAnomaly.Severity.CRITICAL, stackMatcher.group(1), logLine);
        }
        
        // Check for connection timeout
        Matcher timeoutMatcher = CONNECTION_TIMEOUT_PATTERN.matcher(logLine);
        if (timeoutMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.CONNECTION_TIMEOUT,
                LogAnomaly.Severity.HIGH, "Connection timeout detected", logLine);
        }
        
        // Check for database error
        Matcher dbMatcher = DATABASE_ERROR_PATTERN.matcher(logLine);
        if (dbMatcher.find()) {
            return createAnomaly(LogAnomaly.AnomalyType.DATABASE_ERROR,
                LogAnomaly.Severity.HIGH, "Database error detected", logLine);
        }
        
        return null;
    }
    
    private LogAnomaly createAnomaly(LogAnomaly.AnomalyType type, LogAnomaly.Severity severity,
                                     String message, String logLine) {
        String id = "ANO-" + (++anomalyCounter) + "-" + System.currentTimeMillis();
        
        // Try to extract source location from stack trace
        String sourceFile = extractSourceFile(logLine);
        int lineNumber = extractLineNumber(logLine);
        String className = extractClassName(logLine);
        String methodName = extractMethodName(logLine);
        
        return new LogAnomaly(id, type, severity, message, "", sourceFile, 
            lineNumber, className, methodName, logLine);
    }
    
    private LogAnomaly createAnomalyWithStackTrace(LogAnomaly anomaly, String stackTrace) {
        // Re-extract source location from the stack trace if not already set
        String sourceFile = anomaly.getSourceFile();
        int lineNumber = anomaly.getLineNumber();
        String className = anomaly.getClassName();
        String methodName = anomaly.getMethodName();
        
        if (sourceFile == null || sourceFile.isEmpty()) {
            sourceFile = extractSourceFile(stackTrace);
            lineNumber = extractLineNumber(stackTrace);
            className = extractClassName(stackTrace);
            methodName = extractMethodName(stackTrace);
        }
        
        return new LogAnomaly(
            anomaly.getId(),
            anomaly.getType(),
            anomaly.getSeverity(),
            anomaly.getMessage(),
            stackTrace,
            sourceFile,
            lineNumber,
            className,
            methodName,
            anomaly.getRawLogLine()
        );
    }
    
    private LogAnomaly createGenericError(String logLine) {
        String id = "ANO-" + (++anomalyCounter) + "-" + System.currentTimeMillis();
        
        // Try to extract better message from error log
        String message = "Error detected in logs";
        if (logLine.contains(":")) {
            String[] parts = logLine.split(":", 2);
            if (parts.length > 1) {
                message = parts[1].trim();
                if (message.length() > 100) {
                    message = message.substring(0, 100) + "...";
                }
            }
        }
        
        // Try to extract source file even from generic errors
        String sourceFile = extractSourceFile(logLine);
        int lineNumber = extractLineNumber(logLine);
        String className = extractClassName(logLine);
        String methodName = extractMethodName(logLine);
        
        return new LogAnomaly(id, LogAnomaly.AnomalyType.UNKNOWN_EXCEPTION,
            LogAnomaly.Severity.MEDIUM, message, "",
            sourceFile, lineNumber, className, methodName, logLine);
    }
    
    private boolean isErrorLog(String logLine) {
        Matcher matcher = LOG_LEVEL_PATTERN.matcher(logLine);
        if (matcher.find()) {
            String level = matcher.group(1);
            return "ERROR".equals(level) || "FATAL".equals(level);
        }
        return false;
    }
    
    private String extractSourceFile(String logLine) {
        if (logLine == null || logLine.isEmpty()) {
            return "";
        }
        
        // Try stack trace pattern first
        Matcher matcher = STACK_TRACE_PATTERN.matcher(logLine);
        if (matcher.find()) {
            String sourceFile = matcher.group(3);
            if (sourceFile != null && !sourceFile.trim().isEmpty()) {
                return sourceFile.trim();
            }
        }
        
        // Try to extract from exception message pattern
        if (logLine.contains("at ")) {
            int atIndex = logLine.indexOf("at ");
            if (atIndex != -1) {
                String afterAt = logLine.substring(atIndex + 3);
                // Look for Java class notation (classname.method) followed by filename
                if (afterAt.contains("(") && afterAt.contains(")")) {
                    int openParen = afterAt.indexOf("(");
                    int closeParen = afterAt.indexOf(")");
                    if (openParen < closeParen) {
                        String fileInfo = afterAt.substring(openParen + 1, closeParen);
                        if (fileInfo.endsWith(".java") || fileInfo.contains(".java:")) {
                            String[] parts = fileInfo.split(":");
                            return parts[0].trim();
                        }
                    }
                }
            }
        }
        
        // Try to extract from class path pattern (com.example.ClassName)
        Pattern classPattern = Pattern.compile("\\b(\\w+\\.)+\\w+\\.java\\b");
        Matcher classMatcher = classPattern.matcher(logLine);
        if (classMatcher.find()) {
            return classMatcher.group(0);
        }
        
        // Try to extract any .java filename
        Pattern javaPattern = Pattern.compile("(\\w+)\\.java");
        Matcher javaMatcher = javaPattern.matcher(logLine);
        if (javaMatcher.find()) {
            return javaMatcher.group(0);
        }
        
        // Default to empty (never null)
        return "";
    }
    
    private int extractLineNumber(String logLine) {
        if (logLine == null || logLine.isEmpty()) {
            return 0;
        }
        
        // Try stack trace pattern first
        Matcher matcher = STACK_TRACE_PATTERN.matcher(logLine);
        if (matcher.find()) {
            try {
                String lineStr = matcher.group(4);
                if (lineStr != null && !lineStr.isEmpty()) {
                    return Integer.parseInt(lineStr);
                }
            } catch (NumberFormatException e) {
                // Fall through to other patterns
            }
        }
        
        // Try pattern like "ClassName.java:123"
        Pattern linePattern = Pattern.compile("\\.java:(\\d+)");
        Matcher lineMatcher = linePattern.matcher(logLine);
        if (lineMatcher.find()) {
            try {
                return Integer.parseInt(lineMatcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        
        return 0;
    }
    
    private String extractClassName(String logLine) {
        Matcher matcher = STACK_TRACE_PATTERN.matcher(logLine);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private String extractMethodName(String logLine) {
        Matcher matcher = STACK_TRACE_PATTERN.matcher(logLine);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return "";
    }
}
