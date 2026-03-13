package com.blacklight.uac.git;

import java.io.*;
import java.net.http.*;
import java.net.*;
import java.util.*;

/**
 * GitHubAPI - GitHub API integration for UAC
 * Provides PR creation, approval, and merge capabilities.
 * PR creation prefers the GitHub CLI (gh pr create) so it follows
 * the existing gh auth / SSH workflow without requiring GITHUB_TOKEN.
 * REST API is used as a fallback when a token is explicitly provided.
 */
public class GitHubAPI {

    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final String token;
    private final String owner;
    private final String repo;
    private final File workingDir;
    private final HttpClient httpClient;

    /** Use when a working directory is available (enables gh CLI path). */
    public GitHubAPI(String token, String owner, String repo, File workingDir) {
        this.token = token;
        this.owner = owner;
        this.repo = repo;
        this.workingDir = workingDir;
        this.httpClient = HttpClient.newHttpClient();
    }

    /** Convenience constructor – no working dir (gh CLI will run in CWD). */
    public GitHubAPI(String token, String owner, String repo) {
        this(token, owner, repo, null);
    }

    /**
     * Pull Request information
     */
    public static class PullRequest {
        private final int number;
        private final String title;
        private final String url;
        private final String state;
        private final String branch;

        public PullRequest(int number, String title, String url, String state, String branch) {
            this.number = number;
            this.title = title;
            this.url = url;
            this.state = state;
            this.branch = branch;
        }

        public int getNumber() { return number; }
        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getState() { return state; }
        public String getBranch() { return branch; }

        @Override
        public String toString() {
            return String.format("PR #%d: %s [%s]", number, title, state);
        }
    }

    /**
     * Create a pull request.
     * Tries the GitHub CLI (gh pr create) first so auth is handled by
     * "gh auth login" / SSH keys.  Falls back to the REST API only when
     * a personal-access token is explicitly available.
     */
    public PullRequest createPullRequest(String title, String body, String headBranch, String baseBranch) {
        System.out.println(CYAN + "📝 Creating Pull Request..." + RESET);
        System.out.println(YELLOW + "  → Title: " + title + RESET);
        System.out.println(YELLOW + "  → Branch: " + headBranch + " → " + baseBranch + RESET);

        // ── 1. Try gh CLI (no token required) ──────────────────────────────
        PullRequest cliPr = createViaCLI(title, body, headBranch, baseBranch);
        if (cliPr != null) return cliPr;

        // ── 2. Fall back to REST API when a token is available ──────────────
        if (token == null || token.isEmpty()) {
            System.out.println(RED + "  ✗ gh CLI unavailable and no token configured – cannot create PR" + RESET);
            return null;
        }

        try {
            String requestBody = """
                {
                    "title": "%s",
                    "body": "%s",
                    "head": "%s",
                    "base": "%s"
                }
                """.formatted(
                    escapeJson(title),
                    escapeJson(body),
                    headBranch,
                    baseBranch
                );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/pulls"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 201) {
                int prNumber = extractInt(response.body(), "number");
                String prUrl = extractString(response.body(), "html_url");
                PullRequest pr = new PullRequest(prNumber, title, prUrl, "open", headBranch);
                System.out.println(GREEN + "  ✓ Pull Request created via REST API: " + pr + RESET);
                return pr;
            } else {
                System.out.println(RED + "  ✗ Failed to create PR via REST API: " + response.statusCode() + RESET);
                return null;
            }

        } catch (Exception e) {
            System.out.println(RED + "  ✗ Error creating PR via REST API: " + e.getMessage() + RESET);
            return null;
        }
    }

    /**
     * Attempt to create a PR via the GitHub CLI.
     * Returns a {@link PullRequest} with the URL on success, or {@code null}
     * if the CLI is not installed / not authenticated.
     */
    private PullRequest createViaCLI(String title, String body, String headBranch, String baseBranch) {
        try {
            List<String> cmd = new ArrayList<>(List.of(
                "gh", "pr", "create",
                "--title", title,
                "--body",  body,
                "--head",  headBranch,
                "--base",  baseBranch
            ));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workingDir != null) pb.directory(workingDir);
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            String stdout = new String(proc.getInputStream().readAllBytes()).trim();
            proc.getErrorStream().transferTo(OutputStream.nullOutputStream()); // drain stderr
            int exitCode = proc.waitFor();

            if (exitCode == 0 && stdout.contains("github.com")) {
                System.out.println(GREEN + "  ✓ Pull Request created via gh CLI: " + stdout + RESET);
                return new PullRequest(-1, title, stdout, "open", headBranch);
            }
            return null;
        } catch (Exception e) {
            // gh not installed or not on PATH – fall through to REST API
            return null;
        }
    }

    /**
     * Approve a pull request
     */
    public boolean approvePullRequest(int prNumber) {
        System.out.println(CYAN + "✅ Approving Pull Request #" + prNumber + "..." + RESET);

        try {
            String requestBody = """
                {
                    "event": "APPROVE"
                }
                """;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo +
                    "/pulls/" + prNumber + "/reviews"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            boolean success = response.statusCode() == 200;
            if (success) {
                System.out.println(GREEN + "  ✓ Pull Request approved" + RESET);
            } else {
                System.out.println(RED + "  ✗ Failed to approve PR: " + response.statusCode() + RESET);
            }

            return success;

        } catch (Exception e) {
            System.out.println(RED + "  ✗ Error approving PR: " + e.getMessage() + RESET);
            return false;
        }
    }

    /**
     * Merge a pull request
     */
    public boolean mergePullRequest(int prNumber, String commitMessage) {
        System.out.println(CYAN + "🔀 Merging Pull Request #" + prNumber + "..." + RESET);

        try {
            String requestBody = """
                {
                    "commit_title": "%s",
                    "merge_method": "squash"
                }
                """.formatted(escapeJson(commitMessage));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo +
                    "/pulls/" + prNumber + "/merge"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            boolean success = response.statusCode() == 200;
            if (success) {
                System.out.println(GREEN + "  ✓ Pull Request merged" + RESET);
            } else {
                System.out.println(RED + "  ✗ Failed to merge PR: " + response.statusCode() + RESET);
            }

            return success;

        } catch (Exception e) {
            System.out.println(RED + "  ✗ Error merging PR: " + e.getMessage() + RESET);
            return false;
        }
    }

    /**
     * Get pull request status
     */
    public String getPullRequestStatus(int prNumber) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo +
                    "/pulls/" + prNumber))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                return extractString(response.body(), "state");
            }

            return "unknown";

        } catch (Exception e) {
            return "error";
        }
    }

    /**
     * Check if API is configured
     */
    public boolean isConfigured() {
        return token != null && !token.isEmpty() &&
               owner != null && !owner.isEmpty() &&
               repo != null && !repo.isEmpty();
    }

    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private int extractInt(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search) + search.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private String extractString(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search) + search.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }
}
