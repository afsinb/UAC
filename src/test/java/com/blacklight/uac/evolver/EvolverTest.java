package com.blacklight.uac.evolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EvolverTest {
    private Evolver evolver;
    
    @BeforeEach
    public void setUp() {
        evolver = new Evolver("/test/repo", "/test/sandbox");
    }
    
    @Test
    public void testEvolverInitialization() {
        assertNotNull(evolver);
    }
    
    @Test
    public void testExecuteCloneTask() {
        DevelopmentTask task = new DevelopmentTask("clone");
        
        assertDoesNotThrow(() -> {
            var future = evolver.executeDevelopmentCycle(task);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testExecuteReproduceTask() {
        DevelopmentTask task = new DevelopmentTask("reproduce");
        task.setSourceCode("test_code");
        
        assertDoesNotThrow(() -> {
            var future = evolver.executeDevelopmentCycle(task);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testExecutePatchTask() {
        DevelopmentTask task = new DevelopmentTask("patch");
        task.setSourceCode("test_code");
        
        assertDoesNotThrow(() -> {
            var future = evolver.executeDevelopmentCycle(task);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testExecuteVerifyTask() {
        DevelopmentTask task = new DevelopmentTask("verify");
        task.setPatchContent("fix: test patch");
        
        assertDoesNotThrow(() -> {
            var future = evolver.executeDevelopmentCycle(task);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testExecutePRTask() {
        DevelopmentTask task = new DevelopmentTask("pr");
        task.setVerified(true);
        task.setPatchContent("fix: test patch");
        
        assertDoesNotThrow(() -> {
            var future = evolver.executeDevelopmentCycle(task);
            assertNotNull(future);
        });
    }
    
    @Test
    public void testInvalidTaskType() {
        DevelopmentTask task = new DevelopmentTask("invalid_type");
        
        assertThrows(Exception.class, () -> evolver.executeDevelopmentCycle(task).get());
    }
    
    @Test
    public void testDevelopmentTaskCreation() {
        DevelopmentTask task = new DevelopmentTask("patch");
        
        assertEquals("patch", task.getTaskType());
        assertFalse(task.isVerified());
    }
}

