package com.blacklight.uac.evolver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskDependencyGraph - Enforces ordered task execution in the PR → Deploy pipeline.
 *
 * Dependency chain (any task is BLOCKED until every dependency is COMPLETED):
 *
 *   CODE_FIX  →  PR_RAISED  →  PR_APPROVED  →  PR_MERGED  →  DEPLOY
 *
 * Usage:
 * <pre>
 *   TaskDependencyGraph graph = new TaskDependencyGraph();
 *   graph.registerPipeline("fix-123");          // registers all tasks for a fix
 *   graph.markCompleted("fix-123", TaskNode.CODE_FIX);
 *   boolean ok = graph.canExecute("fix-123", TaskNode.PR_RAISED);  // true
 * </pre>
 */
public class TaskDependencyGraph {

    // ── Task node identifiers ─────────────────────────────────────────────

    public enum TaskNode {
        CODE_FIX,
        PR_RAISED,
        PR_APPROVED,
        PR_MERGED,
        DEPLOY
    }

    // ── Dependency declarations (static, same for every pipeline) ─────────

    /** For each task, the set of tasks that MUST be completed first. */
    private static final Map<TaskNode, Set<TaskNode>> DEPENDENCY_MAP;

    static {
        Map<TaskNode, Set<TaskNode>> m = new EnumMap<>(TaskNode.class);
        m.put(TaskNode.CODE_FIX,    EnumSet.noneOf(TaskNode.class));
        m.put(TaskNode.PR_RAISED,   EnumSet.of(TaskNode.CODE_FIX));
        m.put(TaskNode.PR_APPROVED, EnumSet.of(TaskNode.PR_RAISED));
        m.put(TaskNode.PR_MERGED,   EnumSet.of(TaskNode.PR_APPROVED));
        m.put(TaskNode.DEPLOY,      EnumSet.of(TaskNode.PR_MERGED));
        DEPENDENCY_MAP = Collections.unmodifiableMap(m);
    }

    // ── Per-pipeline state ────────────────────────────────────────────────

    /** pipelineId → set of already-completed nodes */
    private final Map<String, Set<TaskNode>> completedNodes = new ConcurrentHashMap<>();

    /** pipelineId → set of nodes currently being executed */
    private final Map<String, Set<TaskNode>> inProgressNodes = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Register a new pipeline (e.g. one per fix / PR).
     *
     * @param pipelineId unique identifier (e.g. "fix-123", PR branch name)
     */
    public void registerPipeline(String pipelineId) {
        completedNodes.putIfAbsent(pipelineId, ConcurrentHashMap.newKeySet());
        inProgressNodes.putIfAbsent(pipelineId, ConcurrentHashMap.newKeySet());
        System.out.println("[TaskDependencyGraph] Pipeline registered: " + pipelineId);
    }

    /**
     * Returns {@code true} when every dependency of {@code node} in the named
     * pipeline has already been marked completed.
     *
     * @throws IllegalArgumentException if the pipeline has not been registered
     */
    public boolean canExecute(String pipelineId, TaskNode node) {
        Set<TaskNode> completed = requirePipeline(pipelineId);
        Set<TaskNode> required  = DEPENDENCY_MAP.get(node);
        boolean allowed = completed.containsAll(required);

        if (!allowed) {
            Set<TaskNode> missing = new HashSet<>(required);
            missing.removeAll(completed);
            System.out.println("[TaskDependencyGraph] ⛔ Cannot execute " + node
                    + " in pipeline [" + pipelineId + "]. Missing: " + missing);
        }
        return allowed;
    }

    /**
     * Mark a task as currently in progress (optional but aids diagnostics).
     */
    public void markInProgress(String pipelineId, TaskNode node) {
        requirePipeline(pipelineId);
        inProgressNodes.get(pipelineId).add(node);
        System.out.println("[TaskDependencyGraph] ▶ In-progress: " + node
                + " [pipeline=" + pipelineId + "]");
    }

    /**
     * Mark a task as completed, unblocking any tasks that depend on it.
     */
    public void markCompleted(String pipelineId, TaskNode node) {
        requirePipeline(pipelineId);
        inProgressNodes.get(pipelineId).remove(node);
        completedNodes.get(pipelineId).add(node);
        System.out.println("[TaskDependencyGraph] ✅ Completed: " + node
                + " [pipeline=" + pipelineId + "]");
        printReadyTasks(pipelineId);
    }

    /**
     * Returns the set of tasks that are now executable (all deps met, not yet
     * started or completed) for the given pipeline.
     */
    public Set<TaskNode> getReadyTasks(String pipelineId) {
        Set<TaskNode> completed   = requirePipeline(pipelineId);
        Set<TaskNode> inProgress  = inProgressNodes.get(pipelineId);
        Set<TaskNode> ready       = new HashSet<>();

        for (TaskNode node : TaskNode.values()) {
            if (!completed.contains(node) && !inProgress.contains(node)
                    && completed.containsAll(DEPENDENCY_MAP.get(node))) {
                ready.add(node);
            }
        }
        return Collections.unmodifiableSet(ready);
    }

    /**
     * Returns a snapshot of all completed nodes for the given pipeline.
     */
    public Set<TaskNode> getCompletedTasks(String pipelineId) {
        return Collections.unmodifiableSet(requirePipeline(pipelineId));
    }

    /**
     * Convenience: is DEPLOY completed for this pipeline?
     */
    public boolean isDeployed(String pipelineId) {
        return requirePipeline(pipelineId).contains(TaskNode.DEPLOY);
    }

    /**
     * Remove a pipeline (e.g. after deployment or abort).
     */
    public void removePipeline(String pipelineId) {
        completedNodes.remove(pipelineId);
        inProgressNodes.remove(pipelineId);
        System.out.println("[TaskDependencyGraph] 🗑 Pipeline removed: " + pipelineId);
    }

    /** List all registered pipelines. */
    public Set<String> getAllPipelines() {
        return Collections.unmodifiableSet(completedNodes.keySet());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Set<TaskNode> requirePipeline(String pipelineId) {
        Set<TaskNode> set = completedNodes.get(pipelineId);
        if (set == null) {
            throw new IllegalArgumentException(
                    "Pipeline not registered: " + pipelineId
                    + ". Call registerPipeline() first.");
        }
        return set;
    }

    private void printReadyTasks(String pipelineId) {
        Set<TaskNode> ready = getReadyTasks(pipelineId);
        if (!ready.isEmpty()) {
            System.out.println("[TaskDependencyGraph] 🔓 Now ready in ["
                    + pipelineId + "]: " + ready);
        }
    }
}

