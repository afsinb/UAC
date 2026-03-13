package com.blacklight.uac.core.events;

/**
 * Event bus for asynchronous event-driven architecture.
 * Enables decoupled communication between UAC components.
 */
public interface EventBus {
    
    /**
     * Publish an event to all registered subscribers.
     */
    void publish(UAEvent event);
    
    /**
     * Subscribe to events of a specific type.
     */
    <T extends UAEvent> void subscribe(Class<T> eventType, EventHandler<T> handler);
    
    /**
     * Unsubscribe from event notifications.
     */
    <T extends UAEvent> void unsubscribe(Class<T> eventType, EventHandler<T> handler);
    
    /**
     * Get event history for audit trail (event sourcing).
     */
    java.util.List<UAEvent> getEventHistory(long fromTimestamp, long toTimestamp);
}

