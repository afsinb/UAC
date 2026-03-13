package com.blacklight.uac.ai.models;

import java.net.http.*;
import java.util.*;

/**
 * AnthropicModel - Anthropic Claude integration for UAC
 * Uses Anthropic API for intelligent code analysis and fix generation
 */
public class AnthropicModel extends ExternalLLMModel {
    
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String DEFAULT_MODEL = "claude-3-opus-20240229";
    private static final String API_VERSION = "2023-06-01";
    
    public AnthropicModel(String apiKey) {
        this(apiKey, DEFAULT_MODEL, DEFAULT_BASE_URL, 30);
    }
    
    public AnthropicModel(String apiKey, String model, String baseUrl, int timeoutSeconds) {
        super(apiKey, "Anthropic-" + model, baseUrl, timeoutSeconds);
    }
    
    @Override
    protected String callLLMAPI(String prompt, Map<String, Object> context) throws Exception {
        String requestBody = buildRequestBody(prompt, context);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create(baseUrl + "/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Anthropic API error: " + response.statusCode() + " - " + response.body());
        }
        
        return parseResponse(response.body());
    }
    
    @Override
    protected String buildRequestBody(String prompt, Map<String, Object> context) {
        String systemPrompt = "You are an expert software engineer specializing in code analysis, " +
            "bug detection, and automated fixing. Provide clear, actionable recommendations.";
        
        return """
            {
                "model": "%s",
                "max_tokens": 2000,
                "system": "%s",
                "messages": [
                    {
                        "role": "user",
                        "content": "%s"
                    }
                ]
            }
            """.formatted(
                modelName.replace("Anthropic-", ""),
                escapeJson(systemPrompt),
                escapeJson(prompt)
            );
    }
    
    @Override
    protected String parseResponse(String responseBody) {
        try {
            int contentStart = responseBody.indexOf("\"text\":\"") + 8;
            int contentEnd = responseBody.indexOf("\"", contentStart);
            
            if (contentStart > 7 && contentEnd > contentStart) {
                String content = responseBody.substring(contentStart, contentEnd);
                return unescapeJson(content);
            }
            
            return responseBody;
            
        } catch (Exception e) {
            return responseBody;
        }
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
