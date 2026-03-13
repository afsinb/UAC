package com.blacklight.uac.mcp;

import com.blacklight.uac.evolver.PRDeploymentGate;
import com.blacklight.uac.evolver.PRLifecycleState;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MergerMCPServer - MCP server that serialises concurrent PR merges
 * and handles merge-conflict resolution.
 *
 * Problem it solves:
 *   Two (or more) PRs may receive approval at the same time and both attempt
 *   to merge into master concurrently.  Without serialisation, the second
 *   merge can silently overwrite changes from the first, or GitHub rejects
 *   it with a "merge conflict" error.
 *
 * Solution:
 *   • A priority-ordered merge queue ensures only ONE PR is merged at a time.
 *   • After each merge, the server re-checks whether remaining queued PRs
 *     still apply cleanly; conflicting ones are flagged and offered
 *     auto-resolution strategies (rebase, re-sync, or manual).
 *   • All operations are reported back through the standard MCPResponse so
 *     the MCPOrchestrator and Brain can react accordingly.
 *
 * Capabilities:
 *   queue_merge        – enqueue a PR for merging
 *   process_queue      – trigger processing of the next PR in the queue
 *   check_conflict     – check whether a PR has conflicts with current master
 *   resolve_conflict   – attempt automated conflict resolution
 *   get_queue_status   – inspect the current merge queue
 *   abort_merge        – remove a PR from the queue or cancel an active merge
 */
public class MergerMCPServer implements MCPServer {

    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    // ── Merge-request model ───────────────────────────────────────────────

    public enum ConflictResolutionStrategy {
        /** Rebase the PR branch on top of the current master. */
        REBASE,
        /** Accept ours (master) for all conflicting hunks. */
        ACCEPT_OURS,
        /** Accept theirs (feature branch) for all conflicting hunks. */
        ACCEPT_THEIRS,
        /** Requires human intervention – system cannot auto-resolve. */
        MANUAL
    }

    public static class MergeRequest implements Comparable<MergeRequest> {
        private final int prNumber;
        private final String pipelineId;
        private final String branch;
        private final String targetBranch;
        private final int priority;          // lower = higher priority
        private final Instant queuedAt;
        private volatile String status;      // QUEUED | MERGING | MERGED | CONFLICT | ABORTED

        public MergeRequest(int prNumber, String pipelineId, String branch,
                            String targetBranch, int priority) {
            this.prNumber     = prNumber;
            this.pipelineId   = pipelineId;
            this.branch       = branch;
            this.targetBranch = targetBranch;
            this.priority     = priority;
            this.queuedAt     = Instant.now();
            this.status       = "QUEUED";
        }

        public int    getPrNumber()     { return prNumber; }
        public String getPipelineId()   { return pipelineId; }
        public String getBranch()       { return branch; }
        public String getTargetBranch() { return targetBranch; }
        public int    getPriority()     { return priority; }
        public Instant getQueuedAt()   { return queuedAt; }
        public String getStatus()      { return status; }
        public void   setStatus(String s) { this.status = s; }

        @Override
        public int compareTo(MergeRequest other) {
            int cmp = Integer.compare(this.priority, other.priority);
            return cmp != 0 ? cmp : this.queuedAt.compareTo(other.queuedAt);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prNumber",     prNumber);
            m.put("pipelineId",   pipelineId);
            m.put("branch",       branch);
            m.put("targetBranch", targetBranch);
            m.put("priority",     priority);
            m.put("queuedAt",     queuedAt.toString());
            m.put("status",       status);
            return m;
        }
    }

    // ── Internal state ────────────────────────────────────────────────────

    /** Serialises all merge operations – only one at a time. */
    private final ReentrantLock mergeLock = new ReentrantLock(true /* fair */);

    /** Priority queue: lowest priority-number first, then FIFO. */
    private final PriorityBlockingQueue<MergeRequest> mergeQueue =
            new PriorityBlockingQueue<>();

    /** History of completed / aborted merge requests for audit. */
    private final List<MergeRequest> mergeHistory =
            Collections.synchronizedList(new ArrayList<>());

    /** Currently-active merge request (null when idle). */
    private volatile MergeRequest activeMerge = null;

