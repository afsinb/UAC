package com.blacklight.uac.ai.models;

import java.net.http.*;
import java.util.*;

/**
 * OpenRouterModel - OpenRouter.ai integration for UAC
 * Provides access to multiple LLM models through a single API
 * Supports free models and paid models from various providers
 */
public class OpenRouterModel extends ExternalLLMModel {
    
    private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
    private static final String DEFAULT_MODEL = "meta-llama/llama-3.1-8b-instruct:free";
    
    // Popular free models available on OpenRouter
    public static final String LLAMA_3_8B_FREE = "meta-llama/llama-3.1-8b-instruct:free";
    public static final String MISTRAL_7B_FREE = "mistralai/mistral-7b-instruct:free";
    public static final String GEMMA_7B_FREE = "google/gemma-7b-it:free";
    public static final String PHI_3_FREE = "microsoft/phi-3-mini-128k-instruct:free";
    public static final String QWEN_7B_FREE = "qwen/qwen-2-7b-instruct:free";
    public static final String HUNTER_ALPHA = "hunter-alpha/hunter-alpha";
    
    // Popular paid models
    public static final String GPT_4O = "openai/gpt-4o";
    public static final String CLAUDE_3_5_SONNET = "anthropic/claude-3.5-sonnet";
    public static final String GEMINI_PRO = "google/gemini-pro-1.5";
    public static final String LLAMA_3_70B = "meta-llama/llama-3.1-70b-instruct";
    
    private final String siteUrl;
    private final String appName;
    
    public OpenRouterModel(String apiKey) {
        this(apiKey, DEFAULT_MODEL, DEFAULT_BASE_URL, 30, null, null);
    }
    
    public OpenRouterModel(String apiKey, String model) {
        this(apiKey, model, DEFAULT_BASE_URL, 30, null, null);
    }
    
    public OpenRouterModel(String apiKey, String model, String baseUrl, int timeoutSeconds,
                          String siteUrl, String appName) {
        super(apiKey, "OpenRouter-" + model, baseUrl, timeoutSeconds);
        this.siteUrl = siteUrl;
        this.appName = appName;
    }
    
    @Override
    protected String callLLMAPI(String prompt, Map<String, Object> context) throws Exception {
        String requestBody = buildRequestBody(prompt, context);
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(java.net.URI.create(baseUrl + "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .header("HTTP-Referer", siteUrl != null ? siteUrl : "https://github.com/uac")
            .header("X-Title", appName != null ? appName : "UAC-Autonomous-Core")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        
        HttpResponse<String> response = httpClient.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenRouter API error: " + response.statusCode() + " - " + response.body());
        }
        
        return parseResponse(response.body());
    }
    
    @Override
    protected String buildRequestBody(String prompt, Map<String, Object> context) {
        String systemPrompt = "You are an expert software engineer specializing in code analysis, " +
            "bug detection, and automated fixing. Provide clear, actionable recommendations. " +
            "Focus on practical solutions that can be implemented immediately.";
        
        // Extract model name without "OpenRouter-" prefix
        String actualModel = modelName.replace("OpenRouter-", "");
        
        return """
            {
                "model": "%s",
                "messages": [
                    {
                        "role": "system",
                        "content": "%s"
                    },
                    {
                        "role": "user",
                        "content": "%s"
                    }
                ],
                "temperature": 0.1,
                "max_tokens": 2000,
                "top_p": 0.9,
                "frequency_penalty": 0,
                "presence_penalty": 0
            }
            """.formatted(
                actualModel,
                escapeJson(systemPrompt),
                escapeJson(prompt)
            );
    }
    
    @Override
    protected String parseResponse(String responseBody) {
        try {
            // OpenRouter returns OpenAI-compatible format
            int contentStart = responseBody.indexOf("\"content\":\"") + 11;
            int contentEnd = responseBody.indexOf("\"", contentStart);
            
            if (contentStart > 10 && contentEnd > contentStart) {
                String content = responseBody.substring(contentStart, contentEnd);
                return unescapeJson(content);
            }
            
            // Try alternative format
            contentStart = responseBody.indexOf("\"text\":\"") + 8;
            contentEnd = responseBody.indexOf("\"", contentStart);
            
            if (contentStart > 7 && contentEnd > contentStart) {
                String content = responseBody.substring(contentStart, contentEnd);
                return unescapeJson(content);
            }
            
            return responseBody;
            
        } catch (Exception e) {
            return responseBody;
        }
    }
    
    /**
     * Get list of available free models
     */
    public static List<String> getFreeModels() {
        return Arrays.asList(
            LLAMA_3_8B_FREE,
            MISTRAL_7B_FREE,
            GEMMA_7B_FREE,
            PHI_3_FREE,
            QWEN_7B_FREE
        );
    }
    
    /**
     * Get list of popular paid models
     */
    public static List<String> getPaidModels() {
        return Arrays.asList(
            GPT_4O,
            CLAUDE_3_5_SONNET,
            GEMINI_PRO,
            LLAMA_3_70B
        );
    }
    
    /**
     * Create OpenRouter model with a specific free model
     */
    public static OpenRouterModel createFreeModel(String apiKey, String modelId) {
        return new OpenRouterModel(apiKey, modelId);
    }
    
    /**
     * Create OpenRouter model with best free model for code tasks
     */
    public static OpenRouterModel createBestFreeModel(String apiKey) {
        return new OpenRouterModel(apiKey, LLAMA_3_8B_FREE);
    }
    
    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    private String unescapeJson(String text) {
        return text
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }
}
