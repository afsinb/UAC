package com.blacklight.uac.strategies.recovery;

import com.blacklight.uac.core.events.AnomalyDetectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class RecoveryStrategyTest {
    
    private ScalingRecoveryStrategy scalingStrategy;
    private RestartRecoveryStrategy restartStrategy;
    
    @BeforeEach
    public void setUp() {
        scalingStrategy = new ScalingRecoveryStrategy();
        restartStrategy = new RestartRecoveryStrategy();
    }
    
    @Test
    public void testScalingStrategyCanHandle() {
        Map<String, Object> data = new HashMap<>();
        data.put("cpu_usage", 85.0);
        data.put("service", "api-server");
        
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.3, "METRIC_ANOMALY", data
        );
        
        assertTrue(scalingStrategy.canHandle(event));
    }
    
    @Test
    public void testScalingStrategyExecution() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("cpu_usage", 85.0);
        data.put("service", "api-server");
        
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.3, "METRIC_ANOMALY", data
        );
        
        assertTrue(scalingStrategy.execute(event));
    }
    
    @Test
    public void testRestartStrategyCanHandle() {
        Map<String, Object> data = new HashMap<>();
        data.put("error_message", "Connection timeout");
        data.put("service", "db-service");
        
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.2, "LOG_ANOMALY", data
        );
        
        assertTrue(restartStrategy.canHandle(event));
    }
    
    @Test
    public void testRestartStrategyExecution() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("error_message", "Connection refused");
        data.put("service", "db-service");
        
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
            "observer", 0.2, "LOG_ANOMALY", data
        );
        
        assertTrue(restartStrategy.execute(event));
    }
    
    @Test
    public void testIdempotency() {
        assertTrue(scalingStrategy.isIdempotent());
        assertTrue(restartStrategy.isIdempotent());
    }
    
    @Test
    public void testStrategyPriority() {
        assertTrue(scalingStrategy.getPriority() > 0);
        assertTrue(restartStrategy.getPriority() > 0);
        assertTrue(scalingStrategy.getPriority() > restartStrategy.getPriority());
    }
}

