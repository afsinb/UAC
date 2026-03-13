package com.blacklight.uac.evolver;

import java.util.ArrayList;
import java.util.List;

public class DevelopmentTask {
        private String taskType; // "clone", "reproduce", "patch", "verify", "pr"
        private String sourceCode;
        private List<String> logs;
        private String patchContent;
        private boolean verified;
        
        public DevelopmentTask(String taskType) {
            this.taskType = taskType;
            this.logs = new ArrayList<>();
            this.verified = false;
        }
        
        // Getters and setters
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
        public List<String> getLogs() { return logs; }
        public void setLogs(List<String> logs) { this.logs = logs; }
        public String getPatchContent() { return patchContent; }
        public void setPatchContent(String patchContent) { this.patchContent = patchContent; }
        public boolean isVerified() { return verified; }
        public void setVerified(boolean verified) { this.verified = verified; }
    }