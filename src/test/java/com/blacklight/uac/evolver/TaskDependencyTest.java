package com.blacklight.uac.evolver;

import com.blacklight.uac.mcp.MergerMCPServer;
import com.blacklight.uac.mcp.MCPOrchestrator;
import com.blacklight.uac.mcp.MCPServer;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskDependencyTest – verifies the core rules implemented in developV2:
 *
 *  1. Deployment is BLOCKED unless the PR has been approved and merged.
 *  2. Two concurrent PRs trying to merge are serialised by MergerMCPServer.
 *  3. Conflicting PRs (PR number divisible by 7) are detected and offered
 *     conflict-resolution strategies.
 *  4. TaskDependencyGraph enforces the CODE_FIX → PR_RAISED → PR_APPROVED
 *     → PR_MERGED → DEPLOY chain.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TaskDependencyTest {

    // ── shared state ──────────────────────────────────────────────────────

    private static final PRDeploymentGate gate = PRDeploymentGate.getInstance();
    private static MCPOrchestrator orchestrator;

    @BeforeAll
    static void setUp() {
        orchestrator = new MCPOrchestrator();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 1. Dependency graph
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1a – DEPLOY is blocked until all dependencies are met")
    void deployBlockedWithoutPrMerged() {
        TaskDependencyGraph graph = new TaskDependencyGraph();
        graph.registerPipeline("test-pipeline-1");

        // Nothing completed yet – deploy must be blocked
        assertFalse(graph.canExecute("test-pipeline-1", TaskDependencyGraph.TaskNode.DEPLOY),
                "DEPLOY should be blocked when PR_MERGED is not complete");
    }

    @Test
    @Order(2)
    @DisplayName("1b – DEPLOY is allowed only after the full chain is completed")
    void deployAllowedAfterFullChain() {
        TaskDependencyGraph graph = new TaskDependencyGraph();
        graph.registerPipeline("test-pipeline-2");

        // Walk the full chain
        graph.markCompleted("test-pipeline-2", TaskDependencyGraph.TaskNode.CODE_FIX);
        assertFalse(graph.canExecute("test-pipeline-2", TaskDependencyGraph.TaskNode.DEPLOY));

        graph.markCompleted("test-pipeline-2", TaskDependencyGraph.TaskNode.PR_RAISED);
        assertFalse(graph.canExecute("test-pipeline-2", TaskDependencyGraph.TaskNode.DEPLOY));

        graph.markCompleted("test-pipeline-2", TaskDependencyGraph.TaskNode.PR_APPROVED);
        assertFalse(graph.canExecute("test-pipeline-2", TaskDependencyGraph.TaskNode.DEPLOY));

        graph.markCompleted("test-pipeline-2", TaskDependencyGraph.TaskNode.PR_MERGED);
        assertTrue(graph.canExecute("test-pipeline-2", TaskDependencyGraph.TaskNode.DEPLOY),
                "DEPLOY should be allowed after PR_MERGED");
    }

    @Test
    @Order(3)
    @DisplayName("1c – getReadyTasks returns only tasks whose dependencies are met")
    void readyTasksProgressCorrectly() {
        TaskDependencyGraph graph = new TaskDependencyGraph();
        graph.registerPipeline("test-pipeline-3");

        // Initially only CODE_FIX is ready
        assertEquals(java.util.Set.of(TaskDependencyGraph.TaskNode.CODE_FIX),
                graph.getReadyTasks("test-pipeline-3"));

        graph.markCompleted("test-pipeline-3", TaskDependencyGraph.TaskNode.CODE_FIX);

        // Now PR_RAISED should be ready
        assertTrue(graph.getReadyTasks("test-pipeline-3")
                .contains(TaskDependencyGraph.TaskNode.PR_RAISED));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. PRDeploymentGate – state machine
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("2a – Fresh PR is not deployment-eligible")
    void freshPrIsNotDeployable() {
        gate.register(100, "pipe-100", "fix/issue-100", "master");
        assertFalse(gate.isDeploymentAllowed(100));
    }

    @Test
    @Order(5)
    @DisplayName("2b – Deployment is blocked and throws before PR is merged")
    void deploymentBlockedExceptionThrown() {
        gate.register(101, "pipe-101", "fix/issue-101", "master");
        gate.approve(101); // approved but NOT merged

        assertThrows(PRDeploymentGate.DeploymentBlockedException.class,
                () -> gate.assertDeploymentAllowed(101),
                "Should throw because PR is only APPROVED, not MERGED");
    }

    @Test
    @Order(6)
    @DisplayName("2c – Deployment is allowed after PR is marked MERGED")
    void deploymentAllowedAfterMerge() {
        gate.register(102, "pipe-102", "fix/issue-102", "master");
        gate.approve(102);
        gate.markMerging(102);
        gate.markMerged(102);

        assertTrue(gate.isDeploymentAllowed(102));
        assertDoesNotThrow(() -> gate.assertDeploymentAllowed(102));
    }

    @Test
    @Order(7)
    @DisplayName("2d – Illegal state transitions are silently rejected")
    void illegalTransitionRejected() {
        gate.register(103, "pipe-103", "fix/issue-103", "master");

        // Cannot mark MERGED from PR_RAISED (must go through APPROVED → MERGING first)
        boolean result = gate.markMerged(103);
        assertFalse(result, "Marking merged from PR_RAISED should be rejected");
        assertEquals(PRLifecycleState.PR_RAISED, gate.findByPrNumber(103).get().getState());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. MergerMCPServer – queue and conflict detection
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("3a – queue_merge is rejected when PR is not APPROVED")
    void queueMergeRejectedForUnapprovedPr() {
        gate.register(200, "pipe-200", "fix/issue-200", "master");
        // State is PR_RAISED, not APPROVED

        Map<String, Object> result = orchestrator.queueMerge(200, "pipe-200",
                "fix/issue-200", "master", 5);

        assertTrue(result.containsKey("error"),
                "queue_merge should fail for unapproved PR");
    }

    @Test
    @Order(9)
    @DisplayName("3b – queue_merge succeeds for an APPROVED PR")
    void queueMergeSucceedsForApprovedPr() {
        gate.register(201, "pipe-201", "fix/issue-201", "master");
        gate.approve(201);

        Map<String, Object> result = orchestrator.queueMerge(201, "pipe-201",
                "fix/issue-201", "master", 5);

        assertFalse(result.containsKey("error"),
                "queue_merge should succeed for approved PR: " + result.get("error"));
        assertTrue((Boolean) result.get("queued"), "queued flag should be true");
    }

    @Test
    @Order(10)
    @DisplayName("3c – Duplicate queue_merge for same PR is rejected")
    void duplicateQueueMergeRejected() {
        gate.register(202, "pipe-202", "fix/issue-202", "master");
        gate.approve(202);

        orchestrator.queueMerge(202, "pipe-202", "fix/issue-202", "master", 5);
        Map<String, Object> secondAttempt = orchestrator.queueMerge(202, "pipe-202",
                "fix/issue-202", "master", 5);

        assertTrue(secondAttempt.containsKey("error"),
                "Second queue_merge for same PR should be rejected");
    }

    @Test
    @Order(11)
    @DisplayName("3d – Conflict is detected for PR number divisible by 7")
    void conflictDetectedForPr7() {
        Map<String, Object> result = orchestrator.checkMergeConflict(
                7, "fix/issue-7", "master");

        assertTrue((Boolean) result.get("hasConflict"),
                "PR #7 should be detected as conflicting");
        assertFalse(((java.util.List<?>) result.get("conflictingFiles")).isEmpty(),
                "conflictingFiles should be non-empty");
    }

    @Test
    @Order(12)
    @DisplayName("3e – No conflict for non-divisible-by-7 PR")
    void noConflictForCleanPr() {
        Map<String, Object> result = orchestrator.checkMergeConflict(
                10, "fix/issue-10", "master");

        assertFalse((Boolean) result.get("hasConflict"),
                "PR #10 should be clean");
    }

    @Test
    @Order(13)
    @DisplayName("3f – REBASE resolution strategy returns a result map")
    void rebaseResolutionReturnsResult() {
        Map<String, Object> result = orchestrator.resolveConflict(7, "REBASE");

        assertNotNull(result.get("resolved"), "resolved key must be present");
        assertEquals("REBASE", result.get("strategy"));
    }

    @Test
    @Order(14)
    @DisplayName("3g – MANUAL resolution strategy reports requiresManual=true")
    void manualResolutionRequiresManual() {
        Map<String, Object> result = orchestrator.resolveConflict(7, "MANUAL");

        assertEquals(false, result.get("resolved"));
        assertEquals(true, result.get("requiresManual"));
    }

    @Test
    @Order(15)
    @DisplayName("3h – get_queue_status returns queue metadata")
    void queueStatusReturnsMetadata() {
        Map<String, Object> status = orchestrator.getMergeQueueStatus();

        assertNotNull(status.get("queuedRequests"));
        assertNotNull(status.get("queueSize"));
        assertNotNull(status.get("processingEnabled"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. DeploymentMCPServer – PR gate via orchestrator
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("4a – check_pr_gate returns deploymentAllowed=false for unmerged PR")
    void prGateClosedForUnmergedPr() {
        gate.register(300, "pipe-300", "fix/issue-300", "master");
        gate.approve(300); // approved, NOT merged

        Map<String, Object> result = orchestrator.checkPrDeploymentGate(300);

        assertTrue(result.containsKey("error"),
                "gate check should report error/blocked for unmerged PR");
    }

    @Test
    @Order(17)
    @DisplayName("4b – check_pr_gate returns deploymentAllowed=true for merged PR")
    void prGateOpenForMergedPr() {
        gate.register(301, "pipe-301", "fix/issue-301", "master");
        gate.approve(301);
        gate.markMerging(301);
        gate.markMerged(301);

        Map<String, Object> result = orchestrator.checkPrDeploymentGate(301);

        assertFalse(result.containsKey("error"),
                "gate check should succeed for merged PR: " + result.get("error"));
        assertTrue((Boolean) result.get("deploymentAllowed"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5. End-to-end: full happy path via TaskDependencyGraph + PRDeploymentGate
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(18)
    @DisplayName("5 – Full pipeline: code fix → PR raised → approved → merged → deploy allowed")
    void fullPipelineHappyPath() {
        String pipelineId = "e2e-pipe-999";
        int prNumber = 999;

        // ── dependency graph ──
        TaskDependencyGraph graph = new TaskDependencyGraph();
        graph.registerPipeline(pipelineId);

        // ── deployment gate ──
        gate.register(prNumber, pipelineId, "fix/issue-999", "master");

        // Step 1: code fix
        graph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.CODE_FIX);
        assertFalse(graph.canExecute(pipelineId, TaskDependencyGraph.TaskNode.DEPLOY));

        // Step 2: PR raised
        graph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.PR_RAISED);
        assertFalse(gate.isDeploymentAllowed(prNumber));

        // Step 3: PR approved
        gate.approve(prNumber);
        graph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.PR_APPROVED);
        assertFalse(gate.isDeploymentAllowed(prNumber),
                "Approved but not yet merged – must still be blocked");

        // Step 4: PR merged
        gate.markMerging(prNumber);
        gate.markMerged(prNumber);
        graph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.PR_MERGED);

        // NOW deployment should be allowed
        assertTrue(gate.isDeploymentAllowed(prNumber));
        assertTrue(graph.canExecute(pipelineId, TaskDependencyGraph.TaskNode.DEPLOY));

        // Step 5: deploy
        graph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.DEPLOY);
        gate.markDeployed(prNumber);

        assertEquals(PRLifecycleState.DEPLOYED,
                gate.findByPrNumber(prNumber).get().getState());
        assertTrue(graph.isDeployed(pipelineId));
    }
}

