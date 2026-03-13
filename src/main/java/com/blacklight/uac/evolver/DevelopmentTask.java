package com.blacklight.uac.evolver;

import java.util.ArrayList;
import java.util.List;

/**
 * DevelopmentTask - Represents a unit of work in the autonomous development pipeline.
 *
 * Extended with PR lifecycle tracking so the pipeline (Evolver, Brain) can
 * enforce the dependency rule:
 *   CODE_FIX → PR_RAISED → PR_APPROVED → PR_MERGED → DEPLOY
 */
public class DevelopmentTask {

    private String taskType; // "clone", "reproduce", "patch", "verify", "pr"
    private String sourceCode;
    private List<String> logs;
    private String patchContent;
    private boolean verified;

    // ── PR lifecycle fields ───────────────────────────────────────────────

    /** Unique pipeline identifier used by TaskDependencyGraph. */
    private String pipelineId;

    /** GitHub PR number; -1 until a PR is created. */
    private int prNumber = -1;

    /** URL of the created pull request. */
    private String prUrl;

    /** Current lifecycle state of the associated PR. */
    private PRLifecycleState prState = PRLifecycleState.CODE_FIX_COMMITTED;

    /** Source branch name for the PR. */
    private String branch;

    /** Target / base branch (e.g. "master"). */
    private String targetBranch = "master";

    // ── Constructors ──────────────────────────────────────────────────────

    public DevelopmentTask(String taskType) {
        this.taskType   = taskType;
        this.logs       = new ArrayList<>();
        this.verified   = false;
        this.pipelineId = "pipeline-" + System.currentTimeMillis();
    }

    public DevelopmentTask(String taskType, String pipelineId) {
        this(taskType);
        this.pipelineId = pipelineId;
    }

    // ── Original getters / setters ────────────────────────────────────────

    public String getTaskType()                  { return taskType; }
    public void   setTaskType(String taskType)   { this.taskType = taskType; }

    public String getSourceCode()                { return sourceCode; }
    public void   setSourceCode(String src)      { this.sourceCode = src; }

    public List<String> getLogs()                { return logs; }
    public void   setLogs(List<String> logs)     { this.logs = logs; }

    public String getPatchContent()              { return patchContent; }
    public void   setPatchContent(String patch)  { this.patchContent = patch; }

    public boolean isVerified()                  { return verified; }
    public void    setVerified(boolean verified) { this.verified = verified; }

    // ── PR lifecycle getters / setters ────────────────────────────────────

    public String getPipelineId()                        { return pipelineId; }
    public void   setPipelineId(String pipelineId)       { this.pipelineId = pipelineId; }

    public int    getPrNumber()                          { return prNumber; }
    public void   setPrNumber(int prNumber)              { this.prNumber = prNumber; }

    public String getPrUrl()                             { return prUrl; }
    public void   setPrUrl(String prUrl)                 { this.prUrl = prUrl; }

    public PRLifecycleState getPrState()                 { return prState; }
    public void   setPrState(PRLifecycleState prState)   { this.prState = prState; }

    public String getBranch()                            { return branch; }
    public void   setBranch(String branch)               { this.branch = branch; }

    public String getTargetBranch()                      { return targetBranch; }
    public void   setTargetBranch(String targetBranch)   { this.targetBranch = targetBranch; }

    // ── Convenience guards ────────────────────────────────────────────────

    /** Returns {@code true} only when the PR has been merged and deployment is allowed. */
    public boolean isDeploymentAllowed() {
        return prState != null && prState.isDeploymentAllowed();
    }

    @Override
    public String toString() {
        return String.format("DevelopmentTask{type=%s, pipeline=%s, pr=#%d, state=%s, verified=%b}",
                taskType, pipelineId, prNumber, prState, verified);
    }
}

