package com.blacklight.uac.evolver;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRDeploymentGate - Enforces the rule:
 *   "No deployment may occur unless the PR has been APPROVED and MERGED into master."
 *
 * Acts as the authoritative registry of PR lifecycle states.  Every component
 * that wishes to deploy or advance a PR must consult (and update) this gate.
 *
 * Thread-safe; intended as a singleton shared by Evolver and DeploymentMCPServer.
 */
public class PRDeploymentGate {

    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    // ── Inner record ──────────────────────────────────────────────────────

    public static class PRRecord {
        private final int prNumber;
        private final String pipelineId;
        private final String branch;
        private final String targetBranch;
        private volatile PRLifecycleState state;
        private final Instant createdAt;
        private volatile Instant updatedAt;
        private final List<String> auditLog;

        public PRRecord(int prNumber, String pipelineId, String branch, String targetBranch) {
            this.prNumber     = prNumber;
            this.pipelineId   = pipelineId;
            this.branch       = branch;
            this.targetBranch = targetBranch;
            this.state        = PRLifecycleState.PR_RAISED;
            this.createdAt    = Instant.now();
            this.updatedAt    = Instant.now();
            this.auditLog     = Collections.synchronizedList(new ArrayList<>());
            auditLog.add(Instant.now() + " → PR_RAISED");
        }

        // Getters
        public int getPrNumber()           { return prNumber; }
        public String getPipelineId()      { return pipelineId; }
        public String getBranch()          { return branch; }
        public String getTargetBranch()    { return targetBranch; }
        public PRLifecycleState getState() { return state; }
        public Instant getCreatedAt()      { return createdAt; }
        public Instant getUpdatedAt()      { return updatedAt; }
        public List<String> getAuditLog()  { return Collections.unmodifiableList(auditLog); }

        void transition(PRLifecycleState next) {
            this.state     = next;
            this.updatedAt = Instant.now();
            auditLog.add(updatedAt + " → " + next);
        }

        @Override
        public String toString() {
            return String.format("PR #%d [branch=%s → %s, state=%s]",
                    prNumber, branch, targetBranch, state);
        }
    }

    // ── Singleton ─────────────────────────────────────────────────────────

    private static final PRDeploymentGate INSTANCE = new PRDeploymentGate();

    public static PRDeploymentGate getInstance() { return INSTANCE; }

    // ── State ─────────────────────────────────────────────────────────────

    /** prNumber → record */
    private final Map<Integer, PRRecord> registry = new ConcurrentHashMap<>();
    /** pipelineId → prNumber (1-to-1 for our purposes) */
    private final Map<String, Integer>   pipelineIndex = new ConcurrentHashMap<>();

    private PRDeploymentGate() {}

    // ── Registration ──────────────────────────────────────────────────────

    /**
     * Register a newly raised PR.
     *
     * @param prNumber   GitHub PR number (use -1 if not yet known, update later)
     * @param pipelineId the TaskDependencyGraph pipeline this PR belongs to
     * @param branch     source branch
     * @param targetBranch  base branch (e.g. "master")
     */
    public PRRecord register(int prNumber, String pipelineId, String branch, String targetBranch) {
        PRRecord record = new PRRecord(prNumber, pipelineId, branch, targetBranch);
        registry.put(prNumber, record);
        pipelineIndex.put(pipelineId, prNumber);
        System.out.println(CYAN + "[PRDeploymentGate] 📋 Registered " + record + RESET);
        return record;
    }

    // ── State transitions ─────────────────────────────────────────────────

    /**
     * Advance a PR to the REVIEW_IN_PROGRESS state.
     */
    public boolean startReview(int prNumber) {
        return transition(prNumber, PRLifecycleState.REVIEW_IN_PROGRESS,
                EnumSet.of(PRLifecycleState.PR_RAISED));
    }

    /**
     * Mark a PR as APPROVED.  After this call the PR is eligible to enter
     * the MergerMCPServer queue.
     */
    public boolean approve(int prNumber) {
        return transition(prNumber, PRLifecycleState.APPROVED,
                EnumSet.of(PRLifecycleState.PR_RAISED,
                           PRLifecycleState.REVIEW_IN_PROGRESS,
                           PRLifecycleState.CHANGES_REQUESTED));
    }

    /**
     * Mark a PR as having been merged into the target branch.
     * This is the only transition that makes {@link #isDeploymentAllowed} return true.
     */
    public boolean markMerged(int prNumber) {
        boolean ok = transition(prNumber, PRLifecycleState.MERGED,
                EnumSet.of(PRLifecycleState.APPROVED, PRLifecycleState.MERGING));
        if (ok) {
            System.out.println(GREEN + "[PRDeploymentGate] ✅ PR #" + prNumber
                    + " merged – deployment gate OPEN" + RESET);
        }
        return ok;
    }

    /** Mark MERGING (called by MergerMCPServer when it starts the merge). */
    public boolean markMerging(int prNumber) {
        return transition(prNumber, PRLifecycleState.MERGING,
                EnumSet.of(PRLifecycleState.APPROVED));
    }

