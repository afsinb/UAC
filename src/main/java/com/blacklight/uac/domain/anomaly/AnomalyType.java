package com.blacklight.uac.domain.anomaly;

/**
 * Anomaly Type - Domain Value Object
 * Defines different types of anomalies the system can detect
 */
public enum AnomalyType {
    METRIC_THRESHOLD_EXCEEDED("High CPU/Memory/Disk usage"),
    ERROR_RATE_SPIKE("Unusual error rate increase"),
    RESPONSE_TIME_DEGRADATION("Slow response times"),
    CONNECTION_FAILURE("Database/Service connection issues"),
    NULL_POINTER_EXCEPTION("Null pointer in application code"),
    RESOURCE_EXHAUSTION("Connection pool or memory exhaustion"),
    LOGIC_ERROR("Incorrect business logic results"),
    PERFORMANCE_REGRESSION("Performance degradation"),
    UNKNOWN("Unknown anomaly type");
    
    private final String description;
    
    AnomalyType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isOperational() {
        return this == METRIC_THRESHOLD_EXCEEDED || 
               this == ERROR_RATE_SPIKE || 
               this == RESPONSE_TIME_DEGRADATION || 
               this == CONNECTION_FAILURE || 
               this == RESOURCE_EXHAUSTION;
    }
    
    public boolean isLogical() {
        return this == NULL_POINTER_EXCEPTION || 
               this == LOGIC_ERROR || 
               this == PERFORMANCE_REGRESSION;
    }
}

