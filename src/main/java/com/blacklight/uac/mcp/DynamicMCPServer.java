package com.blacklight.uac.mcp;

import com.blacklight.uac.ai.models.*;
import com.blacklight.uac.ai.learning.ReinforcementLearner;

import java.util.*;

/**
 * DynamicMCPServer - MCP server with dynamic AI model selection
 * Uses reinforcement learning to improve over time
 */
public class DynamicMCPServer implements MCPServer {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final ModelRegistry modelRegistry;
    private final ReinforcementLearner learner;
    private final Map<String, Object> serverState;
    
    public DynamicMCPServer() {
        this.modelRegistry = new ModelRegistry();
        this.learner = new ReinforcementLearner();
        this.serverState = new HashMap<>();
        
        System.out.println(CYAN + "🔌 Dynamic MCP Server initialized" + RESET);
    }
    
    @Override
    public String getName() {
        return "DynamicAI";
    }
    
    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "analyze_code",
            "generate_fix",
            "detect_anomalies",
            "recommend_deployment",
            "learn_from_outcome",
            "evolve_system",
            "get_insights",
            "register_model"
        );
    }
    
    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "analyze_code" -> analyzeCode(request);
            case "generate_fix" -> generateFix(request);
            case "detect_anomalies" -> detectAnomalies(request);
            case "recommend_deployment" -> recommendDeployment(request);
            case "learn_from_outcome" -> learnFromOutcome(request);
            case "evolve_system" -> evolveSystem(request);
            case "get_insights" -> getInsights(request);
            case "register_model" -> registerModel(request);
            default -> MCPResponse.error(request.getId(), "Unknown method: " + request.getMethod());
        };
    }
    
    @Override
    public boolean isHealthy() {
        return modelRegistry.getDefaultModel() != null;
    }
    
    /**
     * Register an AI model directly
     */
    public void registerModel(AIModel model) {
        modelRegistry.registerModel(model);
    }
    
    /**
     * Get the model registry
     */
    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }
    
    /**
     * Analyze code using best available model
     */
    private MCPResponse analyzeCode(MCPRequest request) {
        String code = request.getStringParam("code");
        String language = request.getStringParam("language");
        String preferredModel = request.getStringParam("model");
        
        if (code == null) {
            return MCPResponse.error(request.getId(), "Missing 'code' parameter");
        }
        
        // Get best model for code analysis
        AIModel model = preferredModel != null ?
            modelRegistry.getModelWithFallback(preferredModel) :
            modelRegistry.getBestModel("code_analysis");
        
        if (model == null) {
            return MCPResponse.error(request.getId(), "No available model for code analysis");
        }
        
        // Perform analysis
        AIModel.AIResponse response = model.analyzeCode(code, language != null ? language : "java");
        
        // Record performance
        modelRegistry.recordPerformance(
            model.getModelName(),
            response.isSuccess(),
            response.getResponseTime(),
            response.getConfidence()
        );
        
        if (response.isSuccess()) {
            Map<String, Object> result = new HashMap<>();
            result.put("analysis", response.getMetadata().get("analysis"));
            result.put("model", model.getModelName());
            result.put("confidence", response.getConfidence());
            result.put("responseTime", response.getResponseTime());
            
            MCPResponse mcpResponse = MCPResponse.success(request.getId(), result);
            mcpResponse.addMetadata("modelUsed", model.getModelName());
            
            return mcpResponse;
        }
        
        return MCPResponse.error(request.getId(), "Analysis failed: " + response.getContent());
    }
    
    /**
     * Generate fix using AI with reinforcement learning
     */
    private MCPResponse generateFix(MCPRequest request) {
        String anomalyType = request.getStringParam("anomalyType");
        String code = request.getStringParam("code");
        String stackTrace = request.getStringParam("stackTrace");
        
        if (anomalyType == null || code == null) {
            return MCPResponse.error(request.getId(), "Missing required parameters");
        }
        
        // Check learned recommendations first
        Optional<String> learnedFix = learner.getRecommendation(anomalyType, code);
        if (learnedFix.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("fix", learnedFix.get());
            result.put("source", "learned");
            result.put("confidence", 0.95);
            
            System.out.println(GREEN + "  ✓ Using learned fix for " + anomalyType + RESET);
            
            return MCPResponse.success(request.getId(), result);
        }
        
        // Get best model for fix generation
        AIModel model = modelRegistry.getBestModel("fix_generation");
        if (model == null) {
            return MCPResponse.error(request.getId(), "No available model for fix generation");
        }
        
        // Generate fix
        AIModel.AIResponse response = model.generateFix(anomalyType, code, stackTrace);
        
        // Record performance
        modelRegistry.recordPerformance(
            model.getModelName(),
            response.isSuccess(),
            response.getResponseTime(),
            response.getConfidence()
        );
        
        if (response.isSuccess()) {
            Map<String, Object> result = new HashMap<>();
            result.put("fix", response.getContent());
            result.put("model", model.getModelName());
            result.put("confidence", response.getConfidence());
            result.put("responseTime", response.getResponseTime());
            result.put("source", "ai_generated");
            
            MCPResponse mcpResponse = MCPResponse.success(request.getId(), result);
            mcpResponse.addMetadata("modelUsed", model.getModelName());
            
            return mcpResponse;
        }
        
        return MCPResponse.error(request.getId(), "Fix generation failed");
    }
    
    /**
     * Detect anomalies in code or logs
     */
    private MCPResponse detectAnomalies(MCPRequest request) {
        String input = request.getStringParam("input");
        String inputType = request.getStringParam("inputType"); // "code" or "log"
        
        if (input == null) {
            return MCPResponse.error(request.getId(), "Missing 'input' parameter");
        }
        
        AIModel model = modelRegistry.getBestModel("anomaly_classification");
        if (model == null) {
            return MCPResponse.error(request.getId(), "No available model for anomaly detection");
        }
        
        Map<String, Object> context = new HashMap<>();
        context.put("inputType", inputType != null ? inputType : "code");
        
        AIModel.AIResponse response = model.generate("Detect anomalies in: " + input, context);
        
        Map<String, Object> result = new HashMap<>();
        result.put("detection", response.getContent());
        result.put("model", model.getModelName());
        result.put("confidence", response.getConfidence());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Recommend deployment strategy
     */
    private MCPResponse recommendDeployment(MCPRequest request) {
        String changeType = request.getStringParam("changeType");
        String environment = request.getStringParam("environment");
        Double riskLevel = request.getDoubleParam("riskLevel");
        
        AIModel model = modelRegistry.getBestModel("decision_making");
        if (model == null) {
            return MCPResponse.error(request.getId(), "No available model for deployment decisions");
        }
        
        String prompt = String.format(
            "Recommend deployment strategy for %s change to %s environment with risk level %.2f",
            changeType, environment, riskLevel != null ? riskLevel : 0.5
        );
        
        AIModel.AIResponse response = model.generate(prompt, new HashMap<>());
        
        Map<String, Object> result = new HashMap<>();
        result.put("recommendation", response.getContent());
        result.put("model", model.getModelName());
        result.put("confidence", response.getConfidence());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Learn from fix outcome
     */
    private MCPResponse learnFromOutcome(MCPRequest request) {
        String anomalyType = request.getStringParam("anomalyType");
        String code = request.getStringParam("code");
        String fix = request.getStringParam("fix");
        Boolean success = (Boolean) request.getParam("success");
        String reason = request.getStringParam("reason");
        
        if (anomalyType == null || success == null) {
            return MCPResponse.error(request.getId(), "Missing required parameters");
        }
        
        // Create experience for reinforcement learning
        String state = anomalyType + ":" + code.hashCode();
        String action = fix != null ? fix : "unknown";
        double reward = success ? 1.0 : -0.5;
        String nextState = success ? "resolved" : "unresolved";
        
        ReinforcementLearner.Experience experience = new ReinforcementLearner.Experience(
            state, action, reward, nextState
        );
        
        learner.learn(experience);
        
        // Learn from success/failure
        if (success && code != null && fix != null) {
            learner.learnFromSuccess(anomalyType, code, fix, new HashMap<>());
        } else if (!success) {
            learner.learnFromFailure(anomalyType, code != null ? code : "", 
                fix != null ? fix : "", reason != null ? reason : "Unknown");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("learned", true);
        result.put("anomalyType", anomalyType);
        result.put("success", success);
        
        System.out.println(CYAN + "🧠 Learning recorded for " + anomalyType + 
                          " (success: " + success + ")" + RESET);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Evolve the system based on accumulated knowledge
     */
    private MCPResponse evolveSystem(MCPRequest request) {
        System.out.println();
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "🧬 SYSTEM EVOLUTION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        // Get evolution insights
        Map<String, Object> evolution = learner.evolve();
        
        // Get model recommendations
        List<String> modelRecommendations = modelRegistry.getRecommendations();
        evolution.put("modelRecommendations", modelRecommendations);
        
        // Get learner insights
        Map<String, Object> learnerInsights = learner.getInsights();
        evolution.put("learnerInsights", learnerInsights);
        
        // Update server state
        serverState.put("lastEvolution", System.currentTimeMillis());
        serverState.put("evolutionLevel", evolution.get("evolutionLevel"));
        
        System.out.println(GREEN + "  ✓ System evolved to level: " + 
            String.format("%.2f", evolution.get("evolutionLevel")) + RESET);
        System.out.println(GREEN + "  ✓ Knowledge base: " + 
            learnerInsights.get("knowledgeBaseSize") + " entries" + RESET);
        System.out.println(GREEN + "  ✓ Pattern libraries: " + 
            learnerInsights.get("patternLibraries") + " libraries" + RESET);
        System.out.println();
        
        return MCPResponse.success(request.getId(), evolution);
    }
    
    /**
     * Get AI and learning insights
     */
    private MCPResponse getInsights(MCPRequest request) {
        Map<String, Object> insights = new HashMap<>();
        
        // Model registry insights
        Map<String, Object> modelInsights = new HashMap<>();
        modelInsights.put("totalModels", modelRegistry.getAllModels().size());
        modelInsights.put("recommendations", modelRegistry.getRecommendations());
        insights.put("models", modelInsights);
        
        // Learner insights
        insights.put("learning", learner.getInsights());
        
        // Server state
        insights.put("serverState", serverState);
        
        return MCPResponse.success(request.getId(), insights);
    }
    
    /**
     * Register a new AI model dynamically
     */
    private MCPResponse registerModel(MCPRequest request) {
        String modelName = request.getStringParam("modelName");
        String modelType = request.getStringParam("modelType");
        Map<String, Object> config = (Map<String, Object>) request.getParam("config");
        
        if (modelName == null || modelType == null) {
            return MCPResponse.error(request.getId(), "Missing required parameters");
        }
        
        // In a real implementation, this would create and register the model
        // For now, we'll just acknowledge the request
        
        Map<String, Object> result = new HashMap<>();
        result.put("registered", true);
        result.put("modelName", modelName);
        result.put("modelType", modelType);
        
        System.out.println(GREEN + "  ✓ Model registration requested: " + modelName + 
                          " (" + modelType + ")" + RESET);
        
        return MCPResponse.success(request.getId(), result);
    }
}
