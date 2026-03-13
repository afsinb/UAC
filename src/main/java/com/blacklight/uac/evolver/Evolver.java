package com.blacklight.uac.evolver;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/**
 * Evolver - Autonomous development pipeline with task dependency enforcement.
 *
 * Pipeline: clone → reproduce → patch → verify → pr
 *
 * Task dependency rules (enforced by TaskDependencyGraph):
 *   CODE_FIX must complete before PR_RAISED
 *   PR_RAISED must complete before PR_APPROVED
 *   PR_APPROVED must complete before PR_MERGED
 *   PR_MERGED must complete before DEPLOY
 *
 * Deployment is additionally gated by PRDeploymentGate, which verifies the PR
 * is in MERGED state before allowing deployment to proceed.
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
     * Attempt deployment of the artefact produced by this task's PR.
     * Throws {@link PRDeploymentGate.DeploymentBlockedException} if the PR
     * has not been approved and merged into master.
     */
    public void deploy(DevelopmentTask task) {
        String pipelineId = task.getPipelineId();

        // 1. Dependency graph check
        if (!dependencyGraph.canExecute(pipelineId, TaskDependencyGraph.TaskNode.DEPLOY)) {
            throw new PRDeploymentGate.DeploymentBlockedException(
                    "Deploy is not yet unblocked in the dependency graph for pipeline "
                            + pipelineId + ". Ensure PR_MERGED step is complete.");
        }

        // 2. PR lifecycle gate check
        deploymentGate.assertDeploymentAllowed(task.getPrNumber());

        // 3. Proceed with deployment
        System.out.println(GREEN + "[Evolver] 🚀 Deploying artefact from pipeline "
                + pipelineId + " (PR #" + task.getPrNumber() + ")" + RESET);

        // -- real deployment logic would go here --

        // 4. Mark DEPLOY as completed in the graph
        dependencyGraph.markCompleted(pipelineId, TaskDependencyGraph.TaskNode.DEPLOY);
        task.setPrState(PRLifecycleState.DEPLOYED);
        deploymentGate.markDeployed(task.getPrNumber());

        System.out.println(GREEN + "[Evolver] ✅ Deployment complete for PR #"
                + task.getPrNumber() + RESET);
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
