package com.blacklight.uac.resolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResolverTest {
    private Resolver resolver;
    
    @BeforeEach
    public void setUp() {
        resolver = new Resolver();
    }
    
    @Test
    public void testResolverInitialization() {
        assertNotNull(resolver);
    }
    
    @Test
    public void testExecuteRestartAction() {
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.RESTART,
            "test_service"
        );
        
        assertDoesNotThrow(() -> {
            var future = resolver.executeRecovery(action);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testExecuteScaleAction() {
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.SCALE,
            "api_service"
        );
        action.getParameters().put("scale_factor", 2);
        
        assertDoesNotThrow(() -> {
            var future = resolver.executeRecovery(action);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testExecuteRollbackAction() {
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.ROLLBACK,
            "deployment_v1.0.0"
        );
        
        assertDoesNotThrow(() -> {
            var future = resolver.executeRecovery(action);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testRecoveryActionCreation() {
        RecoveryAction action = new RecoveryAction(
            RecoveryAction.ActionType.RESTART,
            "service"
        );
        
        assertEquals(RecoveryAction.ActionType.RESTART, action.getActionType());
        assertEquals("service", action.getTargetResource());
        assertTrue(action.isIdempotent());
    }

    
    @Test
    public void testResolverShutdown() {
        assertDoesNotThrow(() -> resolver.shutdown());
    }
}

