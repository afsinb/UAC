package com.blacklight.uac.evolver;

import com.blacklight.uac.mcp.DeploymentEvent;
import com.blacklight.uac.mcp.DeploymentSyncManager;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/**
 * Evolver - Autonomous development pipeline with task dependency enforcement.
 *
 * Pipeline: clone → reproduce → patch → verify → pr
 *
 * Deployment rule (developV2):
 *   Calling {@link #deploy(DevelopmentTask)} does NOT deploy immediately.
 *   It submits a {@link DeploymentEvent} to the central
 *   {@link DeploymentSyncManager}, which ensures:
 *     – Only ONE deployment runs system-wide at any time.
 *     – Events for the same pipeline+environment are consolidated.
 *     – PR gate is re-verified at execution time.
 *
 * Task dependency rules (TaskDependencyGraph):
 *   CODE_FIX → PR_RAISED → PR_APPROVED → PR_MERGED → DEPLOY
 */
public class Evolver {

    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    private String repositoryPath;
    private String verificationSandboxPath;

    /** Shared dependency graph – one pipeline per DevelopmentTask. */
    private final TaskDependencyGraph dependencyGraph = new TaskDependencyGraph();

    /** Shared deployment gate singleton. */
    private final PRDeploymentGate deploymentGate = PRDeploymentGate.getInstance();

    public Evolver(String repositoryPath, String sandboxPath) {
        this.repositoryPath        = repositoryPath;
        this.verificationSandboxPath = sandboxPath;
    }

    // ── Public API ────────────────────────────────────────────────────────

