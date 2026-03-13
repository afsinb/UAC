package com.blacklight.uac.ai.learning;

import java.util.*;
import java.util.concurrent.*;

/**
 * ReinforcementLearner - Self-evolving reinforcement learning system
 * Learns from outcomes to improve future decisions
 */
public class ReinforcementLearner {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    // Q-learning parameters
    private final Map<String, Map<String, Double>> qTable;
    private final double learningRate; // alpha
    private final double discountFactor; // gamma
    private final double explorationRate; // epsilon
    
    // Experience replay
    private final Deque<Experience> experienceBuffer;
    private final int maxBufferSize;
    
    // Knowledge base
    private final Map<String, KnowledgeEntry> knowledgeBase;
    private final Map<String, PatternLibrary> patternLibrary;
    
    // Statistics
    private int totalEpisodes;
    private int successfulEpisodes;
    private double cumulativeReward;
    
    public ReinforcementLearner() {
        this.qTable = new ConcurrentHashMap<>();
        this.learningRate = 0.1;
        this.discountFactor = 0.9;
        this.explorationRate = 0.1;
        this.experienceBuffer = new ConcurrentLinkedDeque<>();
        this.maxBufferSize = 1000;
        this.knowledgeBase = new ConcurrentHashMap<>();
        this.patternLibrary = new ConcurrentHashMap<>();
        this.totalEpisodes = 0;
        this.successfulEpisodes = 0;
        this.cumulativeReward = 0.0;
        
        System.out.println(CYAN + "🧠 Reinforcement Learner initialized" + RESET);
        System.out.println(YELLOW + "  → Learning rate: " + learningRate + RESET);
        System.out.println(YELLOW + "  → Discount factor: " + discountFactor + RESET);
        System.out.println(YELLOW + "  → Exploration rate: " + explorationRate + RESET);
    }
    
    /**
     * Experience tuple for learning
     */
    public static class Experience {
        public final String state;
        public final String action;
        public final double reward;
        public final String nextState;
        public final long timestamp;
        
