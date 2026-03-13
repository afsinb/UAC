package com.blacklight.uac.git;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * GitIntegration - Real Git operations for UAC
 * Provides Git clone, commit, push, and PR creation
 */
public class GitIntegration {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final String repoUrl;
    private final String localPath;
    private final String branch;
    
    public GitIntegration(String repoUrl, String localPath, String branch) {
        this.repoUrl = repoUrl;
        this.localPath = localPath;
        this.branch = branch;
    }
    
    /**
     * Git operation result
     */
    public static class GitResult {
        private final boolean success;
        private final String output;
        private final String error;
        private final int exitCode;
        
        public GitResult(boolean success, String output, String error, int exitCode) {
            this.success = success;
            this.output = output;
            this.error = error;
            this.exitCode = exitCode;
        }
        
        public boolean isSuccess() { return success; }
        public String getOutput() { return output; }
        public String getError() { return error; }
        public int getExitCode() { return exitCode; }
    }
    
    /**
     * Clone repository
     */
    public GitResult clone() {
        System.out.println(CYAN + "📥 Cloning repository..." + RESET);
        System.out.println(YELLOW + "  → URL: " + repoUrl + RESET);
        System.out.println(YELLOW + "  → Path: " + localPath + RESET);
        
        return executeGitCommand("clone", repoUrl, localPath);
    }
    
    /**
     * Create and checkout branch
     */
    public GitResult createBranch(String branchName) {
        System.out.println(CYAN + "🌿 Creating branch: " + branchName + RESET);
        
        return executeGitCommand("-C", localPath, "checkout", "-b", branchName);
    }
    
    /**
     * Stage all changes
     */
    public GitResult stageAll() {
        System.out.println(CYAN + "📦 Staging changes..." + RESET);
        
        return executeGitCommand("-C", localPath, "add", ".");
    }
    
    /**
     * Commit changes
     */
    public GitResult commit(String message) {
        System.out.println(CYAN + "💾 Committing: " + message + RESET);
        
        return executeGitCommand("-C", localPath, "commit", "-m", message);
    }
    
    /**
     * Push to remote
     */
    public GitResult push(String branchName) {
        System.out.println(CYAN + "📤 Pushing to remote..." + RESET);
        
        return executeGitCommand("-C", localPath, "push", "-u", "origin", branchName);
    }
    
    /**
     * Full workflow: create branch, commit, push
     */
    public GitResult commitAndPush(String branchName, String commitMessage) {
        GitResult result;
        
        // Create branch
        result = createBranch(branchName);
        if (!result.isSuccess()) {
            return result;
        }
        
        // Stage changes
        result = stageAll();
        if (!result.isSuccess()) {
            return result;
        }
        
        // Commit
        result = commit(commitMessage);
        if (!result.isSuccess()) {
            return result;
        }
        
        // Push
        return push(branchName);
    }
    
    /**
     * Apply patch file
     */
    public GitResult applyPatch(String patchFile) {
        System.out.println(CYAN + "🔧 Applying patch: " + patchFile + RESET);
        
        return executeGitCommand("-C", localPath, "apply", patchFile);
    }
    
    /**
     * Get current status
     */
    public GitResult status() {
        return executeGitCommand("-C", localPath, "status", "--short");
    }
    
    /**
     * Get diff
     */
    public GitResult diff() {
        return executeGitCommand("-C", localPath, "diff");
    }
    
    /**
     * Execute Git command
     */
    private GitResult executeGitCommand(String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(Arrays.asList(args));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            boolean success = exitCode == 0;
            
            if (success) {
                System.out.println(GREEN + "  ✓ Git command successful" + RESET);
            } else {
                System.out.println(RED + "  ✗ Git command failed (exit code: " + exitCode + ")" + RESET);
            }
            
            return new GitResult(success, output.toString(), "", exitCode);
            
        } catch (Exception e) {
            System.out.println(RED + "  ✗ Git error: " + e.getMessage() + RESET);
            return new GitResult(false, "", e.getMessage(), -1);
        }
    }
}