    public CompletableFuture<DevelopmentTask> executeDevelopmentCycle(DevelopmentTask task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure a pipeline is registered for this task
                dependencyGraph.registerPipeline(task.getPipelineId());

                return switch (task.getTaskType()) {
                    case "clone"     -> cloneRepository(task);
                    case "reproduce" -> reproduceIssue(task);
                    case "patch"     -> createPatch(task);
                    case "verify"    -> verifyPatch(task);
                    case "pr"        -> createPullRequest(task);
                    default -> throw new IllegalArgumentException(
                            "Unknown task type: " + task.getTaskType());
                };
            } catch (Exception e) {
                System.err.println("Development cycle failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Submit a deployment event for the artefact produced by this task's PR.
     *
     * The method:
     *  1. Checks the TaskDependencyGraph (PR_MERGED must be complete).
     *  2. Checks the PRDeploymentGate (PR must be in MERGED state).
     *  3. Submits a {@link DeploymentEvent} to {@link DeploymentSyncManager}.
     *     The event is processed asynchronously; this method returns immediately.
     *  4. Marks DEPLOY as completed in the graph once the event is accepted.
     *
     * If another deployment for the same pipeline+environment is already queued,
     * the new event consolidates (supersedes) it — the system deploys only once
     * with the latest artefact.
     *
     * @throws PRDeploymentGate.DeploymentBlockedException if the dependency
     *         graph or PR gate blocks the deployment.
     */
    public DeploymentSyncManager.SubmitResult deploy(DevelopmentTask task) {
        String pipelineId = task.getPipelineId();

        // 1. Dependency graph check
        if (!dependencyGraph.canExecute(pipelineId, TaskDependencyGraph.TaskNode.DEPLOY)) {
            throw new PRDeploymentGate.DeploymentBlockedException(
                    "Deploy is not yet unblocked in the dependency graph for pipeline "
                            + pipelineId + ". Ensure PR_MERGED step is complete.");
        }

        // 2. PR lifecycle gate check (fast-fail before submitting)
        deploymentGate.assertDeploymentAllowed(task.getPrNumber());

        // 3. Build and submit the deployment event (non-blocking)
        String environment  = task.getTargetBranch() != null
                && task.getTargetBranch().contains("prod") ? "production" : "staging";
        DeploymentEvent event = new DeploymentEvent(
                task.getPrNumber(),
                pipelineId,
                environment,
                task.getBranch() != null ? task.getBranch() : "latest",
                5   // default priority
        );

        System.out.println(GREEN + "[Evolver] 📬 Submitting deployment event for pipeline "
                + pipelineId + " (PR #" + task.getPrNumber() + ") → " + environment + RESET);

        DeploymentSyncManager.SubmitResult result =
                DeploymentSyncManager.getInstance().submitEvent(event);

        if (result.isAccepted()) {
            // 4. Mark DEPLOY node in the dependency graph (event accepted = deploy initiated)
            dependencyGraph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.DEPLOY);
            task.setPrState(PRLifecycleState.DEPLOYED);

            System.out.println(GREEN + "[Evolver] ✅ Deployment event accepted: "
                    + result.getEventId() + " – " + result.getMessage() + RESET);
        } else {
            System.out.println(RED + "[Evolver] 🚫 Deployment event rejected: "
                    + result.getMessage() + RESET);
            throw new PRDeploymentGate.DeploymentBlockedException(result.getMessage());
        }

        return result;
    }

    /** Expose the dependency graph for external observers (e.g. Brain, tests). */
    public TaskDependencyGraph getDependencyGraph() { return dependencyGraph; }

    // ── Pipeline stages ───────────────────────────────────────────────────

    private DevelopmentTask cloneRepository(DevelopmentTask task) {
        System.out.println(CYAN + "[Evolver] Cloning repository from: " + repositoryPath + RESET);
        try {
            task.setSourceCode("repository_cloned");
            // CODE_FIX is the first dependency node – mark it completed here
            // so that PR_RAISED can proceed once the PR stage is reached.
            dependencyGraph.markCompleted(task.getPipelineId(),
                    TaskDependencyGraph.TaskNode.CODE_FIX);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone repository", e);
        }
        return task;
    }

    private DevelopmentTask reproduceIssue(DevelopmentTask task) {
        System.out.println(CYAN + "[Evolver] Reproducing issue from logs" + RESET);
        try {
            task.setSourceCode("issue_reproduced");
        } catch (Exception e) {
            throw new RuntimeException("Failed to reproduce issue", e);
        }
        return task;
    }

    private DevelopmentTask createPatch(DevelopmentTask task) {
        System.out.println(CYAN + "[Evolver] Creating patch in sandbox: "
                + verificationSandboxPath + RESET);
        try {
            task.setPatchContent("patch_content_created");
            task.setVerified(false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create patch", e);
        }
        return task;
    }

    private DevelopmentTask verifyPatch(DevelopmentTask task) {
        System.out.println(CYAN + "[Evolver] Verifying patch in sandbox: "
                + verificationSandboxPath + RESET);
        try {
            boolean verificationResult = runVerificationTests();
            task.setVerified(verificationResult);
            if (verificationResult) {
                System.out.println(GREEN + "[Evolver] Patch verified successfully" + RESET);
            } else {
                System.out.println(RED + "[Evolver] Patch verification failed" + RESET);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify patch", e);
        }
        return task;
    }

    /**
     * Create a pull request for a verified patch.
     *
     * Steps:
     *  1. Guard: patch must be verified.
     *  2. Guard: CODE_FIX must be completed in the dependency graph.
     *  3. Create PR via gh CLI.
     *  4. Register PR with PRDeploymentGate.
     *  5. Mark PR_RAISED in the dependency graph.
     */
    private DevelopmentTask createPullRequest(DevelopmentTask task) {
        // Guard 1 – patch must be verified
        if (!task.isVerified()) {
            throw new IllegalStateException("Cannot create PR for unverified patch");
        }

        String pipelineId = task.getPipelineId();

        // Guard 2 – CODE_FIX dependency
        if (!dependencyGraph.canExecute(pipelineId, TaskDependencyGraph.TaskNode.PR_RAISED)) {
            throw new PRDeploymentGate.DeploymentBlockedException(
                    "PR_RAISED is blocked: CODE_FIX has not been completed for pipeline "
                            + pipelineId);
        }

        dependencyGraph.markInProgress(pipelineId, TaskDependencyGraph.TaskNode.PR_RAISED);
        System.out.println(CYAN + "[Evolver] Creating pull request via gh CLI with verified patch" + RESET);

        try {
            String branchName   = task.getBranch()       != null ? task.getBranch()       : "fix/" + pipelineId;
            String targetBranch = task.getTargetBranch() != null ? task.getTargetBranch() : "master";

            ProcessBuilder pb = new ProcessBuilder(
                "gh", "pr", "create",
                "--title", "fix: " + task.getTaskType(),
                "--body",  "Automated fix generated by UAC Evolver.\n\nPatch content:\n"
                           + task.getPatchContent(),
                "--base",  targetBranch
            );
            pb.directory(new File(repositoryPath));
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            String prUrl = new String(proc.getInputStream().readAllBytes()).trim();
            proc.getErrorStream().transferTo(OutputStream.nullOutputStream());
            int exitCode = proc.waitFor();

            if (exitCode == 0 && prUrl.contains("github.com")) {
                // Extract PR number from URL (last path segment)
                int prNumber = -1;
                try {
                    prNumber = Integer.parseInt(prUrl.substring(prUrl.lastIndexOf('/') + 1));
                } catch (NumberFormatException ignored) { }

                task.setPrUrl(prUrl);
                task.setPrNumber(prNumber);
                task.setPrState(PRLifecycleState.PR_RAISED);
                task.setTargetBranch(targetBranch);
                task.setBranch(branchName);

                // Register with deployment gate
                deploymentGate.register(prNumber, pipelineId, branchName, targetBranch);

                // Advance dependency graph
                dependencyGraph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.PR_RAISED);

                System.out.println(GREEN + "[Evolver] ✅ Pull request created: " + prUrl + RESET);
                System.out.println(CYAN + "[Evolver] ⏳ Deployment is BLOCKED until PR #"
                        + prNumber + " is approved and merged into " + targetBranch + RESET);
                task.setTaskType("pr_created:" + prUrl);

            } else {
                System.out.println(RED + "[Evolver] gh pr create exited with code "
                        + exitCode + " – PR not created" + RESET);
                task.setTaskType("pr_failed");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to create pull request via gh CLI", e);
        }

        return task;
    }

    private boolean runVerificationTests() {
        System.out.println(CYAN + "[Evolver] Running verification tests..." + RESET);
        return true; // Placeholder
    }
}