    /** Mark conflict detected. */
    public boolean markConflict(int prNumber) {
        return transition(prNumber, PRLifecycleState.CONFLICT_DETECTED,
                EnumSet.of(PRLifecycleState.MERGING));
    }

    /** Mark merge aborted. */
    public boolean markAborted(int prNumber) {
        return transition(prNumber, PRLifecycleState.MERGE_ABORTED,
                EnumSet.of(PRLifecycleState.MERGING,
                           PRLifecycleState.CONFLICT_DETECTED));
    }

    /** Mark deployed – called after a successful deployment. */
    public boolean markDeployed(int prNumber) {
        return transition(prNumber, PRLifecycleState.DEPLOYED,
                EnumSet.of(PRLifecycleState.MERGED));
    }

    /** Mark changes requested (reviewer asked for changes). */
    public boolean requestChanges(int prNumber) {
        return transition(prNumber, PRLifecycleState.CHANGES_REQUESTED,
                EnumSet.of(PRLifecycleState.PR_RAISED,
                           PRLifecycleState.REVIEW_IN_PROGRESS));
    }

    // ── Gate check ────────────────────────────────────────────────────────

    /**
     * The primary guard.  Returns {@code true} only if the PR has been
     * MERGED (or DEPLOYED) into the target branch.
     *
     * @throws DeploymentBlockedException if the PR is not yet merged
     */
    public boolean assertDeploymentAllowed(int prNumber) {
        PRRecord record = registry.get(prNumber);
        if (record == null) {
            throw new DeploymentBlockedException(
                    "PR #" + prNumber + " is not registered with the deployment gate.");
        }
        if (!record.getState().isDeploymentAllowed()) {
            String reason = buildBlockReason(record);
            System.out.println(RED + "[PRDeploymentGate] 🚫 Deployment BLOCKED for PR #"
                    + prNumber + ": " + reason + RESET);
            throw new DeploymentBlockedException(reason);
        }
        System.out.println(GREEN + "[PRDeploymentGate] 🟢 Deployment ALLOWED for PR #"
                + prNumber + RESET);
        return true;
    }

    /**
     * Non-throwing variant – returns {@code true} if deployment is allowed.
     */
    public boolean isDeploymentAllowed(int prNumber) {
        PRRecord record = registry.get(prNumber);
        return record != null && record.getState().isDeploymentAllowed();
    }

    /** Look up a record by pipeline ID. */
    public Optional<PRRecord> findByPipeline(String pipelineId) {
        Integer prNumber = pipelineIndex.get(pipelineId);
        return prNumber == null ? Optional.empty() : Optional.ofNullable(registry.get(prNumber));
    }

    /** Look up a record by PR number. */
    public Optional<PRRecord> findByPrNumber(int prNumber) {
        return Optional.ofNullable(registry.get(prNumber));
    }

    /** Returns all currently registered PR records. */
    public Collection<PRRecord> allRecords() {
        return Collections.unmodifiableCollection(registry.values());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private boolean transition(int prNumber, PRLifecycleState next, Set<PRLifecycleState> allowedFrom) {
        PRRecord record = registry.get(prNumber);
        if (record == null) {
            System.out.println(RED + "[PRDeploymentGate] ⚠ PR #" + prNumber + " not found" + RESET);
            return false;
        }
        if (!allowedFrom.contains(record.getState())) {
            System.out.println(YELLOW + "[PRDeploymentGate] ⚠ Illegal transition for PR #" + prNumber
                    + ": " + record.getState() + " → " + next
                    + " (allowed from: " + allowedFrom + ")" + RESET);
            return false;
        }
        record.transition(next);
        System.out.println(CYAN + "[PRDeploymentGate] 🔄 PR #" + prNumber
                + " state: " + next + RESET);
        return true;
    }

    private String buildBlockReason(PRRecord record) {
        return switch (record.getState()) {
            case CODE_FIX_COMMITTED -> "PR has not been raised yet.";
            case PR_RAISED          -> "PR is awaiting review/approval.";
            case REVIEW_IN_PROGRESS -> "PR is still under review.";
            case CHANGES_REQUESTED  -> "Reviewer requested changes – please address them.";
            case APPROVED           -> "PR is approved but not yet merged.";
            case MERGING            -> "PR is currently being merged – please wait.";
            case CONFLICT_DETECTED  -> "Merge conflict detected – resolve before deploying.";
            case MERGE_ABORTED      -> "Merge was aborted – re-raise or fix the PR.";
            case CLOSED             -> "PR was closed without merging.";
            default                 -> "PR state (" + record.getState() + ") does not permit deployment.";
        };
    }

    // ── Checked exception ─────────────────────────────────────────────────

    /**
     * Thrown when a deployment is attempted before the PR is approved and merged.
     */
    public static class DeploymentBlockedException extends RuntimeException {
        public DeploymentBlockedException(String message) {
            super(message);
        }
    }
}

