package com.blacklight.uac.config;

/**
 * ApplicationConfig - Application configuration settings
 * Central configuration for UAC system
 */
public class ApplicationConfig {
    
    // Health score thresholds
    private double criticalThreshold = 0.3;
    private double unhealthyThreshold = 0.5;
    private double degradedThreshold = 0.7;
    private double stableThreshold = 0.9;
    
    // Timing configurations
    private long stabilizationWaitMs = 10000;
    private long dnaWindowMinutes = 5;
    private long anomalyDetectionIntervalMs = 5000;
    
    // Recovery configurations
    private int maxRetryAttempts = 3;
    private long retryDelayMs = 1000;
    private boolean enableAutoRecovery = true;
    
    // Evolver configurations
    private String repositoryPath = "/tmp/repo";
    private String sandboxPath = "/tmp/sandbox";
    private boolean enableAutoPatch = true;
    private boolean requireVerification = true;
    
    // Observer configurations
    private int metricsBufferSize = 1000;
    private int logsBufferSize = 1000;
    private int dnaBufferSize = 100;
    
    // Health score weights
    private double metricsWeight = 0.40;
    private double logsWeight = 0.35;
    private double dnaWeight = 0.25;
    
    // AI/LLM API Keys
    private String openRouterApiKey = System.getenv("OPENROUTER_API_KEY");
    private String openAiApiKey = System.getenv("OPENAI_API_KEY");
    private String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
    private String openRouterModel = System.getenv("OPENROUTER_MODEL") != null ? 
        System.getenv("OPENROUTER_MODEL") : "meta-llama/llama-3.1-8b-instruct:free";
    
    // GitHub Configuration
    private String githubToken = System.getenv("GITHUB_TOKEN");
    private String githubOwner = System.getenv("GITHUB_OWNER");
    private String githubRepo = System.getenv("GITHUB_REPO");
    
    public ApplicationConfig() {
        // Default constructor
    }
    
    // Getters and setters
    public double getCriticalThreshold() { return criticalThreshold; }
    public void setCriticalThreshold(double criticalThreshold) { this.criticalThreshold = criticalThreshold; }
    
    public double getUnhealthyThreshold() { return unhealthyThreshold; }
    public void setUnhealthyThreshold(double unhealthyThreshold) { this.unhealthyThreshold = unhealthyThreshold; }
    
    public double getDegradedThreshold() { return degradedThreshold; }
    public void setDegradedThreshold(double degradedThreshold) { this.degradedThreshold = degradedThreshold; }
    
    public double getStableThreshold() { return stableThreshold; }
    public void setStableThreshold(double stableThreshold) { this.stableThreshold = stableThreshold; }
    
    public long getStabilizationWaitMs() { return stabilizationWaitMs; }
    public void setStabilizationWaitMs(long stabilizationWaitMs) { this.stabilizationWaitMs = stabilizationWaitMs; }
    
    public long getDnaWindowMinutes() { return dnaWindowMinutes; }
    public void setDnaWindowMinutes(long dnaWindowMinutes) { this.dnaWindowMinutes = dnaWindowMinutes; }
    
    public long getAnomalyDetectionIntervalMs() { return anomalyDetectionIntervalMs; }
    public void setAnomalyDetectionIntervalMs(long anomalyDetectionIntervalMs) { this.anomalyDetectionIntervalMs = anomalyDetectionIntervalMs; }
    
    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
    
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    
    public boolean isEnableAutoRecovery() { return enableAutoRecovery; }
    public void setEnableAutoRecovery(boolean enableAutoRecovery) { this.enableAutoRecovery = enableAutoRecovery; }
    
    public String getRepositoryPath() { return repositoryPath; }
    public void setRepositoryPath(String repositoryPath) { this.repositoryPath = repositoryPath; }
    
    public String getSandboxPath() { return sandboxPath; }
    public void setSandboxPath(String sandboxPath) { this.sandboxPath = sandboxPath; }
    
    public boolean isEnableAutoPatch() { return enableAutoPatch; }
    public void setEnableAutoPatch(boolean enableAutoPatch) { this.enableAutoPatch = enableAutoPatch; }
    
    public boolean isRequireVerification() { return requireVerification; }
    public void setRequireVerification(boolean requireVerification) { this.requireVerification = requireVerification; }
    
    public int getMetricsBufferSize() { return metricsBufferSize; }
    public void setMetricsBufferSize(int metricsBufferSize) { this.metricsBufferSize = metricsBufferSize; }
    
    public int getLogsBufferSize() { return logsBufferSize; }
    public void setLogsBufferSize(int logsBufferSize) { this.logsBufferSize = logsBufferSize; }
    
    public int getDnaBufferSize() { return dnaBufferSize; }
    public void setDnaBufferSize(int dnaBufferSize) { this.dnaBufferSize = dnaBufferSize; }
    
    public double getMetricsWeight() { return metricsWeight; }
    public void setMetricsWeight(double metricsWeight) { this.metricsWeight = metricsWeight; }
    
    public double getLogsWeight() { return logsWeight; }
    public void setLogsWeight(double logsWeight) { this.logsWeight = logsWeight; }
    
    public double getDnaWeight() { return dnaWeight; }
    public void setDnaWeight(double dnaWeight) { this.dnaWeight = dnaWeight; }
    
    // Getters and setters for API keys
    public String getOpenRouterApiKey() { return openRouterApiKey; }
    public void setOpenRouterApiKey(String openRouterApiKey) { this.openRouterApiKey = openRouterApiKey; }
    
    public String getOpenAiApiKey() { return openAiApiKey; }
    public void setOpenAiApiKey(String openAiApiKey) { this.openAiApiKey = openAiApiKey; }
    
    public String getAnthropicApiKey() { return anthropicApiKey; }
    public void setAnthropicApiKey(String anthropicApiKey) { this.anthropicApiKey = anthropicApiKey; }
    
    public String getOpenRouterModel() { return openRouterModel; }
    public void setOpenRouterModel(String openRouterModel) { this.openRouterModel = openRouterModel; }
    
    public String getGithubToken() { return githubToken; }
    public void setGithubToken(String githubToken) { this.githubToken = githubToken; }
    
    public String getGithubOwner() { return githubOwner; }
    public void setGithubOwner(String githubOwner) { this.githubOwner = githubOwner; }
    
    public String getGithubRepo() { return githubRepo; }
    public void setGithubRepo(String githubRepo) { this.githubRepo = githubRepo; }
    
    @Override
    public String toString() {
        return String.format("ApplicationConfig{thresholds=[%.2f, %.2f, %.2f, %.2f], autoRecovery=%s, autoPatch=%s}",
            criticalThreshold, unhealthyThreshold, degradedThreshold, stableThreshold,
            enableAutoRecovery, enableAutoPatch);
    }
}
