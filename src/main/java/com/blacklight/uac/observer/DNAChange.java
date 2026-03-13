package com.blacklight.uac.observer;

import java.util.ArrayList;
import java.util.List;

public class DNAChange {
        private String commitId;
        private String author;
        private String message;
        private long timestamp;
        private List<String> changedFiles;
        
        public DNAChange(String commitId, String author, String message) {
            this.commitId = commitId;
            this.author = author;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.changedFiles = new ArrayList<>();
        }
        
        // Getters and setters
        public String getCommitId() { return commitId; }
        public void setCommitId(String commitId) { this.commitId = commitId; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public List<String> getChangedFiles() { return changedFiles; }
        public void setChangedFiles(List<String> changedFiles) { this.changedFiles = changedFiles; }
    }