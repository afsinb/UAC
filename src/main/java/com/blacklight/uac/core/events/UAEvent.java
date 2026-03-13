package com.blacklight.uac.core.events;

/**
 * Base event class for UAC event-driven architecture.
 * All system events derive from this, enabling event sourcing and replay.
 */
public abstract class UAEvent {
    private final String eventId;
    private final long timestamp;
    private final String source;
    private final EventPriority priority;
    
    public UAEvent(String source, EventPriority priority) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.source = source;
        this.priority = priority;
    }
    
    public String getEventId() { return eventId; }
    public long getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public EventPriority getPriority() { return priority; }
    
    public abstract String getEventType();
}

