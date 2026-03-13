package com.blacklight.uac.mcp;

import com.blacklight.uac.evolver.PRDeploymentGate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DeploymentSyncManager – Central coordinator that ensures:
 *
 *   1. ONLY ONE deployment runs at a time (global ReentrantLock slot).
 *   2. Deployment events are submitted asynchronously and queued – callers
 *      never block waiting for a previous deploy to finish.
 *   3. Events for the same {@code pipelineId + environment} submitted while
 *      an earlier event for that combination is still QUEUED are
 *      CONSOLIDATED: the newer event supersedes the older one so the system
 *      is deployed only once with the latest artefact.
 *   4. A configurable cooldown separates consecutive deployments to let the
 *      system stabilise before the next change lands.
 *   5. The PR gate (PRDeploymentGate) is re-verified at execution time,
 *      not only at submission time, guarding against race conditions.
 *
 * ┌────────────────────────────────────────────────────────────────┐
 * │  caller A  ──submitEvent──►  PriorityBlockingQueue             │
 * │  caller B  ──submitEvent──►       │   ← consolidation here     │
 * │  caller C  ──submitEvent──►       │                            │
 * │                                   ▼                            │
 * │              single-thread worker ──► acquireSlot ──► deploy   │
 * │                                           │                    │
 * │                             cooldown ◄────┘                    │
 * └────────────────────────────────────────────────────────────────┘
 *
 * Usage:
 * <pre>
 *   DeploymentEvent evt = new DeploymentEvent(prNumber, pipelineId,
 *                                             "production", "v1.2.3", 5);
 *   DeploymentSyncManager.getInstance().submitEvent(evt);
 * </pre>
 *
 * For testing, construct with the package-private constructor that accepts
 * a custom {@link DeploymentExecutor}, cooldownMs=0, and
 * consolidationWindowMs=0.
 */
public class DeploymentSyncManager {

    // ── ANSI colours ──────────────────────────────────────────────────────

    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    // ── Defaults ──────────────────────────────────────────────────────────

    /** Default cooldown for non-production environments (ms). */
    public static final long DEFAULT_COOLDOWN_MS             = 5_000L;
    /** Default cooldown for the "production" environment (ms). */
    public static final long PRODUCTION_COOLDOWN_MS          = 30_000L;
    /** History ring-buffer capacity. */
    private static final int  HISTORY_CAPACITY               = 100;

    // ── Singleton ─────────────────────────────────────────────────────────

    private static final DeploymentSyncManager INSTANCE = new DeploymentSyncManager();

    /** Returns the process-wide singleton. */
    public static DeploymentSyncManager getInstance() { return INSTANCE; }

    // ── Pluggable deployment executor ─────────────────────────────────────

    /**
     * Strategy interface for the actual deployment action.
     * Replace the default (no-op + log) with real infra calls in production.
     */
    @FunctionalInterface
    public interface DeploymentExecutor {
        void deploy(DeploymentEvent event) throws Exception;
    }

    /** Default executor: logs the action (real infra call plugged in externally). */
    private static final DeploymentExecutor DEFAULT_EXECUTOR = event ->
            System.out.println(GREEN + "[DeploymentSyncManager] ⚙  Executing deployment: "
                    + event.getPipelineId() + " @ " + event.getVersion()
                    + " → " + event.getEnvironment() + RESET);

    // ── Core state ────────────────────────────────────────────────────────

    /**
     * The ONE global deployment slot.
     * Only the worker thread that holds this lock may deploy.
     * Fair=true ensures FIFO acquisition when multiple workers compete
     * (though in practice only one worker thread exists).
     */
    private final ReentrantLock deploymentSlot = new ReentrantLock(true);

    /**
     * Priority-ordered event queue.
     * Lower {@link DeploymentEvent#getPriority()} value = processed first.
     * FIFO within same priority (enforced by submittedAt in compareTo).
     */
    private final PriorityBlockingQueue<DeploymentEvent> eventQueue =
            new PriorityBlockingQueue<>();

    /**
     * Protects the consolidation scan-remove-add sequence so that two
     * concurrent submitters cannot both "win" the consolidation check.
     */
    private final ReentrantLock consolidationLock = new ReentrantLock();

    /** The event that is currently being deployed; null when idle. */
    private volatile DeploymentEvent activeDeployment = null;

    /** Ring buffer of the last HISTORY_CAPACITY completed/failed/cancelled events. */
    private final Deque<DeploymentEvent> history = new ArrayDeque<>(HISTORY_CAPACITY);

    /** Single-threaded executor: serial queue processing guaranteed. */
    private final ExecutorService worker;

    private final DeploymentExecutor deploymentExecutor;
    private final long               cooldownMs;
    private final PRDeploymentGate   gate = PRDeploymentGate.getInstance();

    // ── Constructors ──────────────────────────────────────────────────────

    /** Singleton / production constructor. */
    private DeploymentSyncManager() {
        this(DEFAULT_EXECUTOR, DEFAULT_COOLDOWN_MS);
    }

    /**
     * Test / custom constructor.
     *
     * @param executor   custom deployment action
     * @param cooldownMs ms to wait between consecutive deployments (0 for tests)
     */
    public DeploymentSyncManager(DeploymentExecutor executor, long cooldownMs) {
        this.deploymentExecutor = executor;
        this.cooldownMs         = cooldownMs;
        this.worker             = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "deployment-sync-worker");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Result returned synchronously from {@link #submitEvent}.
     */
    public static class SubmitResult {
        private final boolean accepted;
        private final String  eventId;
        private final String  message;
        private final int     queueDepth;

        private SubmitResult(boolean accepted, String eventId, String message, int depth) {
            this.accepted   = accepted;
            this.eventId    = eventId;
            this.message    = message;
            this.queueDepth = depth;
        }

        public static SubmitResult accepted(String eventId, String message, int depth) {
            return new SubmitResult(true,  eventId, message, depth);
        }
        public static SubmitResult blocked(String reason) {
            return new SubmitResult(false, null, reason, 0);
        }

        public boolean isAccepted()   { return accepted; }
        public String  getEventId()   { return eventId; }
        public String  getMessage()   { return message; }
        public int     getQueueDepth(){ return queueDepth; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accepted",   accepted);
            m.put("eventId",    eventId);
            m.put("message",    message);
            m.put("queueDepth", queueDepth);
            return m;
        }
    }

    /**
     * Submit a deployment event.
     * Returns immediately (non-blocking).  The event will be processed by the
     * single background worker in priority+FIFO order.
     *
     * <p><b>Consolidation</b>: if a QUEUED event already exists for the same
     * {@code pipelineId + environment}, it is marked CONSOLIDATED and replaced
     * by {@code newEvent}.
     *
     * @param newEvent the deployment to request
     * @return a {@link SubmitResult} describing whether the event was accepted
     */
    public SubmitResult submitEvent(DeploymentEvent newEvent) {
        // ── 1. Fast-fail PR gate pre-check ────────────────────────────────
        if (!gate.isDeploymentAllowed(newEvent.getPrNumber())) {
            String reason = "PR #" + newEvent.getPrNumber()
                    + " is not yet merged – deployment event rejected.";
            System.out.println(RED + "[DeploymentSyncManager] 🚫 " + reason + RESET);
            return SubmitResult.blocked(reason);
        }

        // ── 2. Consolidation (atomic scan-remove-add) ─────────────────────
        consolidationLock.lock();
        try {
            Optional<DeploymentEvent> superseded = findConsolidationTarget(newEvent);
            superseded.ifPresent(old -> {
                eventQueue.remove(old);
                old.markConsolidated();
                // New event inherits the superseded event's ID + its own consolidation chain
                newEvent.addConsolidatedFrom(old.getEventId());
                newEvent.inheritConsolidatedFrom(old.getConsolidatedFrom());
                System.out.println(YELLOW + "[DeploymentSyncManager] 🔀 Consolidated "
                        + old.getEventId() + " → " + newEvent.getEventId()
                        + " [pipeline=" + newEvent.getPipelineId()
                        + ", env="  + newEvent.getEnvironment() + "]" + RESET);
            });

            eventQueue.add(newEvent);
        } finally {
            consolidationLock.unlock();
        }

        int depth = eventQueue.size() + (activeDeployment != null ? 1 : 0);
        System.out.println(CYAN + "[DeploymentSyncManager] 📬 Event queued: "
                + newEvent.getEventId() + " (queue depth=" + depth + ")" + RESET);

        // ── 3. Kick background worker ──────────────────────────────────────
        scheduleProcessing();

        String msg = newEvent.getConsolidatedFrom().isEmpty()
                ? "Deployment event accepted."
                : "Deployment event accepted (consolidated " + newEvent.getConsolidatedFrom().size()
                  + " earlier event(s)).";
        return SubmitResult.accepted(newEvent.getEventId(), msg, depth);
    }

    /**
     * Cancel a queued event by event ID.
     *
     * @return {@code true} if the event was found and cancelled
     */
    public boolean cancelEvent(String eventId) {
        consolidationLock.lock();
        try {
            for (DeploymentEvent e : eventQueue) {
                if (e.getEventId().equals(eventId)) {
                    eventQueue.remove(e);
                    e.markCancelled();
                    addToHistory(e);
                    System.out.println(YELLOW + "[DeploymentSyncManager] ❌ Cancelled: "
                            + eventId + RESET);
                    return true;
                }
            }
        } finally {
            consolidationLock.unlock();
        }
        System.out.println(YELLOW + "[DeploymentSyncManager] ⚠ Cancel failed – event not found: "
                + eventId + RESET);
        return false;
    }

    // ── Observability ─────────────────────────────────────────────────────

    /** Returns the currently active deployment, or empty if idle. */
    public Optional<DeploymentEvent> getActiveDeployment() {
        return Optional.ofNullable(activeDeployment);
    }

    /** Returns a snapshot of the current queue (sorted by priority+time). */
    public List<DeploymentEvent> getQueueSnapshot() {
        List<DeploymentEvent> snapshot = new ArrayList<>(eventQueue);
        Collections.sort(snapshot);
        return Collections.unmodifiableList(snapshot);
    }

    /** Returns the last {@code n} completed/failed/cancelled events. */
    public List<DeploymentEvent> getHistory(int n) {
        synchronized (history) {
            List<DeploymentEvent> list = new ArrayList<>(history);
            Collections.reverse(list); // newest first
            return Collections.unmodifiableList(list.subList(0, Math.min(n, list.size())));
        }
    }

    /** Returns {@code true} if a deployment is currently running. */
    public boolean isDeploymentActive() { return activeDeployment != null; }

    /** Number of events waiting in the queue. */
    public int getQueueSize() { return eventQueue.size(); }

    /** Orderly shutdown of the background worker. */
    public void shutdown() {
        worker.shutdown();
        System.out.println(CYAN + "[DeploymentSyncManager] 🛑 Shutdown requested." + RESET);
    }

    // ── Internal processing ───────────────────────────────────────────────

    private void scheduleProcessing() {
        worker.submit(this::drainQueue);
    }

    /**
     * Drains the event queue sequentially.
     * Runs on the single-threaded worker; concurrent scheduling results in
     * multiple no-op invocations once the queue is empty.
     */
    private void drainQueue() {
        DeploymentEvent event;
        while ((event = eventQueue.poll()) != null) {
            executeDeployment(event);
            if (event.getStatus() != DeploymentEvent.Status.FAILED
                    && event.getStatus() != DeploymentEvent.Status.CANCELLED) {
                applyCooldown(event);
            }
        }
    }

    /**
     * Execute a single deployment event.
     * Acquires the deployment slot, verifies the PR gate again, calls the
     * pluggable executor, then releases the slot.
     */
    private void executeDeployment(DeploymentEvent event) {
        deploymentSlot.lock();
        try {
            activeDeployment = event;
            event.markActive();

            System.out.println(CYAN
                    + "\n┌──────────────────────────────────────────────────────────────┐"
                    + "\n│  🚀 DEPLOYMENT STARTED                                        │"
                    + "\n│  event   : " + padRight(event.getEventId(), 50) + "│"
                    + "\n│  pipeline: " + padRight(event.getPipelineId(), 50) + "│"
                    + "\n│  env     : " + padRight(event.getEnvironment(), 50) + "│"
                    + "\n│  version : " + padRight(event.getVersion(), 50) + "│"
                    + "\n│  PR #    : " + padRight(String.valueOf(event.getPrNumber()), 50) + "│"
                    + "\n└──────────────────────────────────────────────────────────────┘"
                    + RESET);

            // ── Re-check PR gate at execution time ─────────────────────────
            if (!gate.isDeploymentAllowed(event.getPrNumber())) {
                String reason = "PR #" + event.getPrNumber()
                        + " gate closed at execution time (state changed since submission).";
                event.markFailed(reason);
                System.out.println(RED + "[DeploymentSyncManager] 🚫 " + reason + RESET);
                return;
            }

            // ── Run the actual deployment ───────────────────────────────────
            deploymentExecutor.deploy(event);
            event.markCompleted();
            gate.markDeployed(event.getPrNumber());

            System.out.println(GREEN
                    + "[DeploymentSyncManager] ✅ Deployment COMPLETED: "
                    + event.getEventId() + RESET);

        } catch (Exception ex) {
            event.markFailed(ex.getMessage());
            System.out.println(RED
                    + "[DeploymentSyncManager] ✗ Deployment FAILED: "
                    + event.getEventId() + " – " + ex.getMessage() + RESET);
        } finally {
            addToHistory(event);
            activeDeployment = null;
            deploymentSlot.unlock();
        }
    }

    /**
     * After each deployment (success or not), wait for a cooldown period
     * before the next one starts.  The cooldown is production-specific.
     */
    private void applyCooldown(DeploymentEvent event) {
        long ms = "production".equalsIgnoreCase(event.getEnvironment())
                ? PRODUCTION_COOLDOWN_MS : cooldownMs;
        if (ms <= 0) return;
        System.out.println(CYAN + "[DeploymentSyncManager] ⏳ Cooldown " + ms + " ms after "
                + event.getEventId() + RESET);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Consolidation helper ──────────────────────────────────────────────

    /**
     * Scans the queue for an existing QUEUED event with the same
     * {@code pipelineId + environment} as {@code candidate}.
     * Must be called with {@link #consolidationLock} held.
     */
    private Optional<DeploymentEvent> findConsolidationTarget(DeploymentEvent candidate) {
        for (DeploymentEvent queued : eventQueue) {
            if (queued.getStatus() == DeploymentEvent.Status.QUEUED
                    && queued.getPipelineId().equals(candidate.getPipelineId())
                    && queued.getEnvironment().equals(candidate.getEnvironment())) {
                return Optional.of(queued);
            }
        }
        return Optional.empty();
    }

    // ── History ───────────────────────────────────────────────────────────

    private void addToHistory(DeploymentEvent event) {
        synchronized (history) {
            if (history.size() >= HISTORY_CAPACITY) {
                history.pollFirst(); // drop oldest
            }
            history.addLast(event);
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
    }
}