        public Experience(String state, String action, double reward, String nextState) {
            this.state = state;
            this.action = action;
            this.reward = reward;
            this.nextState = nextState;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Knowledge entry for learned patterns
     */
    public static class KnowledgeEntry {
        private final String id;
        private final String pattern;
        private final String solution;
        private final double confidence;
        private final int usageCount;
        private final int successCount;
        private final long lastUpdated;
        
        public KnowledgeEntry(String id, String pattern, String solution, double confidence,
                             int usageCount, int successCount) {
            this.id = id;
            this.pattern = pattern;
            this.solution = solution;
            this.confidence = confidence;
            this.usageCount = usageCount;
            this.successCount = successCount;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public KnowledgeEntry incrementUsage(boolean success) {
            return new KnowledgeEntry(
                id, pattern, solution,
                calculateNewConfidence(success),
                usageCount + 1,
                success ? successCount + 1 : successCount
            );
        }
        
        private double calculateNewConfidence(boolean success) {
            int newUsage = usageCount + 1;
            int newSuccess = success ? successCount + 1 : successCount;
            return (double) newSuccess / newUsage;
        }
        
        public String getId() { return id; }
        public String getPattern() { return pattern; }
        public String getSolution() { return solution; }
        public double getConfidence() { return confidence; }
        public int getUsageCount() { return usageCount; }
        public int getSuccessCount() { return successCount; }
    }
    
    /**
     * Pattern library for code patterns
     */
    public static class PatternLibrary {
        private final String name;
        private final List<String> patterns;
        private final Map<String, String> fixes;
        private final Map<String, Integer> occurrences;
        
        public PatternLibrary(String name) {
            this.name = name;
            this.patterns = new ArrayList<>();
            this.fixes = new HashMap<>();
            this.occurrences = new HashMap<>();
        }
        
        public void addPattern(String pattern, String fix) {
            if (!patterns.contains(pattern)) {
                patterns.add(pattern);
                fixes.put(pattern, fix);
                occurrences.put(pattern, 1);
            } else {
                occurrences.merge(pattern, 1, Integer::sum);
            }
        }
        
        public String getFix(String pattern) {
            return fixes.get(pattern);
        }
        
        public int getOccurrences(String pattern) {
            return occurrences.getOrDefault(pattern, 0);
        }
        
        public List<String> getPatterns() { return patterns; }
        public String getName() { return name; }
    }
    
    /**
     * Choose action using epsilon-greedy policy
     */
    public String chooseAction(String state, List<String> availableActions) {
        // Exploration: random action
        if (Math.random() < explorationRate) {
            return availableActions.get(new Random().nextInt(availableActions.size()));
        }
        
        // Exploitation: best known action
        Map<String, Double> stateActions = qTable.getOrDefault(state, new HashMap<>());
        
        return availableActions.stream()
            .max(Comparator.comparingDouble(a -> stateActions.getOrDefault(a, 0.0)))
            .orElse(availableActions.get(0));
    }
    
    /**
     * Learn from experience using Q-learning
     */
    public void learn(Experience experience) {
        // Add to experience buffer
        experienceBuffer.addLast(experience);
        if (experienceBuffer.size() > maxBufferSize) {
            experienceBuffer.removeFirst();
        }
        
        // Update Q-table
        Map<String, Double> stateActions = qTable.computeIfAbsent(
            experience.state, k -> new ConcurrentHashMap<>()
        );
        
        double currentQ = stateActions.getOrDefault(experience.action, 0.0);
        double maxNextQ = getMaxQValue(experience.nextState);
        
        // Q-learning update rule
        double newQ = currentQ + learningRate * (
            experience.reward + discountFactor * maxNextQ - currentQ
        );
        
        stateActions.put(experience.action, newQ);
        
        // Update statistics
        totalEpisodes++;
        cumulativeReward += experience.reward;
        if (experience.reward > 0) {
            successfulEpisodes++;
        }
        
        // Learn patterns
        learnPattern(experience);
    }
    
    /**
     * Learn from a successful fix
     */
    public void learnFromSuccess(String anomalyType, String code, String fix, Map<String, Object> context) {
        String knowledgeId = anomalyType + ":" + hashString(code);
        
        KnowledgeEntry entry = knowledgeBase.getOrDefault(
            knowledgeId,
            new KnowledgeEntry(knowledgeId, code, fix, 0.5, 0, 0)
        );
        
        knowledgeBase.put(knowledgeId, entry.incrementUsage(true));
        
        // Add to pattern library
        PatternLibrary library = patternLibrary.computeIfAbsent(
            anomalyType, k -> new PatternLibrary(anomalyType)
        );
        library.addPattern(extractPattern(code), fix);
        
        System.out.println(GREEN + "🧠 Learned from success: " + anomalyType + RESET);
    }
    
    /**
     * Learn from a failed fix
     */
    public void learnFromFailure(String anomalyType, String code, String attemptedFix, String reason) {
        String knowledgeId = anomalyType + ":" + hashString(code);
        
        KnowledgeEntry entry = knowledgeBase.getOrDefault(
            knowledgeId,
            new KnowledgeEntry(knowledgeId, code, attemptedFix, 0.5, 0, 0)
        );
        
        knowledgeBase.put(knowledgeId, entry.incrementUsage(false));
        
        System.out.println(RED + "🧠 Learned from failure: " + anomalyType + " - " + reason + RESET);
    }
    
    /**
     * Get recommended fix based on learned knowledge
     */
    public Optional<String> getRecommendation(String anomalyType, String code) {
        // Check pattern library first
        PatternLibrary library = patternLibrary.get(anomalyType);
        if (library != null) {
            String pattern = extractPattern(code);
            String fix = library.getFix(pattern);
            if (fix != null) {
                return Optional.of(fix);
            }
        }
        
        // Check knowledge base
        String knowledgeId = anomalyType + ":" + hashString(code);
        KnowledgeEntry entry = knowledgeBase.get(knowledgeId);
        if (entry != null && entry.getConfidence() > 0.7) {
            return Optional.of(entry.getSolution());
        }
        
        return Optional.empty();
    }
    
    /**
     * Get learning insights
     */
    public Map<String, Object> getInsights() {
        Map<String, Object> insights = new HashMap<>();
        
        insights.put("totalEpisodes", totalEpisodes);
        insights.put("successfulEpisodes", successfulEpisodes);
        insights.put("successRate", totalEpisodes > 0 ? (double) successfulEpisodes / totalEpisodes : 0.0);
        insights.put("cumulativeReward", cumulativeReward);
        insights.put("averageReward", totalEpisodes > 0 ? cumulativeReward / totalEpisodes : 0.0);
        insights.put("knowledgeBaseSize", knowledgeBase.size());
        insights.put("patternLibraries", patternLibrary.size());
        insights.put("qTableSize", qTable.size());
        insights.put("experienceBufferSize", experienceBuffer.size());
        
        // Top learned patterns
        List<Map<String, Object>> topPatterns = knowledgeBase.values().stream()
            .filter(e -> e.getConfidence() > 0.8)
            .sorted(Comparator.comparingDouble(KnowledgeEntry::getConfidence).reversed())
            .limit(5)
            .map(e -> {
                Map<String, Object> p = new HashMap<>();
                p.put("id", e.getId());
                p.put("confidence", e.getConfidence());
                p.put("usageCount", e.getUsageCount());
                return p;
            })
            .toList();
        insights.put("topPatterns", topPatterns);
        
        return insights;
    }
    
    /**
     * Evolve the system based on accumulated knowledge
     */
    public Map<String, Object> evolve() {
        System.out.println(CYAN + "🧬 System Evolution - Analyzing learned patterns..." + RESET);
        
        Map<String, Object> evolution = new HashMap<>();
        List<String> improvements = new ArrayList<>();
        
        // Analyze Q-table for optimal strategies
        Map<String, String> optimalStrategies = new HashMap<>();
        qTable.forEach((state, actions) -> {
            actions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    optimalStrategies.put(state, entry.getKey());
                    improvements.add("Optimal action for '" + state + "': " + entry.getKey());
                });
        });
        
        // Identify high-confidence patterns
        List<String> reliablePatterns = knowledgeBase.values().stream()
            .filter(e -> e.getConfidence() > 0.9 && e.getUsageCount() > 5)
            .map(KnowledgeEntry::getPattern)
            .toList();
        
        // Identify areas needing improvement
        List<String> weakAreas = knowledgeBase.values().stream()
            .filter(e -> e.getConfidence() < 0.5 && e.getUsageCount() > 3)
            .map(e -> "Weak area: " + e.getId() + " (confidence: " + 
                String.format("%.2f", e.getConfidence()) + ")")
            .toList();
        
        evolution.put("optimalStrategies", optimalStrategies);
        evolution.put("reliablePatterns", reliablePatterns);
        evolution.put("weakAreas", weakAreas);
        evolution.put("improvements", improvements);
        evolution.put("evolutionLevel", calculateEvolutionLevel());
        
        System.out.println(GREEN + "  ✓ Evolution complete - Level: " + 
            String.format("%.2f", calculateEvolutionLevel()) + RESET);
        
        return evolution;
    }
    
