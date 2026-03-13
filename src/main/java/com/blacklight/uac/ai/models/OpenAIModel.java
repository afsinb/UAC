package com.blacklight.uac.ai.models;

import java.net.http.*;
import java.util.*;

/**
 * OpenAIModel - OpenAI GPT integration for UAC
 * Uses OpenAI API for intelligent code analysis and fix generation
 */
public class OpenAIModel extends ExternalLLMModel {
    
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4";
    
    public OpenAIModel(String apiKey) {
        this(apiKey, DEFAULT_MODEL, DEFAULT_BASE_URL, 30);
    }
    
    public OpenAIModel(String apiKey, String model, String baseUrl, int timeoutSeconds) {
        super(apiKey, "OpenAI-" + model, baseUrl, timeoutSeconds);
    }
    
    @Override
    protected String callLLMAPI(String prompt, Map<String, Object> context) throws Exception {
        String requestBody = buildRequestBody(prompt, context);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create(baseUrl + "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
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
                "max_tokens": 2000
            }
            """.formatted(
                modelName.replace("OpenAI-", ""),
                escapeJson(systemPrompt),
                escapeJson(prompt)
            );
    }
    
    @Override
    protected String parseResponse(String responseBody) {
        // Simple JSON parsing for the response
        // In production, use a proper JSON library
        try {
            int contentStart = responseBody.indexOf("\"content\":\"") + 11;
            int contentEnd = responseBody.indexOf("\"", contentStart);
            
            if (contentStart > 10 && contentEnd > contentStart) {
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
