package com.blacklight.uac.ai;

import java.util.*;

/**
 * AIDecisionEngine - AI-powered decision making for UAC
 * Analyzes anomalies and recommends optimal fix strategies
 */
public class AIDecisionEngine {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final List<FixRecipe> recipes;
    private final Map<String, Double> successRates;
    private final List<DecisionHistory> history;
    
    public AIDecisionEngine() {
        this.recipes = new ArrayList<>();
        this.successRates = new HashMap<>();
        this.history = new ArrayList<>();
        initializeRecipes();
    }
    
    /**
     * AI Decision result
     */
    public static class AIDecision {
        private final String anomalyType;
        private final FixRecipe recommendedRecipe;
        private final double confidence;
        private final String reasoning;
        private final List<String> alternativeOptions;
        private final Map<String, Object> context;
        
        public AIDecision(String anomalyType, FixRecipe recommendedRecipe, double confidence,
                         String reasoning, List<String> alternativeOptions, Map<String, Object> context) {
            this.anomalyType = anomalyType;
            this.recommendedRecipe = recommendedRecipe;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.alternativeOptions = alternativeOptions;
            this.context = context;
        }
        
        public String getAnomalyType() { return anomalyType; }
        public FixRecipe getRecommendedRecipe() { return recommendedRecipe; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public List<String> getAlternativeOptions() { return alternativeOptions; }
        public Map<String, Object> getContext() { return context; }
        
        @Override
        public String toString() {
            return String.format("AIDecision{anomaly='%s', recipe='%s', confidence=%.2f}",
                anomalyType, recommendedRecipe.getName(), confidence);
        }
    }
    
    /**
     * Fix Recipe - Pre-defined fix pattern
     */
    public static class FixRecipe {
        private final String id;
        private final String name;
        private final String description;
        private final String anomalyType;
        private final List<String> steps;
        private final Map<String, String> codePatterns;
        private final double successRate;
        private final int priority;
        private final boolean requiresApproval;
        
        public FixRecipe(String id, String name, String description, String anomalyType,
                        List<String> steps, Map<String, String> codePatterns,
                        double successRate, int priority, boolean requiresApproval) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.anomalyType = anomalyType;
            this.steps = steps;
            this.codePatterns = codePatterns;
            this.successRate = successRate;
            this.priority = priority;
            this.requiresApproval = requiresApproval;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getAnomalyType() { return anomalyType; }
        public List<String> getSteps() { return steps; }
        public Map<String, String> getCodePatterns() { return codePatterns; }
        public double getSuccessRate() { return successRate; }
        public int getPriority() { return priority; }
        public boolean isRequiresApproval() { return requiresApproval; }
    }
    
    /**
     * Decision History for learning
     */
    public static class DecisionHistory {
        private final String anomalyType;
        private final String recipeId;
        private final boolean success;
        private final long timestamp;
        private final Map<String, Object> metrics;
        
        public DecisionHistory(String anomalyType, String recipeId, boolean success, Map<String, Object> metrics) {
            this.anomalyType = anomalyType;
            this.recipeId = recipeId;
            this.success = success;
            this.timestamp = System.currentTimeMillis();
            this.metrics = metrics;
        }
    }
    
    /**
     * Analyze anomaly and recommend fix
     */
    public AIDecision analyze(String anomalyType, Map<String, Object> context) {
        System.out.println(CYAN + "🤖 AI Decision Engine analyzing..." + RESET);
        
        // Find matching recipes
        List<FixRecipe> matchingRecipes = findMatchingRecipes(anomalyType);
        
        if (matchingRecipes.isEmpty()) {
            System.out.println(YELLOW + "  → No recipes found for anomaly type: " + anomalyType + RESET);
            return createNoSolutionDecision(anomalyType, context);
        }
        
        // Score and rank recipes
        FixRecipe bestRecipe = selectBestRecipe(matchingRecipes, context);
        
        // Calculate confidence based on historical success
        double confidence = calculateConfidence(bestRecipe, context);
        
        // Generate reasoning
        String reasoning = generateReasoning(bestRecipe, matchingRecipes, context);
        
        // Get alternatives
        List<String> alternatives = matchingRecipes.stream()
            .filter(r -> !r.getId().equals(bestRecipe.getId()))
            .map(FixRecipe::getName)
            .toList();
        
        System.out.println(GREEN + "  ✓ Recommended: " + bestRecipe.getName() + 
                          " (confidence: " + String.format("%.0f%%", confidence * 100) + ")" + RESET);
        
        return new AIDecision(anomalyType, bestRecipe, confidence, reasoning, alternatives, context);
    }
    
