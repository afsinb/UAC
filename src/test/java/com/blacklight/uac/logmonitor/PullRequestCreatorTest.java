package com.blacklight.uac.logmonitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PullRequestCreatorTest {

    @Test
    void autoApproveFailsWhenGitSystemDoesNotConfirmApproval() {
        TestablePullRequestCreator creator = new TestablePullRequestCreator(true, false);
        PullRequestCreator.PullRequest pr = new PullRequestCreator.PullRequest(
                "42", "title", "desc", "fix/branch-42", "diff", "OPEN");

        boolean result = creator.autoApprove(pr);

        assertFalse(result, "autoApprove must fail when Git provider does not report APPROVED");
    }

    @Test
    void autoApproveSucceedsOnlyAfterGitApprovalIsVerified() {
        TestablePullRequestCreator creator = new TestablePullRequestCreator(true, true);
        PullRequestCreator.PullRequest pr = new PullRequestCreator.PullRequest(
                "43", "title", "desc", "fix/branch-43", "diff", "OPEN");

        boolean result = creator.autoApprove(pr);

        assertTrue(result, "autoApprove should succeed once Git provider reports APPROVED");
    }

    @Test
    void autoApproveFailsWhenApprovalSubmissionFails() {
        TestablePullRequestCreator creator = new TestablePullRequestCreator(false, true);
        PullRequestCreator.PullRequest pr = new PullRequestCreator.PullRequest(
                "44", "title", "desc", "fix/branch-44", "diff", "OPEN");

        boolean result = creator.autoApprove(pr);

        assertFalse(result, "autoApprove must fail if approval cannot be submitted in Git provider");
    }

    private static class TestablePullRequestCreator extends PullRequestCreator {
        private final boolean submitApprovalResult;
        private final boolean verifyApprovalResult;

        TestablePullRequestCreator(boolean submitApprovalResult, boolean verifyApprovalResult) {
            super(".", "https://example.com/repo.git", false);
            this.submitApprovalResult = submitApprovalResult;
            this.verifyApprovalResult = verifyApprovalResult;
        }

        @Override
        protected boolean requestApprovalInGitSystem(PullRequest pr) {
            return submitApprovalResult;
        }

        @Override
        protected boolean isApprovedInGitSystem(PullRequest pr) {
            return verifyApprovalResult;
        }
    }
}

