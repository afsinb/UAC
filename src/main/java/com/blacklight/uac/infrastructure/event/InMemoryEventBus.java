package com.blacklight.uac.infrastructure.event;

import java.util.*;
import java.util.concurrent.*;

/**
 * InMemoryEventBus - Event Bus Implementation
 * Simple in-memory event bus with support for async handling
 */
public class InMemoryEventBus implements EventBus {
    private final Map<Class<?>, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();
    private final List<DomainEvent> eventHistory = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }
    
    @Override
    public <T extends DomainEvent> void publish(T event) {
        eventHistory.add(event);
        List<EventHandler<?>> eventHandlers = handlers.get(event.getClass());
        
        if (eventHandlers != null) {
            eventHandlers.forEach(handler -> {
                executor.execute(() -> {
                    try {
                        @SuppressWarnings("unchecked")
                        EventHandler<T> typedHandler = (EventHandler<T>) handler;
                        typedHandler.handle(event);
                    } catch (Exception e) {
                        System.err.println("Error handling event: " + e.getMessage());
                    }
                });
            });
        }
    }
    
    @Override
    public List<DomainEvent> getEventHistory(long fromTimestamp, long toTimestamp) {
        return eventHistory.stream()
            .filter(e -> e.getTimestamp() >= fromTimestamp && e.getTimestamp() <= toTimestamp)
            .toList();
    }
    
    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

