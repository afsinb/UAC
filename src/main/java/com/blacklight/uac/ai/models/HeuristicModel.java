package com.blacklight.uac.ai.models;

import java.util.*;

/**
 * HeuristicModel - AI model using heuristics and scoring
 * Makes decisions based on weighted heuristics
 */
public class HeuristicModel implements AIModel {
    
    private final Map<String, Double> anomalyWeights;
    private final Map<String, String> fixStrategies;
    private final Map<String, Integer> successCounts;
    private final Map<String, Integer> failureCounts;
    
    public HeuristicModel() {
        this.anomalyWeights = new HashMap<>();
        this.fixStrategies = new HashMap<>();
        this.successCounts = new HashMap<>();
        this.failureCounts = new HashMap<>();
        initializeHeuristics();
    }
    
    @Override
    public String getModelName() {
        return "Heuristic";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "code_analysis",
            "fix_generation",
            "decision_making",
            "risk_assessment",
            "priority_scoring"
        );
    }
    
    @Override
    public AIResponse generate(String prompt, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        
        String response = applyHeuristics(prompt, context);
        double confidence = calculateConfidence(prompt);
        
        long responseTime = System.currentTimeMillis() - startTime;
        AIResponse aiResponse = AIResponse.success(response, getModelName(), responseTime);
        aiResponse.addMetadata("confidence", confidence);
        
        return aiResponse;
    }
    
    @Override
    public AIResponse analyzeCode(String code, String language) {
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> analysis = new HashMap<>();
        
        // Calculate various scores
        double complexityScore = calculateComplexityScore(code);
        double maintainabilityScore = calculateMaintainabilityScore(code);
        double riskScore = calculateRiskScore(code);
        
        analysis.put("complexityScore", complexityScore);
        analysis.put("maintainabilityScore", maintainabilityScore);
        analysis.put("riskScore", riskScore);
        analysis.put("overallScore", (complexityScore + maintainabilityScore + (1 - riskScore)) / 3);
        
        // Determine recommendations
        List<String> recommendations = new ArrayList<>();
        if (complexityScore > 0.7) {
            recommendations.add("Consider refactoring to reduce complexity");
        }
        if (maintainabilityScore < 0.5) {
            recommendations.add("Add documentation and improve naming");
        }
        if (riskScore > 0.6) {
            recommendations.add("Add error handling and validation");
        }
        analysis.put("recommendations", recommendations);
        
        long responseTime = System.currentTimeMillis() - startTime;
        AIResponse response = AIResponse.success(analysis.toString(), getModelName(), responseTime);
        response.addMetadata("analysis", analysis);
        
        return response;
    }
    
    @Override
    public AIResponse generateFix(String anomalyType, String code, String stackTrace) {
        long startTime = System.currentTimeMillis();
        
        // Get fix strategy based on anomaly type
        String strategy = fixStrategies.getOrDefault(anomalyType, "MANUAL_REVIEW");
        
        // Calculate confidence based on historical success
        double confidence = calculateFixConfidence(anomalyType);
        
        String fix = generateFixByStrategy(strategy, code, anomalyType);
        
        long responseTime = System.currentTimeMillis() - startTime;
        AIResponse response = AIResponse.success(fix, getModelName(), responseTime);
        response.addMetadata("strategy", strategy);
        response.addMetadata("confidence", confidence);
        response.addMetadata("anomalyType", anomalyType);
        
        return response;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 3; // Highest priority among built-in models
    }
    
    /**
     * Record fix outcome for learning
     */
    public void recordOutcome(String anomalyType, boolean success) {
        if (success) {
            successCounts.merge(anomalyType, 1, Integer::sum);
        } else {
            failureCounts.merge(anomalyType, 1, Integer::sum);
        }
    }
    
    private void initializeHeuristics() {
        // Anomaly severity weights
        anomalyWeights.put("NULL_POINTER_EXCEPTION", 0.8);
        anomalyWeights.put("ARRAY_INDEX_OUT_OF_BOUNDS", 0.7);
        anomalyWeights.put("CLASS_CAST_EXCEPTION", 0.6);
        anomalyWeights.put("ILLEGAL_ARGUMENT", 0.5);
        anomalyWeights.put("OUT_OF_MEMORY_ERROR", 0.95);
        anomalyWeights.put("STACK_OVERFLOW_ERROR", 0.9);
        anomalyWeights.put("CONNECTION_TIMEOUT", 0.75);
        anomalyWeights.put("DATABASE_ERROR", 0.85);
        
        // Fix strategies
        fixStrategies.put("NULL_POINTER_EXCEPTION", "ADD_NULL_CHECK");
        fixStrategies.put("ARRAY_INDEX_OUT_OF_BOUNDS", "ADD_BOUNDS_CHECK");
        fixStrategies.put("CLASS_CAST_EXCEPTION", "ADD_TYPE_CHECK");
        fixStrategies.put("ILLEGAL_ARGUMENT", "ADD_VALIDATION");
        fixStrategies.put("CONNECTION_TIMEOUT", "ADD_RETRY");
        fixStrategies.put("DATABASE_ERROR", "ADD_CONNECTION_POOL");
        fixStrategies.put("OUT_OF_MEMORY_ERROR", "INCREASE_HEAP");
        fixStrategies.put("STACK_OVERFLOW_ERROR", "OPTIMIZE_RECURSION");
    }
    
    private String applyHeuristics(String prompt, Map<String, Object> context) {
        double score = 0.0;
        String recommendation = "No specific recommendation";
        
        // Score based on keywords
        String lowerPrompt = prompt.toLowerCase();
        
        for (Map.Entry<String, Double> entry : anomalyWeights.entrySet()) {
            if (lowerPrompt.contains(entry.getKey().toLowerCase().replace("_", " "))) {
                score = Math.max(score, entry.getValue());
                recommendation = "Detected " + entry.getKey() + " with severity weight " + entry.getValue();
            }
        }
        
        return recommendation + " (score: " + String.format("%.2f", score) + ")";
    }
    
    private double calculateConfidence(String prompt) {
        // Simple confidence calculation based on keyword matches
        int matches = 0;
        String lowerPrompt = prompt.toLowerCase();
        
        for (String keyword : anomalyWeights.keySet()) {
            if (lowerPrompt.contains(keyword.toLowerCase().replace("_", " "))) {
                matches++;
            }
        }
        
        return Math.min(0.95, 0.5 + (matches * 0.15));
    }
    
    private double calculateComplexityScore(String code) {
        int complexity = 1;
        complexity += countOccurrences(code, "if ");
        complexity += countOccurrences(code, "for ");
        complexity += countOccurrences(code, "while ");
        complexity += countOccurrences(code, "switch ");
        complexity += countOccurrences(code, "catch ");
        
        return Math.min(1.0, complexity / 20.0);
    }
    
    private double calculateMaintainabilityScore(String code) {
        double score = 0.5;
        
        // Positive factors
        if (code.contains("//") || code.contains("/*")) score += 0.1; // Has comments
        if (code.contains("@Override")) score += 0.05; // Uses annotations
        if (code.contains("private") || code.contains("protected")) score += 0.1; // Encapsulation
        
        // Negative factors
        if (code.length() > 500) score -= 0.1; // Long methods
        if (countOccurrences(code, "if ") > 5) score -= 0.1; // Complex logic
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    private double calculateRiskScore(String code) {
        double risk = 0.0;
        
        if (!code.contains("try") && !code.contains("catch")) risk += 0.2; // No error handling
        if (code.contains("null") && !code.contains("!= null")) risk += 0.3; // Null risk
        if (code.matches(".*\\w+\\[\\w+\\].*") && !code.contains(".length")) risk += 0.2; // Array risk
        if (code.contains("Thread.sleep")) risk += 0.1; // Blocking calls
        
        return Math.min(1.0, risk);
    }
    
    private double calculateFixConfidence(String anomalyType) {
        int successes = successCounts.getOrDefault(anomalyType, 0);
        int failures = failureCounts.getOrDefault(anomalyType, 0);
        int total = successes + failures;
        
        if (total == 0) return 0.7; // Default confidence
        
        return 0.5 + ((double) successes / total) * 0.5;
    }
    
    private String generateFixByStrategy(String strategy, String code, String anomalyType) {
        return switch (strategy) {
            case "ADD_NULL_CHECK" -> "// Heuristic fix: Add null check\nif (obj != null) { /* original */ }";
            case "ADD_BOUNDS_CHECK" -> "// Heuristic fix: Add bounds check\nif (i >= 0 && i < arr.length) { /* original */ }";
            case "ADD_TYPE_CHECK" -> "// Heuristic fix: Add type check\nif (obj instanceof Type) { /* original */ }";
            case "ADD_VALIDATION" -> "// Heuristic fix: Add validation\nif (isValid(arg)) { /* original */ }";
            case "ADD_RETRY" -> "// Heuristic fix: Add retry logic\nretryWithBackoff(() -> /* original */, 3, 1000);";
            case "ADD_CONNECTION_POOL" -> "// Heuristic fix: Use connection pool\npool.getConnection();";
            case "INCREASE_HEAP" -> "// Heuristic fix: Increase heap\n// JVM: -Xmx2g";
            case "OPTIMIZE_RECURSION" -> "// Heuristic fix: Convert to iteration\n// Use stack/queue instead of recursion";
            default -> "// No automated fix available";
        };
    }
    
    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
