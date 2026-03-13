package com.blacklight.uac.domain.healing;

/**
 * HealingContext - Domain Value Object
 * Contains context for healing execution
 */
public class HealingContext {
    private final String systemState;
    private final String targetMetrics;
    private final String deploymentInfo;
    private final boolean dryRun;
    
    private HealingContext(Builder builder) {
        this.systemState = builder.systemState;
        this.targetMetrics = builder.targetMetrics;
        this.deploymentInfo = builder.deploymentInfo;
        this.dryRun = builder.dryRun;
    }
    
    public String getSystemState() {
        return systemState;
    }
    
    public String getTargetMetrics() {
        return targetMetrics;
    }
    
    public String getDeploymentInfo() {
        return deploymentInfo;
    }
    
    public boolean isDryRun() {
        return dryRun;
    }
    
    public static class Builder {
        private String systemState = "";
        private String targetMetrics = "";
        private String deploymentInfo = "";
        private boolean dryRun = false;
        
        public Builder withSystemState(String state) {
            this.systemState = state;
            return this;
        }
        
        public Builder withTargetMetrics(String metrics) {
            this.targetMetrics = metrics;
            return this;
        }
        
        public Builder withDeploymentInfo(String info) {
            this.deploymentInfo = info;
            return this;
        }
        
        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }
        
        public HealingContext build() {
            return new HealingContext(this);
        }
    }
}

