package com.blacklight.uac.demo;

import com.blacklight.uac.ai.AIDecisionEngine;
import com.blacklight.uac.mcp.MCPOrchestrator;

import java.util.*;

/**
 * AIDecisionDemo - Demonstrates AI-powered decision making with MCP servers
 */
public class AIDecisionDemo {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC AI DECISION ENGINE & MCP SERVERS DEMO                     ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  AI-powered fix recommendations with MCP protocol integration         ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        MCPOrchestrator orchestrator = new MCPOrchestrator();
        
        // Demo 1: AI Decision for NullPointerException
        demoAIDecision(orchestrator);
        
        // Demo 2: Code Analysis via MCP
        demoCodeAnalysis(orchestrator);
        
        // Demo 3: Deployment Recommendation via MCP
        demoDeploymentRecommendation(orchestrator);
        
        // Demo 4: AI Learning
        demoAILearning(orchestrator);
        
        // Print summary
        printSummary(orchestrator);
    }
    
    private static void demoAIDecision(MCPOrchestrator orchestrator) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 1: AI DECISION FOR NULLPOINTEREXCEPTION" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        Map<String, Object> context = new HashMap<>();
        context.put("severity", "HIGH");
        context.put("frequency", "recurring");
        context.put("complexity", "low");
        
        AIDecisionEngine.AIDecision decision = orchestrator.getAIDecision("NULL_POINTER_EXCEPTION", context);
        
        if (decision.getRecommendedRecipe() != null) {
            System.out.println(GREEN + "  ✓ Recipe: " + decision.getRecommendedRecipe().getName() + RESET);
            System.out.println(YELLOW + "  → Confidence: " + String.format("%.0f%%", decision.getConfidence() * 100) + RESET);
            System.out.println(YELLOW + "  → Steps:" + RESET);
            for (String step : decision.getRecommendedRecipe().getSteps()) {
                System.out.println(YELLOW + "    • " + step + RESET);
            }
            System.out.println();
            System.out.println(CYAN + "  Reasoning:" + RESET);
            System.out.println("  " + decision.getReasoning().replace("\n", "\n  "));
        }
        System.out.println();
    }
    
    private static void demoCodeAnalysis(MCPOrchestrator orchestrator) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 2: CODE ANALYSIS VIA MCP" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        String vulnerableCode = """
            public User getUser(String userId) {
                User user = userMap.get(userId);
                return user.getName();
            }
            """;
        
        System.out.println(YELLOW + "  Analyzing code:" + RESET);
        System.out.println("  ┌─────────────────────────────────────────────────────────────────┐");
        for (String line : vulnerableCode.split("\n")) {
            System.out.println("  │ " + line);
        }
        System.out.println("  └─────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        // Analyze code
        Map<String, Object> analysis = orchestrator.analyzeCode(vulnerableCode);
        System.out.println(GREEN + "  ✓ Analysis Results:" + RESET);
        System.out.println(YELLOW + "    Lines: " + analysis.get("lines") + RESET);
        System.out.println(YELLOW + "    Complexity: " + analysis.get("complexity") + RESET);
        System.out.println(YELLOW + "    Has Null Checks: " + analysis.get("hasNullChecks") + RESET);
        System.out.println();
        
        // Find vulnerabilities
        List<Map<String, Object>> vulnerabilities = orchestrator.findVulnerabilities(vulnerableCode);
        if (!vulnerabilities.isEmpty()) {
            System.out.println(RED + "  🚨 Vulnerabilities Found:" + RESET);
            for (Map<String, Object> vuln : vulnerabilities) {
                System.out.println(RED + "    • " + vuln.get("type") + " (" + vuln.get("severity") + ")" + RESET);
                System.out.println(YELLOW + "      " + vuln.get("description") + RESET);
                System.out.println(YELLOW + "      Recommendation: " + vuln.get("recommendation") + RESET);
            }
        }
        System.out.println();
    }
    
    private static void demoDeploymentRecommendation(MCPOrchestrator orchestrator) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 3: DEPLOYMENT RECOMMENDATION VIA MCP" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        // Get deployment recommendation
        Map<String, Object> recommendation = orchestrator.getDeploymentRecommendation(
            "hotfix", "production", 0.6
        );
        
        System.out.println(GREEN + "  ✓ Deployment Strategy: " + recommendation.get("strategy") + RESET);
        System.out.println(YELLOW + "  → Description: " + recommendation.get("description") + RESET);
        System.out.println(YELLOW + "  → Rollback Time: " + recommendation.get("rollbackTime") + RESET);
        System.out.println(YELLOW + "  → Monitoring Period: " + recommendation.get("monitoringPeriod") + RESET);
        System.out.println();
        
        // Assess risk
        Map<String, Object> risk = orchestrator.assessDeploymentRisk("hotfix", 150, true, false);
        
        System.out.println(GREEN + "  ✓ Risk Assessment:" + RESET);
        System.out.println(YELLOW + "  → Risk Score: " + String.format("%.2f", risk.get("riskScore")) + RESET);
        System.out.println(YELLOW + "  → Risk Level: " + risk.get("riskLevel") + RESET);
        System.out.println(YELLOW + "  → Recommendation: " + risk.get("recommendation") + RESET);
        
        List<String> riskFactors = (List<String>) risk.get("riskFactors");
        if (!riskFactors.isEmpty()) {
            System.out.println(YELLOW + "  → Risk Factors:" + RESET);
            for (String factor : riskFactors) {
                System.out.println(YELLOW + "    • " + factor + RESET);
            }
        }
        System.out.println();
    }
    
    private static void demoAILearning(MCPOrchestrator orchestrator) {
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println(CYAN + "DEMO 4: AI LEARNING FROM OUTCOMES" + RESET);
        System.out.println(CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + RESET);
        System.out.println();
        
        // Simulate learning from successful fix
        System.out.println(YELLOW + "  Simulating fix outcome..." + RESET);
        orchestrator.learn("NULL_POINTER_EXCEPTION", "NPE-001", true, 
            Map.of("fixTime", 5000, "testsPass", true));
        
        System.out.println(YELLOW + "  Simulating another successful fix..." + RESET);
        orchestrator.learn("NULL_POINTER_EXCEPTION", "NPE-001", true,
            Map.of("fixTime", 4500, "testsPass", true));
        
        System.out.println(YELLOW + "  Simulating a failed fix..." + RESET);
        orchestrator.learn("ARRAY_INDEX_OUT_OF_BOUNDS", "AIOB-001", false,
            Map.of("fixTime", 8000, "testsPass", false));
        
        System.out.println();
        
        // Get insights
        Map<String, Object> insights = orchestrator.getAIInsights();
        
        System.out.println(GREEN + "  ✓ AI Insights:" + RESET);
        System.out.println(YELLOW + "  → Total Decisions: " + insights.get("totalDecisions") + RESET);
        System.out.println(YELLOW + "  → Successful: " + insights.get("successfulDecisions") + RESET);
        System.out.println(YELLOW + "  → Success Rate: " + 
            String.format("%.0f%%", (Double) insights.get("overallSuccessRate") * 100) + RESET);
        System.out.println(YELLOW + "  → Recipes Available: " + insights.get("recipesAvailable") + RESET);
        System.out.println();
    }
    
    private static void printSummary(MCPOrchestrator orchestrator) {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         DEMONSTRATION SUMMARY                         ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ AI Decision Engine                                                 ║");
        System.out.println("║    • Analyzes anomalies and recommends optimal fix recipes            ║");
        System.out.println("║    • Calculates confidence based on historical success                ║");
        System.out.println("║    • Learns from outcomes to improve future decisions                 ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ MCP Servers                                                        ║");
        System.out.println("║    • CodeAnalysis: Vulnerability detection and fix suggestions        ║");
        System.out.println("║    • Deployment: Strategy recommendations and risk assessment         ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ Capabilities                                                       ║");
        System.out.println("║    • analyze_code: Code complexity and pattern analysis               ║");
        System.out.println("║    • find_vulnerabilities: Security vulnerability detection           ║");
        System.out.println("║    • recommend_strategy: Deployment strategy selection                ║");
        System.out.println("║    • assess_risk: Deployment risk assessment                          ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ AI Learning                                                        ║");
        System.out.println("║    • Tracks decision outcomes                                         ║");
        System.out.println("║    • Updates success rates per recipe                                 ║");
        System.out.println("║    • Improves confidence calculations over time                       ║");
        System.out.println("║                                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        System.out.println(GREEN + "✓ UAC now has AI-powered decision making with MCP protocol support!" + RESET);
        System.out.println();
    }
}
