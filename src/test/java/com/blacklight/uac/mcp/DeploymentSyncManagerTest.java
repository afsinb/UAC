package com.blacklight.uac.mcp;

import com.blacklight.uac.evolver.PRDeploymentGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentSyncManagerTest {

    private final PRDeploymentGate gate = PRDeploymentGate.getInstance();
    private DeploymentSyncManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void submitEventRejectedWhenPrNotMerged() {
        int pr = 91001;
        gate.register(pr, "pipe-91001", "fix/91001", "master");

        manager = new DeploymentSyncManager(event -> {
            // no-op
        }, 0);

        DeploymentEvent event = new DeploymentEvent(pr, "pipe-91001", "staging", "v1", 5);
        DeploymentSyncManager.SubmitResult result = manager.submitEvent(event);

        assertFalse(result.isAccepted());
        assertTrue(result.getMessage().contains("not yet merged"));
        assertEquals(0, manager.getQueueSize());
    }

    @Test
    void onlyOneDeploymentRunsAtATime() throws Exception {
        int pr1 = 91011;
        int pr2 = 91012;
        registerMerged(pr1, "pipe-91011");
        registerMerged(pr2, "pipe-91012");

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseAll = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        manager = new DeploymentSyncManager(event -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, now));
            if (event.getPrNumber() == pr1) {
                firstStarted.countDown();
            }
            // Hold both executions until test releases.
            releaseAll.await(2, TimeUnit.SECONDS);
            concurrent.decrementAndGet();
        }, 0);

        DeploymentSyncManager.SubmitResult r1 = manager.submitEvent(
                new DeploymentEvent(pr1, "pipe-91011", "staging", "v1", 5));
        DeploymentSyncManager.SubmitResult r2 = manager.submitEvent(
                new DeploymentEvent(pr2, "pipe-91012", "staging", "v2", 5));

        assertTrue(r1.isAccepted());
        assertTrue(r2.isAccepted());
        assertTrue(firstStarted.await(500, TimeUnit.MILLISECONDS));

        // While first is active, second should remain queued.
        assertTrue(manager.isDeploymentActive());
        assertEquals(1, manager.getQueueSize());

        releaseAll.countDown();
        waitUntil(() -> manager.getHistory(10).size() >= 2, Duration.ofSeconds(3));

        assertEquals(1, maxConcurrent.get(), "Deployments must never overlap");
        assertEquals(0, manager.getQueueSize());
    }

    @Test
    void queuedEventsAreConsolidatedForSamePipelineAndEnvironment() throws Exception {
        int prA = 91021;
        int prB = 91022;
        int prC = 91023;
        registerMerged(prA, "pipe-same");
        registerMerged(prB, "pipe-same");
        registerMerged(prC, "pipe-same");

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseAll = new CountDownLatch(1);

        manager = new DeploymentSyncManager(event -> {
            if (event.getPrNumber() == prA) {
                firstStarted.countDown();
            }
            releaseAll.await(2, TimeUnit.SECONDS);
        }, 0);

        DeploymentSyncManager.SubmitResult rA = manager.submitEvent(
                new DeploymentEvent(prA, "pipe-same", "staging", "vA", 5));
        assertTrue(rA.isAccepted());
        assertTrue(firstStarted.await(500, TimeUnit.MILLISECONDS));

        // B stays queued (A is active)
        DeploymentSyncManager.SubmitResult rB = manager.submitEvent(
                new DeploymentEvent(prB, "pipe-same", "staging", "vB", 5));
        assertTrue(rB.isAccepted());
        assertEquals(1, manager.getQueueSize());

        // C should consolidate B (same pipeline+environment, B still queued)
        DeploymentSyncManager.SubmitResult rC = manager.submitEvent(
                new DeploymentEvent(prC, "pipe-same", "staging", "vC", 5));
        assertTrue(rC.isAccepted());
        assertTrue(rC.getMessage().contains("consolidated"));
        assertEquals(1, manager.getQueueSize(), "Still one queued event after consolidation");
        assertEquals("vC", manager.getQueueSnapshot().get(0).getVersion(),
                "Latest event should replace older queued event after consolidation");

        releaseAll.countDown();
        waitUntil(() -> manager.getHistory(10).size() >= 2, Duration.ofSeconds(3));

        List<DeploymentEvent> history = manager.getHistory(10);
        assertEquals(2, history.stream().filter(e -> e.getStatus() == DeploymentEvent.Status.COMPLETED).count());
    }

    @Test
    void queuedEventCanBeCancelled() throws Exception {
        int prA = 91031;
        int prB = 91032;
        registerMerged(prA, "pipe-91031");
        registerMerged(prB, "pipe-91032");

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseAll = new CountDownLatch(1);

        manager = new DeploymentSyncManager(event -> {
            if (event.getPrNumber() == prA) {
                firstStarted.countDown();
            }
            releaseAll.await(2, TimeUnit.SECONDS);
        }, 0);

        manager.submitEvent(new DeploymentEvent(prA, "pipe-91031", "staging", "v1", 5));
        assertTrue(firstStarted.await(500, TimeUnit.MILLISECONDS));

        DeploymentSyncManager.SubmitResult queued = manager.submitEvent(
                new DeploymentEvent(prB, "pipe-91032", "staging", "v2", 5));
        assertTrue(queued.isAccepted());
        assertEquals(1, manager.getQueueSize());

        boolean cancelled = manager.cancelEvent(queued.getEventId());
        assertTrue(cancelled);
        assertEquals(0, manager.getQueueSize());

        releaseAll.countDown();
        waitUntil(() -> manager.getHistory(10).size() >= 2, Duration.ofSeconds(3));

        assertTrue(manager.getHistory(10).stream()
                .anyMatch(e -> e.getEventId().equals(queued.getEventId())
                        && e.getStatus() == DeploymentEvent.Status.CANCELLED));
    }

    private void registerMerged(int prNumber, String pipelineId) {
        gate.register(prNumber, pipelineId, "fix/" + pipelineId, "master");
        gate.approve(prNumber);
        gate.markMerging(prNumber);
        gate.markMerged(prNumber);
    }

    private static void waitUntil(Check condition, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.ok()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Condition not met within " + timeout);
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }
}

