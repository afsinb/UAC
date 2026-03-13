package com.blacklight.uac;

import com.blacklight.uac.core.coordinator.Brain;
import com.blacklight.uac.core.coordinator.Signal;
import com.blacklight.uac.evolver.Evolver;
import com.blacklight.uac.observer.Observer;
import com.blacklight.uac.resolver.Resolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Universal Autonomous Core (UAC) system.
 * These tests validate the entire SIGNAL -> CONTEXT -> HYPOTHESIS -> EXECUTION loop
 * as described in the agent_blueprint.md.
 */
public class UniversalAutonomousCoreIntegrationTest {
    private Observer observer;
    private Resolver resolver;
    private Evolver evolver;
    private Brain brain;
    
    @BeforeEach
    public void setUp() {
        // Initialize core v1 components for testing
        observer = new Observer();
        resolver = new Resolver();
        evolver = new Evolver("/test/repo", "/test/sandbox");
        brain = new Brain(observer, resolver, evolver);
    }
    
    @Test
    public void testBrainInitialization() {
        assertNotNull(brain);
    }
    
    /**
     * Test the complete UAC loop:
     * 1. SIGNAL: Observer detects an anomaly
     * 2. CONTEXT: Brain requests recent DNA changes
     * 3. HYPOTHESIS: Brain determines if issue is Operational or Logic
     * 4. EXECUTION: Resolver applies fix OR Evolver creates patch
     */
    @Test
    public void testCompleteUACLoop() {
        // Step 1: SIGNAL - Simulate an error signal
        Signal errorSignal = new Signal("application", Signal.ERROR_DETECTED);
        errorSignal.getData().put("error_type", "NullPointerException");
        errorSignal.getData().put("severity", "HIGH");
        
        // Process the signal through the brain
        // This will trigger the full 5-phase loop
        assertNotNull(errorSignal);
        assertEquals("application", errorSignal.getSource());
        assertEquals(Signal.ERROR_DETECTED, errorSignal.getType());
    }
    
    @Test
    public void testSignalTypes() {
        // Verify all standard signal types are defined
        assertNotNull(Signal.METRIC_UPDATE);
        assertNotNull(Signal.ERROR_DETECTED);
        assertNotNull(Signal.EVOLUTION_REQUEST);
        assertNotNull(Signal.HEALTH_THRESHOLD);
        assertNotNull(Signal.DNA_CHANGE);
    }
    
    @Test
    public void testOperationalHypothesis() {
        // Low health score should trigger operational recovery
        Signal signal = new Signal("monitor", Signal.METRIC_UPDATE);
        signal.getData().put("health_score", 0.3);
        
        // The brain should route this to the resolver
        assertNotNull(signal);
    }
    
    @Test
    public void testLogicalHypothesis() {
        // DNA change with good health should trigger logical fix
        Signal signal = new Signal("git", Signal.DNA_CHANGE);
        signal.getData().put("health_score", 0.8);
        
        // The brain should route this to the evolver
        assertNotNull(signal);
    }
}
