package com.blacklight.uac.ai.models;

import java.util.*;

/**
 * RuleBasedModel - Built-in rule-based AI model
 * Uses predefined rules for common patterns
 */
public class RuleBasedModel implements AIModel {
    
    @Override
    public String getModelName() {
        return "RuleBased";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "code_analysis",
            "fix_generation",
            "anomaly_classification",
            "pattern_detection"
        );
    }
    
    @Override
    public AIResponse generate(String prompt, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        
        // Simple rule-based response generation
        String response = applyRules(prompt, context);
        long responseTime = System.currentTimeMillis() - startTime;
        
        return AIResponse.success(response, getModelName(), responseTime);
    }
    
    @Override
    public AIResponse analyzeCode(String code, String language) {
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("model", getModelName());
        analysis.put("language", language);
        analysis.put("lines", code.split("\n").length);
        analysis.put("hasNullChecks", code.contains("!= null"));
        analysis.put("hasTryCatch", code.contains("try") && code.contains("catch"));
        analysis.put("hasValidation", code.contains("validate") || code.contains("check"));
        
        long responseTime = System.currentTimeMillis() - startTime;
        AIResponse response = AIResponse.success(analysis.toString(), getModelName(), responseTime);
        response.addMetadata("analysis", analysis);
        
        return response;
    }
    
    @Override
    public AIResponse generateFix(String anomalyType, String code, String stackTrace) {
        long startTime = System.currentTimeMillis();
        
        String fix = switch (anomalyType) {
            case "NULL_POINTER_EXCEPTION" -> generateNPEFix(code);
            case "ARRAY_INDEX_OUT_OF_BOUNDS" -> generateArrayBoundsFix(code);
            case "CLASS_CAST_EXCEPTION" -> generateClassCastFix(code);
            case "ILLEGAL_ARGUMENT" -> generateValidationFix(code);
            default -> "// No automated fix available for " + anomalyType;
        };
        
        long responseTime = System.currentTimeMillis() - startTime;
        AIResponse response = AIResponse.success(fix, getModelName(), responseTime);
        response.addMetadata("anomalyType", anomalyType);
        response.addMetadata("fixType", "rule_based");
        
        return response;
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Always available
    }
    
    @Override
    public int getPriority() {
        return 1; // Lowest priority (fallback)
    }
    
    private String applyRules(String prompt, Map<String, Object> context) {
        // Simple keyword-based rule matching
        String lowerPrompt = prompt.toLowerCase();
        
        if (lowerPrompt.contains("null") || lowerPrompt.contains("npe")) {
            return "Recommendation: Add null check before accessing the object.";
        } else if (lowerPrompt.contains("array") || lowerPrompt.contains("index")) {
            return "Recommendation: Add bounds check before array access.";
        } else if (lowerPrompt.contains("cast") || lowerPrompt.contains("type")) {
            return "Recommendation: Add instanceof check before casting.";
        } else if (lowerPrompt.contains("timeout") || lowerPrompt.contains("connection")) {
            return "Recommendation: Implement retry logic with exponential backoff.";
        } else if (lowerPrompt.contains("memory") || lowerPrompt.contains("oom")) {
            return "Recommendation: Increase heap size or optimize memory usage.";
        }
        
        return "No specific recommendation available. Manual review suggested.";
    }
    
    private String generateNPEFix(String code) {
        return """
            // Fix for NullPointerException
            if (object != null) {
                // Original code
                object.method();
            } else {
                logger.warn("Object is null, skipping operation");
                return defaultValue;
            }
            """;
    }
    
    private String generateArrayBoundsFix(String code) {
        return """
            // Fix for ArrayIndexOutOfBoundsException
            if (index >= 0 && index < array.length) {
                // Original code
                array[index];
            } else {
                logger.warn("Index " + index + " out of bounds");
                return defaultValue;
            }
            """;
    }
    
    private String generateClassCastFix(String code) {
        return """
            // Fix for ClassCastException
            if (object instanceof TargetType) {
                TargetType typed = (TargetType) object;
                // Original code
            } else {
                logger.warn("Cannot cast to TargetType");
                return defaultValue;
            }
            """;
    }
    
    private String generateValidationFix(String code) {
        return """
            // Fix for IllegalArgumentException
            if (argument != null && isValid(argument)) {
                // Original code
            } else {
                throw new IllegalArgumentException("Invalid argument: " + argument);
            }
            """;
    }
}