    /**
     * Learn from decision outcome
     */
    public void learn(String anomalyType, String recipeId, boolean success, Map<String, Object> metrics) {
        DecisionHistory entry = new DecisionHistory(anomalyType, recipeId, success, metrics);
        history.add(entry);
        
        // Update success rates
        String key = anomalyType + ":" + recipeId;
        double currentRate = successRates.getOrDefault(key, 0.5);
        double newRate = (currentRate * 0.9) + (success ? 0.1 : 0.0);
        successRates.put(key, newRate);
        
        System.out.println(CYAN + "🧠 AI Learning: " + (success ? "Success" : "Failure") + 
                          " recorded for " + recipeId + RESET);
    }
    
    /**
     * Get AI insights and recommendations
     */
    public Map<String, Object> getInsights() {
        Map<String, Object> insights = new HashMap<>();
        
        // Calculate overall success rate
        long totalDecisions = history.size();
        long successfulDecisions = history.stream().filter(h -> h.success).count();
        double overallSuccessRate = totalDecisions > 0 ? (double) successfulDecisions / totalDecisions : 0.0;
        
        insights.put("totalDecisions", totalDecisions);
        insights.put("successfulDecisions", successfulDecisions);
        insights.put("overallSuccessRate", overallSuccessRate);
        insights.put("recipesAvailable", recipes.size());
        insights.put("successRates", new HashMap<>(successRates));
        
        return insights;
    }
    
    private void initializeRecipes() {
        // NullPointerException recipes
        recipes.add(new FixRecipe(
            "NPE-001",
            "Add Null Check",
            "Wrap potentially null variable access with null check",
            "NULL_POINTER_EXCEPTION",
            Arrays.asList(
                "Identify the null variable",
                "Add null check before access",
                "Add logging for null case",
                "Return safe default value"
            ),
            Map.of(
                "pattern", "(\\w+)\\.(\\w+)",
                "replacement", "if ($1 != null) { $1.$2 } else { logger.warn(\"$1 is null\"); }"
            ),
            0.95,
            1,
            false
        ));
        
        recipes.add(new FixRecipe(
            "NPE-002",
            "Use Optional",
            "Replace null-prone code with Optional pattern",
            "NULL_POINTER_EXCEPTION",
            Arrays.asList(
                "Wrap return type with Optional",
                "Use Optional.ofNullable()",
                "Use orElse() for default"
            ),
            Map.of(
                "pattern", "return (\\w+);",
                "replacement", "return Optional.ofNullable($1).orElse(defaultValue);"
            ),
            0.90,
            2,
            false
        ));
        
        // ArrayIndexOutOfBoundsException recipes
        recipes.add(new FixRecipe(
            "AIOB-001",
            "Add Bounds Check",
            "Add array bounds validation before access",
            "ARRAY_INDEX_OUT_OF_BOUNDS",
            Arrays.asList(
                "Identify array access",
                "Add bounds check",
                "Handle out-of-bounds case"
            ),
            Map.of(
                "pattern", "(\\w+)\\[(\\w+)\\]",
                "replacement", "($2 >= 0 && $2 < $1.length) ? $1[$2] : defaultValue"
            ),
            0.92,
            1,
            false
        ));
        
        // ClassCastException recipes
        recipes.add(new FixRecipe(
            "CCE-001",
            "Add Type Check",
            "Add instanceof check before casting",
            "CLASS_CAST_EXCEPTION",
            Arrays.asList(
                "Identify cast operation",
                "Add instanceof check",
                "Handle incompatible type"
            ),
            Map.of(
                "pattern", "\\(([A-Z]\\w+)\\)\\s*(\\w+)",
                "replacement", "($2 instanceof $1) ? ($1) $2 : null"
            ),
            0.93,
            1,
            false
        ));
        
        // Connection timeout recipes
        recipes.add(new FixRecipe(
            "TIMEOUT-001",
            "Add Retry Logic",
            "Implement exponential backoff retry",
            "CONNECTION_TIMEOUT",
            Arrays.asList(
                "Wrap connection in retry loop",
                "Implement exponential backoff",
                "Set max retry count",
                "Add circuit breaker"
            ),
            Map.of(
                "pattern", "connect\\(\\)",
                "replacement", "retryWithBackoff(() -> connect(), 3, 1000)"
            ),
            0.85,
            1,
            true
        ));
        
        // OutOfMemoryError recipes
        recipes.add(new FixRecipe(
            "OOM-001",
            "Increase Heap Size",
            "Recommend JVM heap size increase",
            "OUT_OF_MEMORY_ERROR",
            Arrays.asList(
                "Analyze memory usage",
                "Calculate required heap",
                "Update JVM parameters",
                "Monitor after change"
            ),
            Map.of(
                "pattern", "-Xmx(\\d+)[mg]",
                "replacement", "-Xmx${current * 2}m"
            ),
            0.80,
            1,
            true
        ));
        
        // Database error recipes
        recipes.add(new FixRecipe(
            "DB-001",
            "Add Connection Pool",
            "Implement connection pooling",
            "DATABASE_ERROR",
            Arrays.asList(
                "Add connection pool config",
                "Set pool size limits",
                "Add connection validation",
                "Implement failover"
            ),
            Map.of(),
            0.88,
            1,
            true
        ));
    }
    
    private List<FixRecipe> findMatchingRecipes(String anomalyType) {
        return recipes.stream()
            .filter(r -> r.getAnomalyType().equals(anomalyType))
            .sorted(Comparator.comparingInt(FixRecipe::getPriority))
            .toList();
    }
    
    private FixRecipe selectBestRecipe(List<FixRecipe> candidates, Map<String, Object> context) {
        // Score each recipe
        return candidates.stream()
            .max(Comparator.comparingDouble(r -> scoreRecipe(r, context)))
            .orElse(candidates.get(0));
    }
    
    private double scoreRecipe(FixRecipe recipe, Map<String, Object> context) {
        double score = recipe.getSuccessRate() * 0.6; // Historical success (60%)
        score += (1.0 / recipe.getPriority()) * 0.2; // Priority (20%)
        score += (recipe.isRequiresApproval() ? 0.1 : 0.2); // Auto-approval bonus (20%)
        return score;
    }
    
    private double calculateConfidence(FixRecipe recipe, Map<String, Object> context) {
        String key = recipe.getAnomalyType() + ":" + recipe.getId();
        double historicalRate = successRates.getOrDefault(key, recipe.getSuccessRate());
        
        // Adjust confidence based on context
        double confidence = historicalRate;
        
        // Reduce confidence for complex scenarios
        if (context.containsKey("complexity") && "high".equals(context.get("complexity"))) {
            confidence *= 0.8;
        }
        
        return Math.min(0.99, Math.max(0.1, confidence));
    }
    
    private String generateReasoning(FixRecipe recipe, List<FixRecipe> alternatives, Map<String, Object> context) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("Selected '").append(recipe.getName()).append("' because:\n");
        reasoning.append("  • Historical success rate: ").append(String.format("%.0f%%", recipe.getSuccessRate() * 100)).append("\n");
        reasoning.append("  • Priority level: ").append(recipe.getPriority()).append("\n");
        reasoning.append("  • Auto-approval: ").append(recipe.isRequiresApproval() ? "No" : "Yes").append("\n");
        
        if (!alternatives.isEmpty()) {
            reasoning.append("  • ").append(alternatives.size()).append(" alternative(s) available\n");
        }
        
        return reasoning.toString();
    }
    
    private AIDecision createNoSolutionDecision(String anomalyType, Map<String, Object> context) {
        return new AIDecision(
            anomalyType,
            null,
            0.0,
            "No automated fix recipe available for this anomaly type. Manual review required.",
            Collections.emptyList(),
            context
        );
    }
}
