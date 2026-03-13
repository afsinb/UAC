package com.blacklight.uac.core.events;

/**
 * Event priority levels for signal processing and routing.
 */
public enum EventPriority {
    CRITICAL(4),      // System down, immediate action required
    HIGH(3),          // Degraded performance, urgent
    MEDIUM(2),        // Warning level, monitor
    LOW(1);           // Informational
    
    private final int level;
    
    EventPriority(int level) {
        this.level = level;
    }
    
    public int getLevel() { return level; }
    
    public static EventPriority fromHealthScore(double healthScore) {
        if (healthScore < 0.25) return CRITICAL;
        if (healthScore < 0.50) return HIGH;
        if (healthScore < 0.75) return MEDIUM;
        return LOW;
    }
}

