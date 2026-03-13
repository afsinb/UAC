package com.blacklight.uac.evolver;

/**
 * PRLifecycleState - Represents every state a Pull Request can be in.
 *
 * State machine:
 *
 *   CODE_FIX_COMMITTED
 *         │
 *         ▼
 *     PR_RAISED
 *         │
 *         ▼
 *  REVIEW_IN_PROGRESS ──► CHANGES_REQUESTED ──► PR_RAISED (re-review)
 *         │
 *         ▼
 *      APPROVED
 *         │
 *         ▼ (via MergerMCPServer – serialised, conflict-checked)
 *      MERGING
 *         │
 *    ┌────┴───────┐
 *    ▼            ▼
 * CONFLICT_DETECTED  MERGED
 *    │                │
 *    ▼                ▼
 * MERGE_ABORTED   DEPLOYED   ◄──  deployment is BLOCKED until here
 *
 *  CLOSED  ─ PR closed without merge
 */
public enum PRLifecycleState {

    /** A code fix has been committed locally but no PR opened yet. */
    CODE_FIX_COMMITTED,

    /** PR has been opened / raised against the target branch. */
    PR_RAISED,

    /** At least one reviewer has been requested or started reviewing. */
    REVIEW_IN_PROGRESS,

    /** At least one reviewer requested changes – deployment still BLOCKED. */
    CHANGES_REQUESTED,

    /** PR has received the required approvals – ready to enter merge queue. */
    APPROVED,

    /** PR is currently being merged by the MergerMCPServer (locked). */
    MERGING,

    /** Merge conflicts were detected; the MergerMCPServer is resolving them. */
    CONFLICT_DETECTED,

    /** Merge was aborted (un-resolvable conflict or operator decision). */
    MERGE_ABORTED,

    /** PR has been successfully merged into the target branch. */
    MERGED,

    /**
     * The artefact produced by this PR has been deployed.
     * This state is only reachable after {@link #MERGED}.
     */
    DEPLOYED,

    /** PR was closed without merging (e.g. abandoned, superseded). */
    CLOSED;

    // ─────────────────────────────────────────────────────────────────────
    // Guard helpers used by PRDeploymentGate and MergerMCPServer
    // ─────────────────────────────────────────────────────────────────────

    /** Returns {@code true} if the PR is in a terminal non-deployable state. */
    public boolean isBlocked() {
        return this == MERGE_ABORTED || this == CLOSED || this == CHANGES_REQUESTED;
    }

    /**
     * Returns {@code true} if deployment of the artefact produced by this PR
     * is permitted (i.e. the PR has been merged into the base branch).
     */
    public boolean isDeploymentAllowed() {
        return this == MERGED || this == DEPLOYED;
    }

    /**
     * Returns {@code true} if the PR is eligible to enter the merge queue
     * (approved but not yet in the queue / being merged / already merged).
     */
    public boolean isMergeEligible() {
        return this == APPROVED;
    }
}

