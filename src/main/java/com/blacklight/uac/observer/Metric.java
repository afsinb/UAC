package com.blacklight.uac.observer;

public class Metric {
        private String name;
        private double value;
        private long timestamp;
        
        public Metric(String name, double value) {
            this.name = name;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }