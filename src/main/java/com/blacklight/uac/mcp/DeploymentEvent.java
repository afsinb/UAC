package com.blacklight.uac.mcp;

import java.time.Instant;
import java.util.*;

/**
 * DeploymentEvent – an immutable-identity, mutable-status record of one deployment request.
 *
 * Lifecycle:
 *   QUEUED → ACTIVE → COMPLETED
 *                  ↘ FAILED
 *         ↘ CANCELLED      (operator cancels before it starts)
 *         ↘ CONSOLIDATED   (superseded by a newer event for the same pipeline+environment)
 */
public class DeploymentEvent implements Comparable<DeploymentEvent> {

    // ── Status enum ───────────────────────────────────────────────────────

    public enum Status {
        /** Waiting in the DeploymentSyncManager queue. */
        QUEUED,
        /** Lock acquired – deployment is running right now. */
        ACTIVE,
        /** Deployment finished successfully. */
        COMPLETED,
        /** Deployment threw an exception or was blocked by the PR gate at run-time. */
        FAILED,
        /** Cancelled by an operator before it could start. */
        CANCELLED,
        /**
         * A newer event for the same pipeline+environment was submitted while this
         * event was still queued.  The newer event supersedes this one; this event
         * is removed from the queue and its ID is recorded in the winner's
         * {@link #getConsolidatedFrom()} list.
         */
        CONSOLIDATED
    }

    // ── Identity (immutable) ──────────────────────────────────────────────

    private final String  eventId;
    private final int     prNumber;
    private final String  pipelineId;
    private final String  environment;  // "production" | "staging" | "dev"
    private final String  version;      // commit SHA / artifact version
    private final int     priority;     // lower number = processed first
    private final Instant submittedAt;

    // ── Mutable state ─────────────────────────────────────────────────────

    private volatile Status  status      = Status.QUEUED;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String  failureReason;

    /**
     * Event IDs that were consolidated (superseded) into this event.
     * Non-empty when this event was submitted while a prior event for the
     * same pipeline+environment was still queued.
     */
    private final List<String> consolidatedFrom =
            Collections.synchronizedList(new ArrayList<>());

    // ── Constructor ───────────────────────────────────────────────────────

    public DeploymentEvent(int prNumber, String pipelineId, String environment,
                           String version, int priority) {
        this.eventId     = "DEP-" + pipelineId.replaceAll("[^a-zA-Z0-9]", "-")
                           + "-" + System.currentTimeMillis()
                           + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.prNumber    = prNumber;
        this.pipelineId  = pipelineId;
        this.environment = environment;
        this.version     = version != null ? version : "latest";
        this.priority    = priority;
        this.submittedAt = Instant.now();
    }

    // ── State transitions (package-private – used by DeploymentSyncManager) ─

    void markActive() {
        this.status    = Status.ACTIVE;
        this.startedAt = Instant.now();
    }

    void markCompleted() {
        this.status      = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    void markFailed(String reason) {
        this.status        = Status.FAILED;
        this.completedAt   = Instant.now();
        this.failureReason = reason;
    }

    void markCancelled() {
        this.status      = Status.CANCELLED;
        this.completedAt = Instant.now();
    }

    void markConsolidated() {
        this.status = Status.CONSOLIDATED;
    }

    void addConsolidatedFrom(String supersededEventId) {
        consolidatedFrom.add(supersededEventId);
    }

    void inheritConsolidatedFrom(List<String> ids) {
        consolidatedFrom.addAll(ids);
    }

    // ── Comparator: priority first, then submission time ──────────────────

    @Override
    public int compareTo(DeploymentEvent other) {
        int cmp = Integer.compare(this.priority, other.priority);
        return cmp != 0 ? cmp : this.submittedAt.compareTo(other.submittedAt);
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String  getEventId()             { return eventId; }
    public int     getPrNumber()            { return prNumber; }
    public String  getPipelineId()          { return pipelineId; }
    public String  getEnvironment()         { return environment; }
    public String  getVersion()             { return version; }
    public int     getPriority()            { return priority; }
    public Instant getSubmittedAt()         { return submittedAt; }
    public Status  getStatus()             { return status; }
    public Instant getStartedAt()          { return startedAt; }
    public Instant getCompletedAt()        { return completedAt; }
    public String  getFailureReason()      { return failureReason; }
    public List<String> getConsolidatedFrom() {
        return Collections.unmodifiableList(consolidatedFrom);
    }

    /** Convenience: is this event in a terminal state? */
    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.FAILED
                || status == Status.CANCELLED || status == Status.CONSOLIDATED;
    }

    /** Snapshot as a plain map (for MCP responses). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId",          eventId);
        m.put("prNumber",         prNumber);
        m.put("pipelineId",       pipelineId);
        m.put("environment",      environment);
        m.put("version",          version);
        m.put("priority",         priority);
        m.put("status",           status.name());
        m.put("submittedAt",      submittedAt.toString());
        m.put("startedAt",        startedAt   != null ? startedAt.toString()   : null);
        m.put("completedAt",      completedAt != null ? completedAt.toString() : null);
        m.put("failureReason",    failureReason);
        m.put("consolidatedFrom", new ArrayList<>(consolidatedFrom));
        return m;
    }

    @Override
    public String toString() {
        return String.format("DeploymentEvent{id=%s, pr=#%d, pipeline=%s, env=%s, status=%s}",
                eventId, prNumber, pipelineId, environment, status);
    }
}
