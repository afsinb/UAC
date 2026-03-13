package com.blacklight.uac.logmonitor;

/**
 * LogAnomaly - Represents an anomaly detected in logs
 */
public class LogAnomaly {
    
    public enum AnomalyType {
        NULL_POINTER_EXCEPTION,
        ARRAY_INDEX_OUT_OF_BOUNDS,
        CLASS_CAST_EXCEPTION,
        ILLEGAL_ARGUMENT,
        OUT_OF_MEMORY_ERROR,
        STACK_OVERFLOW_ERROR,
        CONNECTION_TIMEOUT,
        DATABASE_ERROR,
        AUTHENTICATION_FAILURE,
        PERMISSION_DENIED,
        FILE_NOT_FOUND,
        UNKNOWN_EXCEPTION
    }
    
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    private final String id;
    private final AnomalyType type;
    private final Severity severity;
    private final String message;
    private final String stackTrace;
    private final String sourceFile;
    private final int lineNumber;
    private final String className;
    private final String methodName;
    private final long timestamp;
    private final String rawLogLine;
    
    public LogAnomaly(String id, AnomalyType type, Severity severity, String message,
                      String stackTrace, String sourceFile, int lineNumber,
                      String className, String methodName, String rawLogLine) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.stackTrace = stackTrace;
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.className = className;
        this.methodName = methodName;
        this.timestamp = System.currentTimeMillis();
        this.rawLogLine = rawLogLine;
    }
    
    public String getId() { return id; }
    public AnomalyType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getStackTrace() { return stackTrace; }
    public String getSourceFile() { return sourceFile; }
    public int getLineNumber() { return lineNumber; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public long getTimestamp() { return timestamp; }
    public String getRawLogLine() { return rawLogLine; }
    
    /**
     * Check if this anomaly is fixable by code changes
     */
    public boolean isFixable() {
        return switch (type) {
            case NULL_POINTER_EXCEPTION,
                 ARRAY_INDEX_OUT_OF_BOUNDS,
                 CLASS_CAST_EXCEPTION,
                 ILLEGAL_ARGUMENT -> true;
            default -> false;
        };
    }
    
    /**
     * Get the fix strategy for this anomaly type
     */
    public String getFixStrategy() {
        return switch (type) {
            case NULL_POINTER_EXCEPTION -> "ADD_NULL_CHECK";
            case ARRAY_INDEX_OUT_OF_BOUNDS -> "ADD_BOUNDS_CHECK";
            case CLASS_CAST_EXCEPTION -> "ADD_TYPE_CHECK";
            case ILLEGAL_ARGUMENT -> "ADD_VALIDATION";
            default -> "MANUAL_REVIEW";
        };
    }
    
    @Override
    public String toString() {
        return String.format("LogAnomaly{id='%s', type=%s, severity=%s, class='%s', method='%s', line=%d}",
            id, type, severity, className, methodName, lineNumber);
    }
}
