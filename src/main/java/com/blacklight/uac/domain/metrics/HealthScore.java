package com.blacklight.uac.domain.metrics;

import java.util.Objects;

/**
 * HealthScore - Domain Value Object
 * Represents normalized system health (0.0 to 1.0)
 */
public class HealthScore {
    private final double score;
    private final long timestamp;
    private final String source;
    
    public HealthScore(double score, String source) {
        this.score = normalizScore(score);
        this.timestamp = System.currentTimeMillis();
        this.source = Objects.requireNonNull(source);
    }
    
    public HealthScore(double score, long timestamp, String source) {
        this.score = normalizScore(score);
        this.timestamp = timestamp;
        this.source = Objects.requireNonNull(source);
    }
    
    private static double normalizScore(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    public double getScore() {
        return score;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getSource() {
        return source;
    }
    
    public boolean isHealthy() {
        return score >= 0.8;
    }
    
    public boolean isDegraded() {
        return score >= 0.5 && score < 0.8;
    }
    
    public boolean isCritical() {
        return score < 0.5;
    }
    
    public HealthStatus getStatus() {
        if (isHealthy()) return HealthStatus.HEALTHY;
        if (isDegraded()) return HealthStatus.DEGRADED;
        return HealthStatus.CRITICAL;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HealthScore)) return false;
        HealthScore that = (HealthScore) o;
        return Double.compare(that.score, score) == 0 &&
               source.equals(that.source);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(score, source);
    }
    
    @Override
    public String toString() {
        return String.format("HealthScore{score=%.2f, status=%s, source='%s'}", 
            score, getStatus(), source);
    }
}

