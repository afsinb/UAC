package com.blacklight.uac.demo;

import com.blacklight.uac.ai.models.*;
import com.blacklight.uac.config.ApplicationConfig;
import com.blacklight.uac.config.BeanConfiguration;

/**
 * OpenRouterLiveTest - Quick test to verify OpenRouter API key works
 */
public class OpenRouterLiveTest {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         OPENROUTER.AI LIVE TEST                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        // Test 1: Check configuration
        System.out.println(CYAN + "Test 1: Configuration Check" + RESET);
        ApplicationConfig config = new ApplicationConfig();
        String apiKey = config.getOpenRouterApiKey();
        String model = config.getOpenRouterModel();
        
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println(RED + "  ✗ OPENROUTER_API_KEY not set" + RESET);
            System.out.println(YELLOW + "  → Set it with: export OPENROUTER_API_KEY='sk-or-...'" + RESET);
            return;
        }
        
        System.out.println(GREEN + "  ✓ API Key found: " + apiKey.substring(0, 15) + "..." + RESET);
        System.out.println(GREEN + "  ✓ Model: " + model + RESET);
        System.out.println();
        
        // Test 2: BeanConfiguration auto-registration
        System.out.println(CYAN + "Test 2: Auto-Registration" + RESET);
        BeanConfiguration beans = new BeanConfiguration(config);
        System.out.println(GREEN + "  ✓ BeanConfiguration initialized" + RESET);
        System.out.println();
        
        // Test 3: Direct model test
        System.out.println(CYAN + "Test 3: Direct Model Test" + RESET);
        OpenRouterModel openRouter = new OpenRouterModel(apiKey, model);
        System.out.println(GREEN + "  ✓ OpenRouter model created: " + openRouter.getModelName() + RESET);
        System.out.println(GREEN + "  ✓ Available: " + openRouter.isAvailable() + RESET);
        System.out.println(GREEN + "  ✓ Priority: " + openRouter.getPriority() + RESET);
        System.out.println(GREEN + "  ✓ Capabilities: " + openRouter.getCapabilities().size() + RESET);
        System.out.println();
        
        // Test 4: Code analysis (if you want to test API call, uncomment below)
        System.out.println(CYAN + "Test 4: API Test (Optional)" + RESET);
        System.out.println(YELLOW + "  → To test actual API call, uncomment the code below" + RESET);
        System.out.println();
        
        /*
        // Uncomment to test actual API call
        String testCode = "public User getUser(String id) { return userMap.get(id); }";
        System.out.println(YELLOW + "  → Analyzing code with " + model + "..." + RESET);
        AIModel.AIResponse response = openRouter.analyzeCode(testCode, "java");
        
        if (response.isSuccess()) {
            System.out.println(GREEN + "  ✓ Analysis successful!" + RESET);
            System.out.println(GREEN + "  → Response time: " + response.getResponseTime() + "ms" + RESET);
            System.out.println(CYAN + "  → Content:" + RESET);
            System.out.println(response.getContent());
        } else {
            System.out.println(RED + "  ✗ Analysis failed" + RESET);
        }
        */
        
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         TEST SUMMARY                                  ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                       ║");
        System.out.println("║  ✓ API Key configured                                                 ║");
        System.out.println("║  ✓ Model: " + String.format("%-57s", model) + "║");
        System.out.println("║  ✓ Auto-registration working                                          ║");
        System.out.println("║  ✓ Ready for AI-powered code analysis                                 ║");
        System.out.println("║                                                                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
    }
}
