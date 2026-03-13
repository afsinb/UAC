package com.blacklight.uac.core.events;

import java.util.*;
import java.util.concurrent.*;

/**
 * In-memory implementation of EventBus with event sourcing.
 */
public class InMemoryEventBus implements EventBus {
    
    private final Map<Class<?>, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();
    private final List<UAEvent> eventLog = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    @Override
    public void publish(UAEvent event) {
        // Store in event log (event sourcing)
        eventLog.add(event);
        
        // Notify all subscribers
        List<EventHandler<?>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            for (EventHandler<?> handler : eventHandlers) {
                executor.execute(() -> {
                    try {
                        @SuppressWarnings("unchecked")
                        EventHandler<UAEvent> typedHandler = (EventHandler<UAEvent>) handler;
                        typedHandler.handle(event);
                    } catch (Exception e) {
                        System.err.println("Error handling event: " + e.getMessage());
                    }
                });
            }
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T extends UAEvent> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T extends UAEvent> void unsubscribe(Class<T> eventType, EventHandler<T> handler) {
        List<EventHandler<?>> eventHandlers = handlers.get(eventType);
        if (eventHandlers != null) {
            eventHandlers.remove(handler);
        }
    }
    
    @Override
    public List<UAEvent> getEventHistory(long fromTimestamp, long toTimestamp) {
        return eventLog.stream()
            .filter(e -> e.getTimestamp() >= fromTimestamp && e.getTimestamp() <= toTimestamp)
            .toList();
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}

