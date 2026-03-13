package com.blacklight.uac.demo;

import com.blacklight.uac.ai.models.*;
import com.blacklight.uac.mcp.DynamicMCPServer;
import com.blacklight.uac.mcp.MCPServer;

import java.util.*;

/**
 * OpenRouterDemo - Demonstrates OpenRouter.ai integration with UAC
 * Shows how to use free and paid models through OpenRouter
 */
public class OpenRouterDemo {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC OPENROUTER.AI INTEGRATION DEMO                            ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Access 100+ LLM models through a single API                          ║");
        System.out.println("║  Including FREE models!                                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        // Demo 1: Available models
        demoAvailableModels();
        
        // Demo 2: Registration
        demoRegistration();
        
        // Demo 3: Usage examples
        demoUsage();
        
        // Demo 4: Model comparison
        demoModelComparison();
        
        // Print summary
        printSummary();
    }
    
    private static void demoAvailableModels() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 1: AVAILABLE MODELS" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println(GREEN + "  ✓ FREE Models (no cost):" + RESET);
        System.out.println(YELLOW + "    • meta-llama/llama-3.1-8b-instruct:free" + RESET);
        System.out.println(YELLOW + "    • mistralai/mistral-7b-instruct:free" + RESET);
        System.out.println(YELLOW + "    • google/gemma-7b-it:free" + RESET);
        System.out.println(YELLOW + "    • microsoft/phi-3-mini-128k-instruct:free" + RESET);
        System.out.println(YELLOW + "    • qwen/qwen-2-7b-instruct:free" + RESET);
        System.out.println();
        
        System.out.println(GREEN + "  ✓ Specialized Models:" + RESET);
        System.out.println(YELLOW + "    • hunter-alpha/hunter-alpha (code-focused)" + RESET);
        System.out.println();
        
        System.out.println(GREEN + "  ✓ Popular Paid Models:" + RESET);
        System.out.println(YELLOW + "    • openai/gpt-4o" + RESET);
        System.out.println(YELLOW + "    • anthropic/claude-3.5-sonnet" + RESET);
        System.out.println(YELLOW + "    • google/gemini-pro-1.5" + RESET);
        System.out.println(YELLOW + "    • meta-llama/llama-3.1-70b-instruct" + RESET);
        System.out.println();
        
