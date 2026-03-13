package com.blacklight.uac.demo;

import com.blacklight.uac.ai.models.*;
import com.blacklight.uac.mcp.DynamicMCPServer;

import java.util.*;

/**
 * ExternalLLMDemo - Demonstrates external LLM integration
 * Shows how to connect OpenAI, Anthropic, or other LLMs
 */
public class ExternalLLMDemo {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC EXTERNAL LLM INTEGRATION DEMO                             ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Connect OpenAI, Anthropic, or other LLMs to UAC                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        // Demo 1: Show how to register OpenAI
        demoOpenAIRegistration();
        
        // Demo 2: Show how to register Anthropic
        demoAnthropicRegistration();
        
        // Demo 3: Show model selection with external LLMs
        demoModelSelection();
        
        // Demo 4: Show fallback behavior
        demoFallbackBehavior();
        
        // Print summary
        printSummary();
    }
    
    private static void demoOpenAIRegistration() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 1: OPENAI REGISTRATION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println(YELLOW + "  Example: Register OpenAI GPT-4" + RESET);
        System.out.println();
        System.out.println("  ```java");
        System.out.println("  // Set your API key");
        System.out.println("  String apiKey = System.getenv(\"OPENAI_API_KEY\");");
        System.out.println();
        System.out.println("  // Create OpenAI model");
        System.out.println("  OpenAIModel openai = new OpenAIModel(apiKey);");
        System.out.println();
        System.out.println("  // Register with MCP server");
        System.out.println("  DynamicMCPServer server = new DynamicMCPServer();");
        System.out.println("  server.registerModel(openai);");
        System.out.println("  ```");
        System.out.println();
        
        System.out.println(GREEN + "  ✓ OpenAI model will be used for:" + RESET);
        System.out.println(YELLOW + "    • Complex code analysis" + RESET);
        System.out.println(YELLOW + "    • Advanced fix generation" + RESET);
        System.out.println(YELLOW + "    • Natural language explanations" + RESET);
        System.out.println();
    }
    
    private static void demoAnthropicRegistration() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 2: ANTHROPIC REGISTRATION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println(YELLOW + "  Example: Register Anthropic Claude" + RESET);
        System.out.println();
        System.out.println("  ```java");
        System.out.println("  // Set your API key");
        System.out.println("  String apiKey = System.getenv(\"ANTHROPIC_API_KEY\");");
        System.out.println();
        System.out.println("  // Create Anthropic model");
        System.out.println("  AnthropicModel claude = new AnthropicModel(apiKey);");
        System.out.println();
        System.out.println("  // Register with MCP server");
        System.out.println("  DynamicMCPServer server = new DynamicMCPServer();");
        System.out.println("  server.registerModel(claude);");
        System.out.println("  ```");
        System.out.println();
        
        System.out.println(GREEN + "  ✓ Claude model will be used for:" + RESET);
        System.out.println(YELLOW + "    • Detailed code review" + RESET);
        System.out.println(YELLOW + "    • Complex refactoring suggestions" + RESET);
        System.out.println(YELLOW + "    • Security vulnerability analysis" + RESET);
        System.out.println();
    }
    
    private static void demoModelSelection() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 3: DYNAMIC MODEL SELECTION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println(YELLOW + "  Model Priority Selection:" + RESET);
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("  │ Priority │ Model              │ Use Case                        │");
        System.out.println("  ├─────────────────────────────────────────────────────────────────┤");
        System.out.println("  │ 10       │ OpenAI GPT-4       │ Complex analysis, NL tasks      │");
        System.out.println("  │ 10       │ Anthropic Claude   │ Code review, refactoring        │");
        System.out.println("  │ 3        │ Heuristic          │ Weighted scoring (built-in)     │");
        System.out.println("  │ 2        │ PatternMatching    │ Pattern recognition (built-in)  │");
        System.out.println("  │ 1        │ RuleBased          │ Fast decisions (built-in)       │");
        System.out.println("  └─────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println(GREEN + "  ✓ External LLMs have highest priority (10)" + RESET);
        System.out.println(GREEN + "  ✓ Built-in models serve as fallback" + RESET);
        System.out.println(GREEN + "  ✓ Automatic selection based on capability" + RESET);
        System.out.println();
    }
    
    private static void demoFallbackBehavior() {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 4: FALLBACK BEHAVIOR" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        System.out.println(YELLOW + "  Fallback Chain:" + RESET);
        System.out.println();
        System.out.println("  Request → OpenAI (if available)");
        System.out.println("         → Anthropic (if available)");
        System.out.println("         → Heuristic (built-in)");
        System.out.println("         → PatternMatching (built-in)");
        System.out.println("         → RuleBased (built-in)");
        System.out.println();
        
        System.out.println(GREEN + "  ✓ System works even without external LLMs" + RESET);
        System.out.println(GREEN + "  ✓ Graceful degradation on API failures" + RESET);
        System.out.println(GREEN + "  ✓ Performance tracking per model" + RESET);
        System.out.println();
    }
    
    private static void printSummary() {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         INTEGRATION SUMMARY                           ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ External LLM Support                                               ║");
        System.out.println("║    • OpenAI GPT-4 integration ready                                   ║");
        System.out.println("║    • Anthropic Claude integration ready                               ║");
        System.out.println("║    • Extensible to any LLM provider                                   ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Configuration                                                      ║");
        System.out.println("║    • Set OPENAI_API_KEY environment variable                          ║");
        System.out.println("║    • Set ANTHROPIC_API_KEY environment variable                       ║");
        System.out.println("║    • Models auto-register on startup                                  ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Features                                                           ║");
        System.out.println("║    • Priority-based model selection                                   ║");
        System.out.println("║    • Automatic fallback to built-in models                            ║");
        System.out.println("║    • Performance tracking and learning                                ║");
        System.out.println("║    • Reinforcement learning from outcomes                             ║");
        System.out.println("║                                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        System.out.println(GREEN + "✓ UAC now supports external LLM integration!" + RESET);
        System.out.println();
    }
}
