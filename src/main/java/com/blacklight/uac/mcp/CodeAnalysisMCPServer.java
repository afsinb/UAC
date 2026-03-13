package com.blacklight.uac.mcp;

import java.util.*;

/**
 * CodeAnalysisMCPServer - MCP server for code analysis
 * Provides AI-powered code analysis capabilities
 */
public class CodeAnalysisMCPServer implements MCPServer {
    
    @Override
    public String getName() {
        return "CodeAnalysis";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "analyze_code",
            "find_vulnerabilities",
            "suggest_fixes",
            "calculate_complexity",
            "detect_patterns"
        );
    }
    
    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "analyze_code" -> analyzeCode(request);
            case "find_vulnerabilities" -> findVulnerabilities(request);
            case "suggest_fixes" -> suggestFixes(request);
            case "calculate_complexity" -> calculateComplexity(request);
            case "detect_patterns" -> detectPatterns(request);
            default -> MCPResponse.error(request.getId(), "Unknown method: " + request.getMethod());
        };
    }
    
    @Override
    public boolean isHealthy() {
        return true;
    }
    
    private MCPResponse analyzeCode(MCPRequest request) {
        String code = request.getStringParam("code");
        if (code == null) {
            return MCPResponse.error(request.getId(), "Missing 'code' parameter");
        }
        
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("lines", code.split("\n").length);
        analysis.put("complexity", calculateCyclomaticComplexity(code));
        analysis.put("hasNullChecks", code.contains("!= null"));
        analysis.put("hasBoundsChecks", code.contains(".length"));
        analysis.put("hasTypeChecks", code.contains("instanceof"));
        analysis.put("hasErrorHandling", code.contains("try") && code.contains("catch"));
        
        MCPResponse response = MCPResponse.success(request.getId(), analysis);
        response.addMetadata("analyzer", "CodeAnalysisMCPServer");
        response.addMetadata("version", "1.0");
        
        return response;
    }
    
    private MCPResponse findVulnerabilities(MCPRequest request) {
        String code = request.getStringParam("code");
        if (code == null) {
            return MCPResponse.error(request.getId(), "Missing 'code' parameter");
        }
        
        List<Map<String, Object>> vulnerabilities = new ArrayList<>();
        
        // Check for potential NPE
        if (code.matches(".*\\w+\\.\\w+\\(.*\\).*") && !code.contains("!= null")) {
            Map<String, Object> vuln = new HashMap<>();
            vuln.put("type", "NULL_POINTER_RISK");
            vuln.put("severity", "HIGH");
            vuln.put("description", "Potential NullPointerException without null check");
            vuln.put("recommendation", "Add null check before method call");
            vulnerabilities.add(vuln);
        }
        
        // Check for array access without bounds check
        if (code.matches(".*\\w+\\[\\w+\\].*") && !code.contains(".length")) {
            Map<String, Object> vuln = new HashMap<>();
            vuln.put("type", "ARRAY_BOUNDS_RISK");
            vuln.put("severity", "MEDIUM");
            vuln.put("description", "Array access without bounds validation");
            vuln.put("recommendation", "Add bounds check before array access");
            vulnerabilities.add(vuln);
        }
        
        // Check for unsafe casts
        if (code.matches(".*\\([A-Z]\\w+\\)\\s*\\w+.*") && !code.contains("instanceof")) {
            Map<String, Object> vuln = new HashMap<>();
            vuln.put("type", "UNSAFE_CAST");
            vuln.put("severity", "MEDIUM");
            vuln.put("description", "Type cast without instanceof check");
            vuln.put("recommendation", "Add instanceof check before casting");
            vulnerabilities.add(vuln);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("vulnerabilities", vulnerabilities);
        result.put("count", vulnerabilities.size());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse suggestFixes(MCPRequest request) {
        String code = request.getStringParam("code");
        String vulnerabilityType = request.getStringParam("vulnerabilityType");
        
        if (code == null || vulnerabilityType == null) {
            return MCPResponse.error(request.getId(), "Missing required parameters");
        }
        
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        switch (vulnerabilityType) {
            case "NULL_POINTER_RISK":
                suggestions.add(createSuggestion(
                    "Add null check",
                    "Wrap the potentially null access in an if statement",
                    "if (variable != null) { /* original code */ }"
                ));
                suggestions.add(createSuggestion(
                    "Use Optional",
                    "Use Optional to handle null safely",
                    "Optional.ofNullable(variable).ifPresent(v -> /* code */)"
                ));
                break;
                
            case "ARRAY_BOUNDS_RISK":
                suggestions.add(createSuggestion(
                    "Add bounds check",
                    "Validate index before array access",
                    "if (index >= 0 && index < array.length) { /* access */ }"
                ));
                break;
                
            case "UNSAFE_CAST":
                suggestions.add(createSuggestion(
                    "Add instanceof check",
                    "Check type before casting",
                    "if (obj instanceof Type) { Type t = (Type) obj; }"
                ));
                break;
                
            default:
                return MCPResponse.error(request.getId(), "Unknown vulnerability type");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("suggestions", suggestions);
        result.put("count", suggestions.size());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse calculateComplexity(MCPRequest request) {
        String code = request.getStringParam("code");
        if (code == null) {
            return MCPResponse.error(request.getId(), "Missing 'code' parameter");
        }
        
        int complexity = calculateCyclomaticComplexity(code);
        
        Map<String, Object> result = new HashMap<>();
        result.put("cyclomaticComplexity", complexity);
        result.put("rating", complexity <= 5 ? "LOW" : (complexity <= 10 ? "MEDIUM" : "HIGH"));
        result.put("recommendation", complexity > 10 ? "Consider refactoring to reduce complexity" : "Complexity is acceptable");
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse detectPatterns(MCPRequest request) {
        String code = request.getStringParam("code");
        if (code == null) {
            return MCPResponse.error(request.getId(), "Missing 'code' parameter");
        }
        
        List<String> patterns = new ArrayList<>();
        
        if (code.contains("try") && code.contains("catch")) patterns.add("TRY_CATCH");
        if (code.contains("if") && code.contains("else")) patterns.add("CONDITIONAL");
        if (code.contains("for") || code.contains("while")) patterns.add("LOOP");
        if (code.contains("synchronized")) patterns.add("SYNCHRONIZED");
        if (code.contains("volatile")) patterns.add("VOLATILE");
        if (code.contains("@Override")) patterns.add("OVERRIDE");
        if (code.contains("interface")) patterns.add("INTERFACE");
        if (code.contains("abstract")) patterns.add("ABSTRACT");
        
        Map<String, Object> result = new HashMap<>();
        result.put("patterns", patterns);
        result.put("count", patterns.size());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private int calculateCyclomaticComplexity(String code) {
        int complexity = 1; // Base complexity
        
        // Count decision points
        complexity += countOccurrences(code, "if ");
        complexity += countOccurrences(code, "else if");
        complexity += countOccurrences(code, "for ");
        complexity += countOccurrences(code, "while ");
        complexity += countOccurrences(code, "case ");
        complexity += countOccurrences(code, "catch ");
        complexity += countOccurrences(code, "&&");
        complexity += countOccurrences(code, "||");
        complexity += countOccurrences(code, "?"); // Ternary operator
        
        return complexity;
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
    
    private Map<String, Object> createSuggestion(String title, String description, String codeExample) {
        Map<String, Object> suggestion = new HashMap<>();
        suggestion.put("title", title);
        suggestion.put("description", description);
        suggestion.put("codeExample", codeExample);
        return suggestion;
    }
}
