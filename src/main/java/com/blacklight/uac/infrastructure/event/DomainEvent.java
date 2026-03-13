package com.blacklight.uac.infrastructure.event;

/**
 * DomainEvent - Base Event Class
 * All domain events inherit from this
 */
public abstract class DomainEvent {
    private final String eventId;
    private final long timestamp;
    private final String aggregateId;
    
    protected DomainEvent(String aggregateId) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.aggregateId = aggregateId;
    }
    
    public String getEventId() { return eventId; }
    public long getTimestamp() { return timestamp; }
    public String getAggregateId() { return aggregateId; }
    
    public abstract String getEventType();
}