        System.out.println(CYAN + "  💡 Tip: Start with free models, upgrade to paid for better results" + RESET);
        System.out.println();
    }
    
    private static void demoRegistration() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 2: REGISTRATION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println(YELLOW + "  Get your API key from: https://openrouter.ai/keys" + RESET);
        System.out.println();
        
        System.out.println("  ```java");
        System.out.println("  // Option 1: Use default free model (Llama 3.1 8B)");
        System.out.println("  String apiKey = System.getenv(\"OPENROUTER_API_KEY\");");
        System.out.println("  OpenRouterModel openrouter = new OpenRouterModel(apiKey);");
        System.out.println();
        System.out.println("  // Option 2: Use best free model for code");
        System.out.println("  OpenRouterModel openrouter = OpenRouterModel.createBestFreeModel(apiKey);");
        System.out.println();
        System.out.println("  // Option 3: Use specific free model");
        System.out.println("  OpenRouterModel openrouter = OpenRouterModel.createFreeModel(");
        System.out.println("      apiKey,");
        System.out.println("      OpenRouterModel.MISTRAL_7B_FREE");
        System.out.println("  );");
        System.out.println();
        System.out.println("  // Option 4: Use paid model (GPT-4o)");
        System.out.println("  OpenRouterModel openrouter = new OpenRouterModel(");
        System.out.println("      apiKey,");
        System.out.println("      OpenRouterModel.GPT_4O");
        System.out.println("  );");
        System.out.println();
        System.out.println("  // Register with UAC");
        System.out.println("  DynamicMCPServer server = new DynamicMCPServer();");
        System.out.println("  server.registerModel(openrouter);");
        System.out.println("  ```");
        System.out.println();
        
        System.out.println(GREEN + "  ✓ Single API key for 100+ models" + RESET);
        System.out.println(GREEN + "  ✓ Automatic fallback handling" + RESET);
        System.out.println(GREEN + "  ✓ Priority-based selection" + RESET);
        System.out.println();
    }
    
    private static void demoUsage() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 3: USAGE EXAMPLES" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println(YELLOW + "  Example 1: Code Analysis" + RESET);
        System.out.println("  ```java");
        System.out.println("  AIModel.AIResponse response = openrouter.analyzeCode(code, \"java\");");
        System.out.println("  System.out.println(response.getContent());");
        System.out.println("  ```");
        System.out.println();
        
        System.out.println(YELLOW + "  Example 2: Generate Fix" + RESET);
        System.out.println("  ```java");
        System.out.println("  AIModel.AIResponse fix = openrouter.generateFix(");
        System.out.println("      \"NULL_POINTER_EXCEPTION\",");
        System.out.println("      code,");
        System.out.println("      stackTrace");
        System.out.println("  );");
        System.out.println("  System.out.println(fix.getContent());");
        System.out.println("  ```");
        System.out.println();
        
        System.out.println(YELLOW + "  Example 3: Via MCP Server" + RESET);
        System.out.println("  ```java");
        System.out.println("  MCPServer.MCPRequest request = new MCPServer.MCPRequest(");
        System.out.println("      \"req-1\",");
        System.out.println("      \"generate_fix\",");
        System.out.println("      Map.of(");
        System.out.println("          \"anomalyType\", \"NULL_POINTER_EXCEPTION\",");
        System.out.println("          \"code\", vulnerableCode");
        System.out.println("      )");
        System.out.println("  );");
        System.out.println("  MCPServer.MCPResponse response = server.handleRequest(request);");
        System.out.println("  ```");
        System.out.println();
    }
    
    private static void demoModelComparison() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 4: MODEL COMPARISON" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println("  ┌─────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("  │ Model                          │ Cost    │ Speed   │ Quality │ Best For     │");
        System.out.println("  ├─────────────────────────────────────────────────────────────────────────────┤");
        System.out.println("  │ Llama 3.1 8B (free)            │ FREE    │ Fast    │ Good    │ General code │");
        System.out.println("  │ Mistral 7B (free)              │ FREE    │ Fast    │ Good    │ Quick fixes  │");
        System.out.println("  │ Phi-3 Mini (free)              │ FREE    │ Fast    │ Good    │ Small tasks  │");
        System.out.println("  │ GPT-4o                         │ $$$$    │ Medium  │ Best    │ Complex code │");
        System.out.println("  │ Claude 3.5 Sonnet              │ $$$$    │ Medium  │ Best    │ Code review  │");
        System.out.println("  │ Llama 3.1 70B                  │ $$      │ Slow    │ Great   │ Deep analysis│");
        System.out.println("  └─────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println(CYAN + "  💡 Recommendation: Start with free models, upgrade for production" + RESET);
        System.out.println();
    }
    
    private static void printSummary() {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         INTEGRATION SUMMARY                           ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ OpenRouter.ai Integration                                          ║");
        System.out.println("║    • Access 100+ LLM models through single API                        ║");
        System.out.println("║    • FREE models available (Llama, Mistral, Phi, Gemma)               ║");
        System.out.println("║    • Paid models (GPT-4o, Claude, Gemini)                             ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Configuration                                                      ║");
        System.out.println("║    • Get API key: https://openrouter.ai/keys                          ║");
        System.out.println("║    • Set OPENROUTER_API_KEY environment variable                      ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Features                                                           ║");
        System.out.println("║    • Automatic model selection                                        ║");
        System.out.println("║    • Fallback to built-in models                                      ║");
        System.out.println("║    • Performance tracking                                             ║");
        System.out.println("║    • Reinforcement learning                                           ║");
        System.out.println("║                                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        System.out.println(GREEN + "✓ UAC now supports OpenRouter.ai with FREE models!" + RESET);
        System.out.println();
    }
}
