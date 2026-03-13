package com.blacklight.uac.ai.models;

import java.util.*;

/**
 * AIModel - Interface for pluggable AI/LLM models
 * Supports multiple model backends (OpenAI, Anthropic, Local, etc.)
 */
public interface AIModel {
    
    /**
     * Get model name/identifier
     */
    String getModelName();
    
    /**
     * Get model capabilities
     */
    List<String> getCapabilities();
    
    /**
     * Generate a response for a given prompt
     */
    AIResponse generate(String prompt, Map<String, Object> context);
    
    /**
     * Analyze code and provide insights
     */
    AIResponse analyzeCode(String code, String language);
    
    /**
     * Generate a fix for an anomaly
     */
    AIResponse generateFix(String anomalyType, String code, String stackTrace);
    
    /**
     * Check if model is available
     */
    boolean isAvailable();
    
    /**
     * Get model priority (higher = preferred)
     */
    int getPriority();
    
    /**
     * AI Response wrapper
     */
    class AIResponse {
        private final boolean success;
        private final String content;
        private final Map<String, Object> metadata;
        private final double confidence;
        private final String modelUsed;
        private final long responseTime;
        
        public AIResponse(boolean success, String content, Map<String, Object> metadata,
                         double confidence, String modelUsed, long responseTime) {
            this.success = success;
            this.content = content;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.confidence = confidence;
            this.modelUsed = modelUsed;
            this.responseTime = responseTime;
        }
        
        public static AIResponse success(String content, String modelUsed, long responseTime) {
            return new AIResponse(true, content, new HashMap<>(), 0.9, modelUsed, responseTime);
        }
        
        public static AIResponse error(String error, String modelUsed) {
            return new AIResponse(false, error, new HashMap<>(), 0.0, modelUsed, 0);
        }
        
        public boolean isSuccess() { return success; }
        public String getContent() { return content; }
        public Map<String, Object> getMetadata() { return metadata; }
        public double getConfidence() { return confidence; }
        public String getModelUsed() { return modelUsed; }
        public long getResponseTime() { return responseTime; }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
    }
}
