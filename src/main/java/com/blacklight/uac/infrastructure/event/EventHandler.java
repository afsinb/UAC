package com.blacklight.uac.infrastructure.event;

/**
 * EventHandler - Interface for event handlers
 */
@FunctionalInterface
public interface EventHandler<T extends DomainEvent> {
    void handle(T event) throws Exception;
}

