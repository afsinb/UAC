package com.blacklight.uac.core.coordinator;

import com.blacklight.uac.core.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class HypothesisEngineTest {
    
    private HypothesisEngine engine;
    
    @BeforeEach
    public void setUp() {
        engine = new HypothesisEngine();
    }
    
    @Test
    public void testOperationalIssueDetection() {
        // Low health score → Operational issue
        Map<String, Object> data = new HashMap<>();
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.3, "METRIC_ANOMALY", data
        );
        
        ContextSnapshot context = new ContextSnapshot(event, new ArrayList<>());
        IssueHypothesis hypothesis = engine.analyze(event, context);
        
        assertTrue(hypothesis.isOperational());
        assertFalse(hypothesis.isLogical());
    }
    
    @Test
    public void testLogicalIssueDetection() {
        // DNA anomaly with decent health → Logical issue
        Map<String, Object> data = new HashMap<>();
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.7, "DNA_ANOMALY", data
        );
        
        ContextSnapshot context = new ContextSnapshot(event, new ArrayList<>());
        IssueHypothesis hypothesis = engine.analyze(event, context);
        
        assertTrue(hypothesis.isLogical());
        assertFalse(hypothesis.isOperational());
    }
    
    @Test
    public void testConfidenceCalculation() {
        Map<String, Object> data = new HashMap<>();
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.2, "METRIC_ANOMALY", data
        );
        
        ContextSnapshot context = new ContextSnapshot(event, new ArrayList<>());
        IssueHypothesis hypothesis = engine.analyze(event, context);
        
        assertTrue(hypothesis.getConfidence() > 0.0);
        assertTrue(hypothesis.getConfidence() <= 1.0);
    }
    
    @Test
    public void testHypothesisReasoning() {
        Map<String, Object> data = new HashMap<>();
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.4, "METRIC_ANOMALY", data
        );
        
        ContextSnapshot context = new ContextSnapshot(event, new ArrayList<>());
        IssueHypothesis hypothesis = engine.analyze(event, context);
        
        assertNotNull(hypothesis.getReasoning());
        assertTrue(hypothesis.getReasoning().length() > 0);
    }
}

