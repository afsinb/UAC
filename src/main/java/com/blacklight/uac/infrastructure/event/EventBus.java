package com.blacklight.uac.infrastructure.event;

import java.util.List;

/**
 * EventBus - Interface
 * Central event publishing and subscription hub
 */
public interface EventBus {
    
    /**
     * Subscribe to domain events
     */
    <T extends DomainEvent> void subscribe(Class<T> eventType, EventHandler<T> handler);
    
    /**
     * Publish domain event
     */
    <T extends DomainEvent> void publish(T event);
    
    /**
     * Get event history
     */
    List<DomainEvent> getEventHistory(long fromTimestamp, long toTimestamp);
    
    /**
     * Shutdown event bus
     */
    void shutdown();
}

