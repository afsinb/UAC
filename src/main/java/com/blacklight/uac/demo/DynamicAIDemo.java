package com.blacklight.uac.demo;

import com.blacklight.uac.mcp.DynamicMCPServer;
import com.blacklight.uac.mcp.MCPServer;

import java.util.*;

/**
 * DynamicAIDemo - Demonstrates dynamic AI models with reinforcement learning
 */
public class DynamicAIDemo {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC DYNAMIC AI - MCP SERVERS WITH REINFORCEMENT LEARNING      ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Dynamic model selection • Self-evolution • Continuous learning       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        DynamicMCPServer server = new DynamicMCPServer();
        
        // Demo 1: Code Analysis with Dynamic Model Selection
        demoCodeAnalysis(server);
        
        // Demo 2: Fix Generation with Learning
        demoFixGeneration(server);
        
        // Demo 3: Learning from Outcomes
        demoLearning(server);
        
        // Demo 4: System Evolution
        demoEvolution(server);
        
        // Demo 5: Get Insights
        demoInsights(server);
        
        // Print summary
        printSummary();
    }
    
    private static void demoCodeAnalysis(DynamicMCPServer server) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 1: CODE ANALYSIS WITH DYNAMIC MODEL SELECTION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        String vulnerableCode = """
            public User getUser(String userId) {
                User user = userMap.get(userId);
                return user.getName();
            }
            """;
        
        System.out.println(YELLOW + "  Analyzing code..." + RESET);
        
        MCPServer.MCPRequest request = new MCPServer.MCPRequest(
            "REQ-1", "analyze_code", Map.of("code", vulnerableCode, "language", "java")
        );
        
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        if (response.isSuccess()) {
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            System.out.println(GREEN + "  ✓ Analysis complete" + RESET);
            System.out.println(YELLOW + "  → Model: " + result.get("model") + RESET);
            System.out.println(YELLOW + "  → Confidence: " + 
                String.format("%.0f%%", (Double) result.get("confidence") * 100) + RESET);
            System.out.println(YELLOW + "  → Response time: " + result.get("responseTime") + "ms" + RESET);
        }
        System.out.println();
    }
    
    private static void demoFixGeneration(DynamicMCPServer server) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 2: FIX GENERATION WITH AI" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        String code = "User user = userMap.get(userId); return user.getName();";
        
        System.out.println(YELLOW + "  Generating fix for NullPointerException..." + RESET);
        
        MCPServer.MCPRequest request = new MCPServer.MCPRequest(
            "REQ-2", "generate_fix", Map.of(
                "anomalyType", "NULL_POINTER_EXCEPTION",
                "code", code,
                "stackTrace", "at UserService.getUser(UserService.java:45)"
            )
        );
        
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        if (response.isSuccess()) {
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            System.out.println(GREEN + "  ✓ Fix generated" + RESET);
            System.out.println(YELLOW + "  → Source: " + result.get("source") + RESET);
            System.out.println(YELLOW + "  → Model: " + result.get("model") + RESET);
            System.out.println(YELLOW + "  → Confidence: " + 
                String.format("%.0f%%", (Double) result.get("confidence") * 100) + RESET);
            System.out.println();
            System.out.println(CYAN + "  Generated Fix:" + RESET);
            System.out.println("  ┌─────────────────────────────────────────────────────────────────┐");
            for (String line : result.get("fix").toString().split("\n")) {
                System.out.println("  │ " + line);
            }
            System.out.println("  └─────────────────────────────────────────────────────────────────┘");
        }
        System.out.println();
    }
    
    private static void demoLearning(DynamicMCPServer server) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 3: LEARNING FROM OUTCOMES" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        // Simulate successful fix
        System.out.println(YELLOW + "  Recording successful fix..." + RESET);
        MCPServer.MCPRequest successRequest = new MCPServer.MCPRequest(
            "REQ-3", "learn_from_outcome", Map.of(
                "anomalyType", "NULL_POINTER_EXCEPTION",
                "code", "User user = userMap.get(userId);",
                "fix", "if (userMap != null) { User user = userMap.get(userId); }",
                "success", true
            )
        );
        server.handleRequest(successRequest);
        
        // Simulate another successful fix
        System.out.println(YELLOW + "  Recording another successful fix..." + RESET);
        MCPServer.MCPRequest successRequest2 = new MCPServer.MCPRequest(
            "REQ-4", "learn_from_outcome", Map.of(
                "anomalyType", "NULL_POINTER_EXCEPTION",
                "code", "return user.getName();",
                "fix", "if (user != null) { return user.getName(); }",
                "success", true
            )
        );
        server.handleRequest(successRequest2);
        
        // Simulate failed fix
        System.out.println(YELLOW + "  Recording failed fix..." + RESET);
        MCPServer.MCPRequest failRequest = new MCPServer.MCPRequest(
            "REQ-5", "learn_from_outcome", Map.of(
                "anomalyType", "ARRAY_INDEX_OUT_OF_BOUNDS",
                "code", "return array[index];",
                "fix", "return array[index]; // No bounds check",
                "success", false,
                "reason", "Fix did not add bounds check"
            )
        );
        server.handleRequest(failRequest);
        
        System.out.println(GREEN + "  ✓ Learning complete - 3 experiences recorded" + RESET);
        System.out.println();
    }
    
    private static void demoEvolution(DynamicMCPServer server) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 4: SYSTEM EVOLUTION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        MCPServer.MCPRequest request = new MCPServer.MCPRequest(
            "REQ-6", "evolve_system", new HashMap<>()
        );
        
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        if (response.isSuccess()) {
            Map<String, Object> evolution = (Map<String, Object>) response.getResult();
            
            System.out.println(GREEN + "  ✓ Evolution Results:" + RESET);
            System.out.println(YELLOW + "  → Evolution Level: " + 
                String.format("%.2f", evolution.get("evolutionLevel")) + RESET);
            
            List<String> improvements = (List<String>) evolution.get("improvements");
            if (!improvements.isEmpty()) {
                System.out.println(YELLOW + "  → Improvements:" + RESET);
                for (String improvement : improvements) {
                    System.out.println(YELLOW + "    • " + improvement + RESET);
                }
            }
            
            List<String> weakAreas = (List<String>) evolution.get("weakAreas");
            if (!weakAreas.isEmpty()) {
                System.out.println(RED + "  → Areas for improvement:" + RESET);
                for (String area : weakAreas) {
                    System.out.println(RED + "    • " + area + RESET);
                }
            }
        }
        System.out.println();
    }
    
    private static void demoInsights(DynamicMCPServer server) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 5: AI INSIGHTS" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        MCPServer.MCPRequest request = new MCPServer.MCPRequest(
            "REQ-7", "get_insights", new HashMap<>()
        );
        
        MCPServer.MCPResponse response = server.handleRequest(request);
        
        if (response.isSuccess()) {
            Map<String, Object> insights = (Map<String, Object>) response.getResult();
            
            Map<String, Object> learning = (Map<String, Object>) insights.get("learning");
            
            System.out.println(GREEN + "  ✓ Learning Insights:" + RESET);
            System.out.println(YELLOW + "  → Total Episodes: " + learning.get("totalEpisodes") + RESET);
            System.out.println(YELLOW + "  → Successful: " + learning.get("successfulEpisodes") + RESET);
            System.out.println(YELLOW + "  → Success Rate: " + 
                String.format("%.0f%%", (Double) learning.get("successRate") * 100) + RESET);
            System.out.println(YELLOW + "  → Knowledge Base: " + 
                learning.get("knowledgeBaseSize") + " entries" + RESET);
            System.out.println(YELLOW + "  → Pattern Libraries: " + 
                learning.get("patternLibraries") + " libraries" + RESET);
            
            Map<String, Object> models = (Map<String, Object>) insights.get("models");
            System.out.println();
            System.out.println(GREEN + "  ✓ Model Insights:" + RESET);
            System.out.println(YELLOW + "  → Total Models: " + models.get("totalModels") + RESET);
        }
        System.out.println();
    }
    
    private static void printSummary() {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         DEMONSTRATION SUMMARY                         ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Dynamic AI Models                                                  ║");
        System.out.println("║    • RuleBased: Fast, rule-based decisions (priority 1)               ║");
        System.out.println("║    • PatternMatching: Pattern recognition (priority 2)                ║");
        System.out.println("║    • Heuristic: Weighted scoring (priority 3)                         ║");
        System.out.println("║    • Dynamic selection based on capability and performance            ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ MCP Protocol                                                       ║");
        System.out.println("║    • Standardized request/response interface                          ║");
        System.out.println("║    • Dynamic model registration                                       ║");
        System.out.println("║    • Performance tracking per model                                   ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Reinforcement Learning                                             ║");
        System.out.println("║    • Q-learning for action selection                                  ║");
        System.out.println("║    • Experience replay buffer                                         ║");
        System.out.println("║    • Pattern library accumulation                                     ║");
        System.out.println("║    • Knowledge base with confidence tracking                          ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Self-Evolution                                                     ║");
        System.out.println("║    • Learns from successful fixes                                     ║");
        System.out.println("║    • Learns from failures                                             ║");
        System.out.println("║    • Identifies optimal strategies                                    ║");
        System.out.println("║    • Detects weak areas for improvement                               ║");
        System.out.println("║    • Evolution level tracking                                         ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Capabilities                                                       ║");
        System.out.println("║    • analyze_code: AI-powered code analysis                           ║");
        System.out.println("║    • generate_fix: Fix generation with learning                       ║");
        System.out.println("║    • learn_from_outcome: Reinforcement learning                       ║");
        System.out.println("║    • evolve_system: Self-evolution                                    ║");
        System.out.println("║    • register_model: Dynamic model registration                       ║");
        System.out.println("║                                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        System.out.println(GREEN + "✓ UAC now has dynamic AI models with reinforcement learning!" + RESET);
        System.out.println(GREEN + "✓ The system evolves and improves with every fix!" + RESET);
        System.out.println();
    }
}
