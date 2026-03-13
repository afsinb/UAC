package com.blacklight.uac.core.coordinator;

import com.blacklight.uac.evolver.Evolver;
import com.blacklight.uac.observer.Observer;
import com.blacklight.uac.resolver.Resolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainTest {
    private Brain brain;
    private Observer observerMock;
    private Resolver resolverMock;
    private Evolver evolverMock;
    
    @BeforeEach
    public void setUp() {
        observerMock = mock(Observer.class);
        resolverMock = mock(Resolver.class);
        evolverMock = mock(Evolver.class);
        
        brain = new Brain(observerMock, resolverMock, evolverMock);
    }
    
    @Test
    public void testBrainInitialization() {
        assertNotNull(brain);
    }
    
    @Test
    public void testProcessMetricUpdateSignal() {
        Signal signal = new Signal("system", Signal.METRIC_UPDATE);
        signal.getData().put("cpu", 45.0);
        
        // Should not throw exception
        assertDoesNotThrow(() -> brain.processSignal(signal));
    }
    
    @Test
    public void testProcessErrorDetectedSignal() {
        Signal signal = new Signal("application", Signal.ERROR_DETECTED);
        signal.getData().put("error_type", "NullPointerException");
        
        assertDoesNotThrow(() -> brain.processSignal(signal));
    }
    
    @Test
    public void testProcessEvolutionRequestSignal() {
        Signal signal = new Signal("system", Signal.EVOLUTION_REQUEST);
        signal.getData().put("task_type", "patch");
        
        assertDoesNotThrow(() -> brain.processSignal(signal));
    }
    
    @Test
    public void testSignalWithUnknownType() {
        Signal signal = new Signal("system", "UNKNOWN_TYPE");
        
        assertDoesNotThrow(() -> brain.processSignal(signal));
    }
    
    @Test
    public void testBrainShutdown() {
        assertDoesNotThrow(() -> brain.shutdown());
        verify(resolverMock, times(1)).shutdown();
    }
    
    @Test
    public void testSignalCreation() {
        Signal signal = new Signal("observer", Signal.METRIC_UPDATE);
        
        assertEquals("observer", signal.getSource());
        assertEquals(Signal.METRIC_UPDATE, signal.getType());
        assertTrue(signal.getTimestamp() > 0);
        assertNotNull(signal.getData());
    }
    
    @Test
    public void testSignalDataManipulation() {
        Signal signal = new Signal("test", "TEST_TYPE");
        signal.getData().put("key1", "value1");
        signal.getData().put("key2", 123);
        
        assertEquals("value1", signal.getData().get("key1"));
        assertEquals(123, signal.getData().get("key2"));
    }
    
    @Test
    public void testSignalConstants() {
        // Verify all standard signal types are defined
        assertNotNull(Signal.METRIC_UPDATE);
        assertNotNull(Signal.ERROR_DETECTED);
        assertNotNull(Signal.EVOLUTION_REQUEST);
        assertNotNull(Signal.HEALTH_THRESHOLD);
        assertNotNull(Signal.DNA_CHANGE);
    }
}
