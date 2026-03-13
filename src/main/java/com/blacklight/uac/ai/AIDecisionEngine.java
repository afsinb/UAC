package com.blacklight.uac.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * AIDecisionEngine - AI-powered decision making for UAC.
 *
 * Recipes are externalized to YAML files:
 * - config/next/fix-recipes.yaml            (seeded/static)
 * - config/next/fix-recipes-learned.yaml    (runtime learned)
 */
public class AIDecisionEngine {

    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final List<FixRecipe> recipes;
    private final Map<String, Double> successRates;
    private final List<DecisionHistory> history;

    private final Path recipeFile;
    private final Path learnedRecipeFile;

    public AIDecisionEngine() {
        this(Paths.get(System.getProperty("user.dir"), "config", "next", "fix-recipes.yaml"),
                Paths.get(System.getProperty("user.dir"), "config", "next", "fix-recipes-learned.yaml"));
    }

    public AIDecisionEngine(Path recipeFile, Path learnedRecipeFile) {
        this.recipes = new ArrayList<>();
        this.successRates = new HashMap<>();
        this.history = new ArrayList<>();
        this.recipeFile = recipeFile;
        this.learnedRecipeFile = learnedRecipeFile;
        initializeRecipes();
    }

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
            String recipeName = recommendedRecipe != null ? recommendedRecipe.getName() : "NO_RECIPE";
            return String.format("AIDecision{anomaly='%s', recipe='%s', confidence=%.2f}",
                    anomalyType, recipeName, confidence);
        }
    }

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

    public AIDecision analyze(String anomalyType, Map<String, Object> context) {
        System.out.println(CYAN + "🤖 AI Decision Engine analyzing..." + RESET);

        List<FixRecipe> matchingRecipes = findMatchingRecipes(anomalyType);

        if (matchingRecipes.isEmpty()) {
            System.out.println(YELLOW + "  → No recipes found for anomaly type: " + anomalyType + RESET);
            return createNoSolutionDecision(anomalyType, context);
        }

        FixRecipe bestRecipe = selectBestRecipe(matchingRecipes, context);
        double confidence = calculateConfidence(bestRecipe, context);
        String reasoning = generateReasoning(bestRecipe, matchingRecipes, context);

        List<String> alternatives = matchingRecipes.stream()
                .filter(r -> !r.getId().equals(bestRecipe.getId()))
                .map(FixRecipe::getName)
                .toList();

        System.out.println(GREEN + "  ✓ Recommended: " + bestRecipe.getName() +
                " (confidence: " + String.format("%.0f%%", confidence * 100) + ")" + RESET);

        return new AIDecision(anomalyType, bestRecipe, confidence, reasoning, alternatives, context);
    }

    public void learn(String anomalyType, String recipeId, boolean success, Map<String, Object> metrics) {
        DecisionHistory entry = new DecisionHistory(anomalyType, recipeId, success, metrics);
        history.add(entry);

        String key = anomalyType + ":" + recipeId;
        double currentRate = successRates.getOrDefault(key, 0.5);
        double newRate = (currentRate * 0.9) + (success ? 0.1 : 0.0);
        successRates.put(key, newRate);

        System.out.println(CYAN + "🧠 AI Learning: " + (success ? "Success" : "Failure") +
                " recorded for " + recipeId + RESET);
    }

    public synchronized boolean hasExactRecipe(String anomalyType) {
        return recipes.stream().anyMatch(r -> r.getAnomalyType().equals(anomalyType));
    }

    /**
     * Register a newly learned generic recipe for an unseen anomaly type and
     * persist it to the learned recipe YAML file.
     */
    public synchronized FixRecipe registerLearnedRecipe(String anomalyType, String exceptionClass) {
        Optional<FixRecipe> exact = recipes.stream()
                .filter(r -> r.getAnomalyType().equals(anomalyType))
                .findFirst();
        if (exact.isPresent()) return exact.get();

        FixRecipe learned = new FixRecipe(
                "LEARNED-" + anomalyType,
                "Generic Safety Guard for " + anomalyType,
                "Auto-learned recipe for exception class " + exceptionClass,
                anomalyType,
                List.of(
                        "Capture stack trace and context",
                        "Add validation/guard around failing call",
                        "Fallback to safe default",
                        "Create PR and require review"
                ),
                Map.of(),
                0.70,
                2,
                true
        );
        recipes.add(learned);
        persistLearnedRecipes();
        return learned;
    }

    public Map<String, Object> getInsights() {
        Map<String, Object> insights = new HashMap<>();
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
        recipes.clear();
        recipes.addAll(loadRecipes(recipeFile));
        recipes.addAll(loadRecipes(learnedRecipeFile));

        if (recipes.isEmpty()) {
            // Minimal fallback to keep runtime safe if external files are missing.
            recipes.add(new FixRecipe(
                    "GENERIC-001",
                    "Manual Investigation",
                    "Fallback recipe when no external catalog is available",
                    "UNKNOWN_EXCEPTION",
                    List.of("Collect context", "Create incident", "Manual fix"),
                    Map.of(),
                    0.5,
                    10,
                    true
            ));
        }
    }

    private List<FixRecipe> loadRecipes(Path file) {
        if (file == null || !Files.exists(file)) return List.of();

        List<FixRecipe> loaded = new ArrayList<>();
        Map<String, String> current = null;

        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || "recipes:".equals(line)) continue;

                if (line.startsWith("-")) {
                    if (current != null) loaded.add(mapToRecipe(current));
                    current = new LinkedHashMap<>();
                    String rest = line.substring(1).trim();
                    if (!rest.isEmpty() && rest.contains(":")) {
                        String[] kv = rest.split(":", 2);
                        current.put(kv[0].trim(), strip(kv[1].trim()));
                    }
                    continue;
                }

                if (current != null && line.contains(":")) {
                    String[] kv = line.split(":", 2);
                    current.put(kv[0].trim(), strip(kv[1].trim()));
                }
            }
            if (current != null) loaded.add(mapToRecipe(current));
        } catch (IOException ignored) {
            return List.of();
        }

        return loaded.stream().filter(Objects::nonNull).toList();
    }

    private FixRecipe mapToRecipe(Map<String, String> m) {
        String anomalyType = m.get("anomalyType");
        if (anomalyType == null || anomalyType.isBlank()) return null;

        String id = defaultIfBlank(m.get("id"), "AUTO-" + anomalyType);
        String name = defaultIfBlank(m.get("name"), "Recipe for " + anomalyType);
        String description = defaultIfBlank(m.get("description"), "Externalized recipe");
        double successRate = parseDouble(m.get("successRate"), 0.75);
        int priority = parseInt(m.get("priority"), 3);
        boolean requiresApproval = Boolean.parseBoolean(defaultIfBlank(m.get("requiresApproval"), "false"));

        List<String> steps = List.of();
        String stepsCsv = m.get("stepsCsv");
        if (stepsCsv != null && !stepsCsv.isBlank()) {
            steps = Arrays.stream(stepsCsv.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return new FixRecipe(id, name, description, anomalyType, steps, Map.of(), successRate, priority, requiresApproval);
    }

    private synchronized void persistLearnedRecipes() {
        try {
            if (learnedRecipeFile.getParent() != null) {
                Files.createDirectories(learnedRecipeFile.getParent());
            }
            StringBuilder out = new StringBuilder("recipes:\n");
            for (FixRecipe r : recipes) {
                if (!r.getId().startsWith("LEARNED-")) continue;
                out.append("  - id: \"").append(escape(r.getId())).append("\"\n");
                out.append("    name: \"").append(escape(r.getName())).append("\"\n");
                out.append("    anomalyType: \"").append(escape(r.getAnomalyType())).append("\"\n");
                out.append("    description: \"").append(escape(r.getDescription())).append("\"\n");
                out.append("    successRate: ").append(r.getSuccessRate()).append("\n");
                out.append("    priority: ").append(r.getPriority()).append("\n");
                out.append("    requiresApproval: ").append(r.isRequiresApproval()).append("\n");
                if (!r.getSteps().isEmpty()) {
                    out.append("    stepsCsv: \"").append(escape(String.join("|", r.getSteps()))).append("\"\n");
                }
            }
            Files.writeString(
                    learnedRecipeFile,
                    out.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }

    private List<FixRecipe> findMatchingRecipes(String anomalyType) {
        return recipes.stream()
                .filter(r -> r.getAnomalyType().equals(anomalyType) || "UNKNOWN_EXCEPTION".equals(r.getAnomalyType()))
                .sorted(Comparator.comparingInt(FixRecipe::getPriority))
                .toList();
    }

    private FixRecipe selectBestRecipe(List<FixRecipe> candidates, Map<String, Object> context) {
        return candidates.stream()
                .max(Comparator.comparingDouble(r -> scoreRecipe(r, context)))
                .orElse(candidates.get(0));
    }

    private double scoreRecipe(FixRecipe recipe, Map<String, Object> context) {
        double score = recipe.getSuccessRate() * 0.6;
        score += (1.0 / Math.max(1, recipe.getPriority())) * 0.2;
        score += (recipe.isRequiresApproval() ? 0.1 : 0.2);
        return score;
    }

    private double calculateConfidence(FixRecipe recipe, Map<String, Object> context) {
        String key = recipe.getAnomalyType() + ":" + recipe.getId();
        double historicalRate = successRates.getOrDefault(key, recipe.getSuccessRate());
        double confidence = historicalRate;
        if (context != null && "high".equals(context.get("complexity"))) {
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
        if (alternatives.size() > 1) {
            reasoning.append("  • ").append(alternatives.size() - 1).append(" alternative(s) available\n");
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

    private String strip(String v) {
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private String defaultIfBlank(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }

    private double parseDouble(String v, double d) {
        try { return Double.parseDouble(v); } catch (Exception e) { return d; }
    }

    private int parseInt(String v, int d) {
        try { return Integer.parseInt(v); } catch (Exception e) { return d; }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
