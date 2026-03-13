package com.blacklight.uac.observer;

public class LogEntry {
        private String level;
        private String message;
        private long timestamp;
        private String source;
        
        public LogEntry(String level, String message, String source) {
            this.level = level;
            this.message = message;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }