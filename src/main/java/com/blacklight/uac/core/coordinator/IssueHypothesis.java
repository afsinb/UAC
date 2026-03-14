package com.blacklight.uac.core.coordinator;

/**
 * IssueHypothesis - Represents the analysis result of an anomaly
 * Determines whether an issue is Operational (infrastructure) or Logical (code defect)
 */
public class IssueHypothesis {
    
    public enum IssueType {
        OPERATIONAL,
        LOGICAL
    }
    
    private final IssueType issueType;
    private final double confidence;
    private final String reasoning;
    
    public IssueHypothesis(IssueType issueType, double confidence, String reasoning) {
        this.issueType = issueType;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }
    
    public IssueType getIssueType() {
        return issueType;
    }

    /** Convenience method: returns true when the issue type is OPERATIONAL. */
    public boolean isOperational() {
        return IssueType.OPERATIONAL == issueType;
    }

    /** Convenience method: returns true when the issue type is LOGICAL. */
    public boolean isLogical() {
        return IssueType.LOGICAL == issueType;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    @Override
    public String toString() {
        return String.format("IssueHypothesis{type=%s, confidence=%.2f, reasoning='%s'}", 
            issueType, confidence, reasoning);
    }
}

