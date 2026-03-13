package com.blacklight.uac.ai.models;

import java.util.*;
import java.util.regex.*;

/**
 * PatternMatchingModel - AI model using pattern matching
 * Recognizes common code patterns and suggests fixes
 */
public class PatternMatchingModel implements AIModel {
    
    private final Map<Pattern, String> vulnerabilityPatterns;
    private final Map<Pattern, String> fixPatterns;
    
    public PatternMatchingModel() {
        this.vulnerabilityPatterns = new LinkedHashMap<>();
        this.fixPatterns = new LinkedHashMap<>();
        initializePatterns();
    }
    
    @Override
    public String getModelName() {
        return "PatternMatching";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "code_analysis",
            "fix_generation",
            "vulnerability_detection",
            "pattern_recognition",
            "code_similarity"
        );
    }
    
    @Override
    public AIResponse generate(String prompt, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        
        String response = matchPattern(prompt, context);
        long responseTime = System.currentTimeMillis() - startTime;
        
        return AIResponse.success(response, getModelName(), responseTime);
    }
    
    @Override
    public AIResponse analyzeCode(String code, String language) {
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> analysis = new HashMap<>();
        List<String> detectedPatterns = new ArrayList<>();
        List<Map<String, String>> vulnerabilities = new ArrayList<>();
        
        // Detect patterns
        for (Map.Entry<Pattern, String> entry : vulnerabilityPatterns.entrySet()) {
            if (entry.getKey().matcher(code).find()) {
                detectedPatterns.add(entry.getValue());
                
                Map<String, String> vuln = new HashMap<>();
                vuln.put("pattern", entry.getValue());
                vuln.put("severity", "MEDIUM");
                vulnerabilities.add(vuln);
            }
        }
        
        analysis.put("detectedPatterns", detectedPatterns);
        analysis.put("vulnerabilities", vulnerabilities);
        analysis.put("patternCount", detectedPatterns.size());
        
        long responseTime = System.currentTimeMillis() - startTime;
        AIResponse response = AIResponse.success(analysis.toString(), getModelName(), responseTime);
        response.addMetadata("analysis", analysis);
        response.addMetadata("confidence", detectedPatterns.isEmpty() ? 0.95 : 0.85);
        
        return response;
    }
    
    @Override
    public AIResponse generateFix(String anomalyType, String code, String stackTrace) {
        long startTime = System.currentTimeMillis();
        
        String fix = findMatchingFix(code);
        double confidence = fix != null ? 0.85 : 0.0;
        
        long responseTime = System.currentTimeMillis() - startTime;
        AIResponse response = fix != null ?
            AIResponse.success(fix, getModelName(), responseTime) :
            AIResponse.error("No pattern match found", getModelName());
        
        response.addMetadata("anomalyType", anomalyType);
        response.addMetadata("fixType", "pattern_match");
        response.addMetadata("confidence", confidence);
        
        return response;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 2; // Medium priority
    }
    
    private void initializePatterns() {
        // Vulnerability patterns
        vulnerabilityPatterns.put(
            Pattern.compile("\\w+\\.\\w+\\([^)]*\\)(?!\\s*!=\\s*null)"),
            "POTENTIAL_NPE"
        );
        vulnerabilityPatterns.put(
            Pattern.compile("\\w+\\[\\w+\\](?!\\s*<\\s*\\w+\\.length)"),
            "UNSAFE_ARRAY_ACCESS"
        );
        vulnerabilityPatterns.put(
            Pattern.compile("\\([A-Z]\\w+\\)\\s*\\w+(?!\\s*instanceof)"),
            "UNSAFE_CAST"
        );
        vulnerabilityPatterns.put(
            Pattern.compile("new\\s+\\w+\\[\\d{4,}\\]"),
            "LARGE_ALLOCATION"
        );
        vulnerabilityPatterns.put(
            Pattern.compile("Thread\\.sleep\\(\\d{5,}\\)"),
            "LONG_SLEEP"
        );
        
        // Fix patterns
        fixPatterns.put(
            Pattern.compile("(\\w+)\\.(\\w+)\\("),
            "if ($1 != null) { $1.$2(); }"
        );
        fixPatterns.put(
            Pattern.compile("(\\w+)\\[(\\w+)\\]"),
            "($2 >= 0 && $2 < $1.length) ? $1[$2] : null"
        );
        fixPatterns.put(
            Pattern.compile("\\(([A-Z]\\w+)\\)\\s*(\\w+)"),
            "($2 instanceof $1) ? ($1) $2 : null"
        );
    }
    
    private String matchPattern(String prompt, Map<String, Object> context) {
        String lowerPrompt = prompt.toLowerCase();
        
        for (Map.Entry<Pattern, String> entry : vulnerabilityPatterns.entrySet()) {
            if (entry.getKey().matcher(prompt).find()) {
                return "Detected pattern: " + entry.getValue() + ". Recommendation: Apply corresponding fix.";
            }
        }
        
        return "No matching pattern found.";
    }
    
    private String findMatchingFix(String code) {
        for (Map.Entry<Pattern, String> entry : fixPatterns.entrySet()) {
            Matcher matcher = entry.getKey().matcher(code);
            if (matcher.find()) {
                return "// Pattern-based fix\n" + matcher.replaceAll(entry.getValue());
            }
        }
        return null;
    }
}
