package com.blacklight.uac.core.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class EventBusTest {
    
    private EventBus eventBus;
    
    @BeforeEach
    public void setUp() {
        eventBus = new InMemoryEventBus();
    }
    
    @Test
    public void testEventBusPublish() {
        Map<String, Object> data = new HashMap<>();
        data.put("cpu", 85.0);
        
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.3, "METRIC_ANOMALY", data
        );
        
        assertDoesNotThrow(() -> eventBus.publish(event));
    }
    
    @Test
    public void testEventSubscription() {
        boolean[] handlerCalled = {false};
        
        eventBus.subscribe(AnomalyDetectedEvent.class, event -> {
            handlerCalled[0] = true;
        });
        
        Map<String, Object> data = new HashMap<>();
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.3, "METRIC_ANOMALY", data
        );
        
        eventBus.publish(event);
        
        try {
            Thread.sleep(100); // Wait for async handler
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        assertTrue(handlerCalled[0]);
    }
    
    @Test
    public void testEventHistoryRetrieval() {
        long now = System.currentTimeMillis();
        
        Map<String, Object> data = new HashMap<>();
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.3, "METRIC_ANOMALY", data
        );
        
        eventBus.publish(event);
        
        List<UAEvent> history = eventBus.getEventHistory(now - 1000, now + 1000);
        
        assertEquals(1, history.size());
        assertEquals(event.getEventType(), history.get(0).getEventType());
    }
    
    @Test
    public void testEventPriority() {
        AnomalyDetectedEvent criticalEvent = new AnomalyDetectedEvent(
            "observer", 0.1, "METRIC_ANOMALY", new HashMap<>()
        );
        
        assertEquals(EventPriority.CRITICAL, criticalEvent.getPriority());
        
        AnomalyDetectedEvent highEvent = new AnomalyDetectedEvent(
            "observer", 0.4, "METRIC_ANOMALY", new HashMap<>()
        );
        
        assertEquals(EventPriority.HIGH, highEvent.getPriority());
    }
    
    @Test
    public void testMultipleSubscribers() {
        int[] count = {0};
        
        eventBus.subscribe(AnomalyDetectedEvent.class, event -> count[0]++);
        eventBus.subscribe(AnomalyDetectedEvent.class, event -> count[0]++);
        
        Map<String, Object> data = new HashMap<>();
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.3, "METRIC_ANOMALY", data
        );
        
        eventBus.publish(event);
        
        try {
            Thread.sleep(100); // Wait for async handlers
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        assertEquals(2, count[0]);
    }
}

