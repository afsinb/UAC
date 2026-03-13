package com.blacklight.uac.core.events;

/**
 * Functional interface for handling events asynchronously.
 */
public interface EventHandler<T extends UAEvent> {
    void handle(T event) throws Exception;
}