    private double getMaxQValue(String state) {
        Map<String, Double> stateActions = qTable.getOrDefault(state, new HashMap<>());
        return stateActions.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
    }
    
    private void learnPattern(Experience experience) {
        if (experience.reward > 0) {
            // Successful action - reinforce pattern
            PatternLibrary library = patternLibrary.computeIfAbsent(
                experience.state, k -> new PatternLibrary(experience.state)
            );
            library.addPattern(experience.state, experience.action);
        }
    }
    
    private String extractPattern(String code) {
        // Simple pattern extraction - normalize code
        return code.replaceAll("\\s+", " ")
                  .replaceAll("\\w+\\s*=", "VAR =")
                  .replaceAll("\\d+", "NUM")
                  .trim();
    }
    
    private String hashString(String input) {
        return String.valueOf(Math.abs(input.hashCode()));
    }
    
    private double calculateEvolutionLevel() {
        if (totalEpisodes == 0) return 0.0;
        
        double successComponent = (double) successfulEpisodes / totalEpisodes * 0.4;
        double knowledgeComponent = Math.min(1.0, knowledgeBase.size() / 100.0) * 0.3;
        double patternComponent = Math.min(1.0, patternLibrary.size() / 10.0) * 0.3;
        
        return successComponent + knowledgeComponent + patternComponent;
    }
}
