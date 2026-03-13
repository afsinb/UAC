package com.blacklight.uac.ai.models;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * ExternalLLMModel - Base class for external LLM integrations
 * Supports OpenAI, Anthropic, and other LLM APIs
 */
public abstract class ExternalLLMModel implements AIModel {
    
    protected final String apiKey;
    protected final String modelName;
    protected final String baseUrl;
    protected final HttpClient httpClient;
    protected final int timeoutSeconds;
    
    public ExternalLLMModel(String apiKey, String modelName, String baseUrl, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .build();
    }
    
    @Override
    public String getModelName() {
        return modelName;
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "code_analysis",
            "fix_generation",
            "anomaly_classification",
            "natural_language",
            "code_review",
            "refactoring"
        );
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
    
    @Override
    public int getPriority() {
        return 10; // Highest priority for external LLMs
    }
    
    @Override
    public AIResponse generate(String prompt, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        
        try {
            String response = callLLMAPI(prompt, context);
            long responseTime = System.currentTimeMillis() - startTime;
            
            AIResponse aiResponse = AIResponse.success(response, modelName, responseTime);
            aiResponse.addMetadata("source", "external_llm");
            aiResponse.addMetadata("model", modelName);
            
            return aiResponse;
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            AIResponse errorResponse = AIResponse.error("LLM API error: " + e.getMessage(), modelName);
            errorResponse.addMetadata("error", e.getClass().getSimpleName());
            return errorResponse;
        }
    }
    
    @Override
    public AIResponse analyzeCode(String code, String language) {
        String prompt = String.format("""
            Analyze the following %s code for potential issues, vulnerabilities, and improvements.
            
            Code:
            ```%s
            %s
            ```
            
            Provide:
            1. List of issues found
            2. Severity for each issue
            3. Recommended fixes
            4. Code quality score (0-100)
            """, language, language, code);
        
        Map<String, Object> context = new HashMap<>();
        context.put("task", "code_analysis");
        context.put("language", language);
        
        return generate(prompt, context);
    }
    
    @Override
    public AIResponse generateFix(String anomalyType, String code, String stackTrace) {
        String prompt = String.format("""
            Fix the following %s in the code.
            
            Original Code:
            ```java
            %s
            ```
            
            Stack Trace:
            %s
            
            Provide:
            1. Root cause analysis
            2. Fixed code
            3. Explanation of the fix
            4. Any additional recommendations
            """, anomalyType, code, stackTrace);
        
        Map<String, Object> context = new HashMap<>();
        context.put("task", "fix_generation");
        context.put("anomalyType", anomalyType);
        
        return generate(prompt, context);
    }
    
    /**
     * Call the external LLM API - implemented by subclasses
     */
    protected abstract String callLLMAPI(String prompt, Map<String, Object> context) throws Exception;
    
    /**
     * Build the request body - implemented by subclasses
     */
    protected abstract String buildRequestBody(String prompt, Map<String, Object> context);
    
    /**
     * Parse the response - implemented by subclasses
     */
    protected abstract String parseResponse(String responseBody);
}