    /** Background executor for async queue processing. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "merger-mcp-worker");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean processingEnabled = new AtomicBoolean(true);

    private final PRDeploymentGate gate = PRDeploymentGate.getInstance();

    // ── MCPServer interface ───────────────────────────────────────────────

    @Override
    public String getName() { return "Merger"; }

    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
                "queue_merge",
                "process_queue",
                "check_conflict",
                "resolve_conflict",
                "get_queue_status",
                "abort_merge"
        );
    }

    @Override
    public MCPServer.MCPResponse handleRequest(MCPServer.MCPRequest request) {
        return switch (request.getMethod()) {
            case "queue_merge"      -> queueMerge(request);
            case "process_queue"    -> processQueue(request);
            case "check_conflict"   -> checkConflict(request);
            case "resolve_conflict" -> resolveConflict(request);
            case "get_queue_status" -> getQueueStatus(request);
            case "abort_merge"      -> abortMerge(request);
            default -> MCPServer.MCPResponse.error(request.getId(),
                    "Unknown method: " + request.getMethod());
        };
    }

    @Override
    public boolean isHealthy() { return processingEnabled.get(); }

    // ── Handler implementations ───────────────────────────────────────────

    /**
     * queue_merge – add a PR to the serialised merge queue.
     *
     * Required params: prNumber (int), pipelineId (String), branch (String),
     *                  targetBranch (String)
     * Optional params: priority (int, default 5 – lower = sooner)
     */
    private MCPServer.MCPResponse queueMerge(MCPServer.MCPRequest request) {
        Integer prNumber = request.getIntParam("prNumber");
        String pipelineId  = request.getStringParam("pipelineId");
        String branch      = request.getStringParam("branch");
        String targetBranch = request.getStringParam("targetBranch");

        if (prNumber == null || pipelineId == null || branch == null || targetBranch == null) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "Missing required params: prNumber, pipelineId, branch, targetBranch");
        }

        // Verify the PR is in APPROVED state before queuing
        Optional<PRDeploymentGate.PRRecord> optRecord = gate.findByPrNumber(prNumber);
        if (optRecord.isEmpty()) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "PR #" + prNumber + " is not registered with PRDeploymentGate.");
        }
        PRDeploymentGate.PRRecord record = optRecord.get();
        if (!record.getState().isMergeEligible()) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "PR #" + prNumber + " is not approved (current state: "
                            + record.getState() + "). Merge requires APPROVED state.");
        }

        // Check for duplicate
        boolean alreadyQueued = mergeQueue.stream()
                .anyMatch(r -> r.getPrNumber() == prNumber)
                || (activeMerge != null && activeMerge.getPrNumber() == prNumber);
        if (alreadyQueued) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "PR #" + prNumber + " is already queued or being merged.");
        }

        int priority = Objects.requireNonNullElse(request.getIntParam("priority"), 5);
        MergeRequest mr = new MergeRequest(prNumber, pipelineId, branch, targetBranch, priority);
        mergeQueue.add(mr);

        System.out.println(CYAN + "[MergerMCPServer] 📥 PR #" + prNumber
                + " queued for merge (priority=" + priority
                + ", queue size=" + mergeQueue.size() + ")" + RESET);

        // Kick off async processing
        scheduleProcessing();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queued",    true);
        result.put("prNumber",  prNumber);
        result.put("queueSize", mergeQueue.size() + (activeMerge != null ? 1 : 0));
        result.put("message",   "PR #" + prNumber + " added to merge queue (priority " + priority + ")");
        return MCPServer.MCPResponse.success(request.getId(), result);
    }

    /**
     * process_queue – synchronously process the next item in the queue (for testing / manual trigger).
     */
    private MCPServer.MCPResponse processQueue(MCPServer.MCPRequest request) {
        if (!processingEnabled.get()) {
            return MCPServer.MCPResponse.error(request.getId(), "Merger is currently disabled.");
        }
        MergeRequest next = mergeQueue.peek();
        if (next == null) {
            return MCPServer.MCPResponse.success(request.getId(),
                    Map.of("message", "Merge queue is empty."));
        }
        Map<String, Object> outcome = doMerge(next);
        return MCPServer.MCPResponse.success(request.getId(), outcome);
    }

    /**
     * check_conflict – simulate / detect whether a PR branch conflicts with master.
     *
     * Required params: prNumber (int), branch (String), targetBranch (String)
     */
    private MCPServer.MCPResponse checkConflict(MCPServer.MCPRequest request) {
        Integer prNumber = request.getIntParam("prNumber");
        String branch       = request.getStringParam("branch");
        String targetBranch = request.getStringParam("targetBranch");

        if (prNumber == null || branch == null || targetBranch == null) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "Missing required params: prNumber, branch, targetBranch");
        }

        boolean hasConflict = detectConflict(prNumber, branch, targetBranch);
        List<String> conflictingFiles = hasConflict
                ? simulateConflictingFiles(branch) : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prNumber",        prNumber);
        result.put("branch",          branch);
        result.put("targetBranch",    targetBranch);
        result.put("hasConflict",     hasConflict);
        result.put("conflictingFiles", conflictingFiles);

        if (hasConflict) {
            result.put("suggestedStrategies", suggestResolutionStrategies(conflictingFiles));
            System.out.println(YELLOW + "[MergerMCPServer] ⚠ Conflict detected for PR #"
                    + prNumber + " in files: " + conflictingFiles + RESET);
        } else {
            System.out.println(GREEN + "[MergerMCPServer] ✅ No conflicts for PR #"
                    + prNumber + RESET);
        }

        return MCPServer.MCPResponse.success(request.getId(), result);
    }

    /**
     * resolve_conflict – attempt automated conflict resolution.
     *
     * Required params: prNumber (int), strategy (String – one of REBASE, ACCEPT_OURS, ACCEPT_THEIRS, MANUAL)
     */
    private MCPServer.MCPResponse resolveConflict(MCPServer.MCPRequest request) {
        Integer prNumber = request.getIntParam("prNumber");
        String strategyStr = request.getStringParam("strategy");

        if (prNumber == null || strategyStr == null) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "Missing required params: prNumber, strategy");
        }

        ConflictResolutionStrategy strategy;
        try {
            strategy = ConflictResolutionStrategy.valueOf(strategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "Unknown strategy: " + strategyStr + ". Valid: "
                            + Arrays.toString(ConflictResolutionStrategy.values()));
        }

        Map<String, Object> result = applyResolutionStrategy(prNumber, strategy);
        return MCPServer.MCPResponse.success(request.getId(), result);
    }

    /**
     * get_queue_status – return the current state of the merge queue.
     */
    private MCPServer.MCPResponse getQueueStatus(MCPServer.MCPRequest request) {
        List<Map<String, Object>> queued = mergeQueue.stream()
                .sorted()
                .map(MergeRequest::toMap)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeMerge",    activeMerge != null ? activeMerge.toMap() : null);
        result.put("queuedRequests", queued);
        result.put("queueSize",      queued.size());
        result.put("historySize",    mergeHistory.size());
        result.put("processingEnabled", processingEnabled.get());

        return MCPServer.MCPResponse.success(request.getId(), result);
    }

    /**
     * abort_merge – remove a PR from the queue, or abort an active merge.
     *
     * Required params: prNumber (int)
     * Optional params: reason (String)
     */
    private MCPServer.MCPResponse abortMerge(MCPServer.MCPRequest request) {
        Integer prNumber = request.getIntParam("prNumber");
        if (prNumber == null) {
            return MCPServer.MCPResponse.error(request.getId(), "Missing required param: prNumber");
        }
        String reason = Objects.requireNonNullElse(request.getStringParam("reason"), "Operator aborted");

        // Try to remove from queue first
        boolean removed = mergeQueue.removeIf(r -> r.getPrNumber() == prNumber);

        if (!removed && activeMerge != null && activeMerge.getPrNumber() == prNumber) {
            activeMerge.setStatus("ABORTED");
            gate.markAborted(prNumber);
            mergeHistory.add(activeMerge);
            activeMerge = null;
            removed = true;
            System.out.println(RED + "[MergerMCPServer] 🛑 Active merge for PR #"
                    + prNumber + " aborted: " + reason + RESET);
        }

        if (!removed) {
            return MCPServer.MCPResponse.error(request.getId(),
                    "PR #" + prNumber + " not found in queue or active merge.");
        }

        gate.markAborted(prNumber);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aborted",  true);
        result.put("prNumber", prNumber);
        result.put("reason",   reason);
        return MCPServer.MCPResponse.success(request.getId(), result);
    }

    // ── Core merge logic ──────────────────────────────────────────────────

    /**
     * Perform the actual merge for {@code request}.
     * This method acquires the global merge lock so no two PRs are ever
     * merged simultaneously.
     */
    private Map<String, Object> doMerge(MergeRequest mr) {
        // Remove from queue (might already be polled)
        mergeQueue.remove(mr);
        activeMerge = mr;
        mr.setStatus("MERGING");

        System.out.println(CYAN + "[MergerMCPServer] 🔀 Starting merge for PR #"
                + mr.getPrNumber() + " (" + mr.getBranch() + " → " + mr.getTargetBranch() + ")" + RESET);

        mergeLock.lock();
        try {
            gate.markMerging(mr.getPrNumber());

            // ── Conflict check ──
            boolean conflict = detectConflict(mr.getPrNumber(), mr.getBranch(), mr.getTargetBranch());
            if (conflict) {
                mr.setStatus("CONFLICT");
                gate.markConflict(mr.getPrNumber());
                mergeHistory.add(mr);
                activeMerge = null;

                System.out.println(RED + "[MergerMCPServer] ⚠ Conflict detected for PR #"
                        + mr.getPrNumber() + " – queuing auto-rebase attempt" + RESET);

                // Attempt automatic rebase
                Map<String, Object> rebaseResult = applyResolutionStrategy(
                        mr.getPrNumber(), ConflictResolutionStrategy.REBASE);

                if (Boolean.TRUE.equals(rebaseResult.get("resolved"))) {
                    // Re-add to front of queue with highest priority
                    MergeRequest retry = new MergeRequest(mr.getPrNumber(), mr.getPipelineId(),
                            mr.getBranch(), mr.getTargetBranch(), 0);
                    mergeQueue.add(retry);
                    scheduleProcessing();
                }

                return Map.of(
                        "merged",   false,
                        "conflict", true,
                        "prNumber", mr.getPrNumber(),
                        "message",  "Conflict detected; rebase attempted. Check queue for retry.",
                        "rebase",   rebaseResult
                );
            }

            // ── Perform merge ──
            boolean mergeSuccess = performMerge(mr);

            if (mergeSuccess) {
                mr.setStatus("MERGED");
                gate.markMerged(mr.getPrNumber());
                System.out.println(GREEN + "[MergerMCPServer] ✅ PR #" + mr.getPrNumber()
                        + " successfully merged into " + mr.getTargetBranch() + RESET);

                // After a successful merge, check remaining queued PRs for new conflicts
                revalidateQueue(mr.getTargetBranch());

            } else {
                mr.setStatus("FAILED");
                gate.markAborted(mr.getPrNumber());
                System.out.println(RED + "[MergerMCPServer] ✗ Merge failed for PR #"
                        + mr.getPrNumber() + RESET);
            }

            mergeHistory.add(mr);
            activeMerge = null;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("merged",      mergeSuccess);
            result.put("conflict",    false);
            result.put("prNumber",    mr.getPrNumber());
            result.put("branch",      mr.getBranch());
            result.put("targetBranch", mr.getTargetBranch());
            result.put("mergedAt",    Instant.now().toString());
            return result;

        } finally {
            mergeLock.unlock();
        }
    }

    /**
     * Schedule async processing of the queue on the single-threaded executor.
     */
    private void scheduleProcessing() {
        executor.submit(() -> {
            while (processingEnabled.get() && !mergeQueue.isEmpty() && activeMerge == null) {
                MergeRequest next = mergeQueue.peek();
                if (next != null) {
                    doMerge(next);
                }
            }
        });
    }

    /**
     * After a merge completes, re-check queued PRs against the (now updated) target branch.
     * Any that now have conflicts are moved to CONFLICT_DETECTED and flagged for resolution.
     */
    private void revalidateQueue(String targetBranch) {
        System.out.println(CYAN + "[MergerMCPServer] 🔍 Re-validating " + mergeQueue.size()
                + " queued PR(s) after merge into " + targetBranch + RESET);

        List<MergeRequest> toRemove = new ArrayList<>();
        for (MergeRequest queued : mergeQueue) {
            boolean conflict = detectConflict(queued.getPrNumber(), queued.getBranch(), targetBranch);
            if (conflict) {
                queued.setStatus("CONFLICT");
                gate.markConflict(queued.getPrNumber());
                toRemove.add(queued);
                System.out.println(YELLOW + "[MergerMCPServer] ⚠ PR #" + queued.getPrNumber()
                        + " now has conflicts after previous merge – removed from queue."
                        + " Operator must resolve and re-queue." + RESET);
            }
        }
        mergeQueue.removeAll(toRemove);
        mergeHistory.addAll(toRemove);
    }

    // ── Conflict detection & resolution (simulation layer) ───────────────

    /**
     * In a real integration this would call `git merge-tree` or the GitHub
     * "check merge" API.  Here we use a deterministic simulation based on
     * PR number parity so tests are predictable.
     *
     * Override this method in a subclass (or inject a strategy) to plug in
     * real Git/GitHub logic.
     */
    protected boolean detectConflict(int prNumber, String branch, String targetBranch) {
        // Simulate: PR numbers divisible by 7 conflict (purely for demo purposes)
        boolean simulated = (prNumber % 7 == 0);
        System.out.println(YELLOW + "[MergerMCPServer] 🔎 Conflict check for PR #" + prNumber
                + " (" + branch + " → " + targetBranch + "): "
                + (simulated ? "CONFLICT" : "clean") + RESET);
        return simulated;
    }

    /**
     * In a real integration this would call `git merge` with a --squash or
     * --merge-commit strategy and push.  Returns {@code true} on success.
     */
    protected boolean performMerge(MergeRequest mr) {
        // Simulate always-succeeding merge (after conflict-check gate above)
        System.out.println(GREEN + "[MergerMCPServer] ⚙ Executing merge: PR #" + mr.getPrNumber()
                + " " + mr.getBranch() + " → " + mr.getTargetBranch() + RESET);
        return true;
    }

    private List<String> simulateConflictingFiles(String branch) {
        return List.of("src/main/java/com/blacklight/uac/core/coordinator/Brain.java",
                       "src/main/resources/application.yaml");
    }

    private List<String> suggestResolutionStrategies(List<String> conflictingFiles) {
        // If only config / resource files conflict → rebase is usually safe
        boolean onlyConfig = conflictingFiles.stream()
                .allMatch(f -> f.contains("resources") || f.endsWith(".yaml") || f.endsWith(".yml"));
        if (onlyConfig) {
            return List.of("REBASE", "ACCEPT_THEIRS");
        }
        return List.of("REBASE", "MANUAL");
    }

    private Map<String, Object> applyResolutionStrategy(int prNumber,
                                                         ConflictResolutionStrategy strategy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prNumber", prNumber);
        result.put("strategy", strategy.name());

        switch (strategy) {
            case REBASE -> {
                // Simulate a rebase – in practice: git rebase origin/master && git push --force-with-lease
                System.out.println(CYAN + "[MergerMCPServer] 🔄 Rebasing PR #" + prNumber
                        + " on top of master..." + RESET);
                boolean success = (prNumber % 7 != 0) || (prNumber % 14 == 0); // sim: 50 % re-try
                result.put("resolved", success);
                result.put("message",  success
                        ? "Rebase successful – PR re-queued."
                        : "Rebase still conflicting – manual intervention required.");
                if (!success) {
                    gate.markConflict(prNumber);
                    result.put("requiresManual", true);
                }
            }
            case ACCEPT_OURS -> {
                System.out.println(CYAN + "[MergerMCPServer] 🔧 Resolving PR #" + prNumber
                        + " using ACCEPT_OURS strategy..." + RESET);
                result.put("resolved", true);
                result.put("message",  "Conflicts resolved by keeping master (ours) for all hunks.");
            }
            case ACCEPT_THEIRS -> {
                System.out.println(CYAN + "[MergerMCPServer] 🔧 Resolving PR #" + prNumber
                        + " using ACCEPT_THEIRS strategy..." + RESET);
                result.put("resolved", true);
                result.put("message",  "Conflicts resolved by keeping feature branch (theirs) for all hunks.");
            }
            case MANUAL -> {
                System.out.println(RED + "[MergerMCPServer] 🚨 PR #" + prNumber
                        + " requires manual conflict resolution." + RESET);
                result.put("resolved",       false);
                result.put("requiresManual", true);
                result.put("message",        "Cannot auto-resolve. Please resolve conflicts manually and re-push.");
            }
        }
        return result;
    }
}

