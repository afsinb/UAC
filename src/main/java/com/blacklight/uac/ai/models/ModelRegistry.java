package com.blacklight.uac.ai.models;

import java.util.*;
import java.util.concurrent.*;

/**
 * ModelRegistry - Dynamic registry for AI models
 * Manages model selection, fallback, and load balancing
 */
public class ModelRegistry {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final Map<String, AIModel> models;
    private final Map<String, ModelStats> modelStats;
    private final Map<String, String> capabilityMapping;
    private String defaultModel;
    
    public ModelRegistry() {
        this.models = new ConcurrentHashMap<>();
        this.modelStats = new ConcurrentHashMap<>();
        this.capabilityMapping = new ConcurrentHashMap<>();
        initializeDefaultModels();
    }
    
    /**
     * Model performance statistics
     */
    public static class ModelStats {
        private int totalRequests;
        private int successfulRequests;
        private long totalResponseTime;
        private double averageConfidence;
        private long lastUsed;
        
        public ModelStats() {
            this.totalRequests = 0;
            this.successfulRequests = 0;
            this.totalResponseTime = 0;
            this.averageConfidence = 0.0;
            this.lastUsed = 0;
        }
        
        public synchronized void recordRequest(boolean success, long responseTime, double confidence) {
            totalRequests++;
            if (success) successfulRequests++;
            totalResponseTime += responseTime;
            averageConfidence = (averageConfidence * (totalRequests - 1) + confidence) / totalRequests;
            lastUsed = System.currentTimeMillis();
        }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
        }
        
        public long getAverageResponseTime() {
            return totalRequests > 0 ? totalResponseTime / totalRequests : 0;
        }
        
        public int getTotalRequests() { return totalRequests; }
        public double getAverageConfidence() { return averageConfidence; }
        public long getLastUsed() { return lastUsed; }
    }
    
    /**
     * Register a model
     */
    public void registerModel(AIModel model) {
        models.put(model.getModelName(), model);
        modelStats.put(model.getModelName(), new ModelStats());
        
        // Map capabilities to model
        for (String capability : model.getCapabilities()) {
            capabilityMapping.put(capability, model.getModelName());
        }
        
        // Set as default if first or higher priority
        if (defaultModel == null || model.getPriority() > getModel(defaultModel).getPriority()) {
            defaultModel = model.getModelName();
        }
        
        System.out.println(GREEN + "  ✓ Registered AI model: " + model.getModelName() + 
                          " (priority: " + model.getPriority() + ")" + RESET);
    }
    
    /**
     * Get model by name
     */
    public AIModel getModel(String modelName) {
        return models.get(modelName);
    }
    
    /**
     * Get best model for a capability
     */
    public AIModel getBestModel(String capability) {
        // Try capability-specific model first
        String mappedModel = capabilityMapping.get(capability);
        if (mappedModel != null) {
            AIModel model = models.get(mappedModel);
            if (model != null && model.isAvailable()) {
                return model;
            }
        }
        
        // Find best available model with this capability
        return models.values().stream()
            .filter(AIModel::isAvailable)
            .filter(m -> m.getCapabilities().contains(capability))
            .max(Comparator.comparingInt(AIModel::getPriority))
            .orElse(getDefaultModel());
    }
    
    /**
     * Get default model
     */
    public AIModel getDefaultModel() {
        if (defaultModel != null) {
            AIModel model = models.get(defaultModel);
            if (model != null && model.isAvailable()) {
                return model;
            }
        }
        
        // Fallback to any available model
        return models.values().stream()
            .filter(AIModel::isAvailable)
            .max(Comparator.comparingInt(AIModel::getPriority))
            .orElse(null);
    }
    
    /**
     * Get model with fallback chain
     */
    public AIModel getModelWithFallback(String preferredModel) {
        AIModel model = models.get(preferredModel);
        if (model != null && model.isAvailable()) {
            return model;
        }
        
        return getDefaultModel();
    }
    
    /**
     * Record model performance
     */
    public void recordPerformance(String modelName, boolean success, long responseTime, double confidence) {
        ModelStats stats = modelStats.get(modelName);
        if (stats != null) {
            stats.recordRequest(success, responseTime, confidence);
        }
    }
    
    /**
     * Get model statistics
     */
    public ModelStats getModelStats(String modelName) {
        return modelStats.get(modelName);
    }
    
    /**
     * Get all registered models
     */
    public Collection<AIModel> getAllModels() {
        return models.values();
    }
    
    /**
     * Get model recommendations based on performance
     */
    public List<String> getRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        modelStats.forEach((name, stats) -> {
            if (stats.getTotalRequests() > 10) {
                if (stats.getSuccessRate() < 0.7) {
                    recommendations.add("Model '" + name + "' has low success rate (" + 
                        String.format("%.0f%%", stats.getSuccessRate() * 100) + "). Consider replacement.");
                }
                if (stats.getAverageResponseTime() > 5000) {
                    recommendations.add("Model '" + name + "' has high latency (" + 
                        stats.getAverageResponseTime() + "ms). Consider optimization.");
                }
            }
        });
        
        return recommendations;
    }
    
    private void initializeDefaultModels() {
        // Register built-in models
        registerModel(new RuleBasedModel());
        registerModel(new PatternMatchingModel());
        registerModel(new HeuristicModel());
        
        System.out.println(CYAN + "🧠 Model Registry initialized with " + models.size() + " models" + RESET);
    }
}
