package com.blacklight.uac.demo;

import com.blacklight.uac.docker.DockerManager;
import com.blacklight.uac.git.GitHubAPI;
import com.blacklight.uac.ai.AIDecisionEngine;
import com.blacklight.uac.evolver.PRDeploymentGate;
import com.blacklight.uac.evolver.PRLifecycleState;
import com.blacklight.uac.ui.SelfHealingDashboard;
import com.blacklight.uac.ui.SimpleDashboard;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads local system definitions from config/systems/*.yaml and attaches
 * already-running apps to the UAC dashboard.
 */
public class LocalSystemsMonitorDemo {

    private static final Path DEFAULT_CONFIG_DIR = Paths.get("/Users/afsinbuyuksarac/development/UAC/config/systems");

    private final SelfHealingDashboard dataModel;
    private final SimpleDashboard dashboard;
    private final ScheduledExecutorService scheduler;
    private final Map<String, SystemConfig> systems;
    private final boolean realPrMode;
    private final AIDecisionEngine aiDecisionEngine;
    private final AnomalyRuleRegistry anomalyRuleRegistry;
    private final PRDeploymentGate deploymentGate;

    public LocalSystemsMonitorDemo(int dashboardPort, int dataPort) throws IOException {
        this.dataModel = new SelfHealingDashboard(dataPort);
        this.dashboard = new SimpleDashboard(dataModel, dashboardPort);
        this.scheduler = Executors.newScheduledThreadPool(4);
        this.systems = new LinkedHashMap<>();
        this.realPrMode = "true".equalsIgnoreCase(System.getenv().getOrDefault("UAC_REAL_PR", "false"));
        this.aiDecisionEngine = new AIDecisionEngine();
        this.deploymentGate = PRDeploymentGate.getInstance();
        Path projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        this.anomalyRuleRegistry = new AnomalyRuleRegistry(
                projectRoot.resolve("config").resolve("next").resolve("anomaly-rules.yaml"),
                projectRoot.resolve("config").resolve("next").resolve("anomaly-learned.yaml")
        );
    }

    public static void main(String[] args) throws Exception {
        LocalSystemsMonitorDemo demo = new LocalSystemsMonitorDemo(8888, 8889);
        demo.loadConfigs(DEFAULT_CONFIG_DIR);
        demo.start();

        while (true) {
            Thread.sleep(1000);
        }
    }

    public void loadConfigs(Path configDir) throws IOException {
        if (!Files.exists(configDir)) {
            throw new IOException("Config directory not found: " + configDir);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.yaml")) {
            for (Path file : stream) {
                SystemConfig cfg = parseYaml(file);
                if (cfg != null) {
                    systems.put(cfg.name, cfg);
                }
            }
        }

        if (systems.isEmpty()) {
            throw new IOException("No valid *.yaml system config found in " + configDir);
        }
    }

    public void start() {
        dashboard.start();

        System.out.println("\nAttached systems:");
        for (SystemConfig cfg : systems.values()) {
            dataModel.registerSystem(cfg.name);
            dataModel.updateHealthScore(dataModel.getSystemId(cfg.name), 1.0);
            System.out.println(" - " + cfg.name + " -> " + cfg.healthUrl);
        }

        scheduler.scheduleAtFixedRate(this::pollSystems, 0, 10, TimeUnit.SECONDS);
        System.out.println("\nDashboard URL: http://localhost:8888");
        System.out.println("PR Mode: " + (realPrMode ? "REAL (git+github)" : "SIMULATED"));
    }


    private void pollSystems() {
        for (SystemConfig cfg : systems.values()) {
            try {
                Map<String, Long> health = fetchHealth(cfg.healthUrl);
                updateHealthAndGenerateFlows(cfg, health);
                scanNewLogLines(cfg);
                refreshWaitingDependencyStates(cfg);
            } catch (Exception ex) {
                addAlarm(cfg, "MONITORING_FAILURE", "HIGH", "Health poll failed: " + ex.getMessage(), null);
            }
        }
    }

    private Map<String, Long> fetchHealth(String healthUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(healthUrl).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        if (conn.getResponseCode() != 200) {
            throw new IOException("HTTP " + conn.getResponseCode());
        }

        String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Long> out = new HashMap<>();
        out.put("errors", extractLong(json, "errors"));
        out.put("memory_mb", extractLong(json, "memory_mb"));
        out.put("cache_size", extractLong(json, "cache_size"));
        out.put("leak_size", extractLong(json, "leak_size"));
        return out;
    }

    private void updateHealthAndGenerateFlows(SystemConfig cfg, Map<String, Long> health) {
        String systemId = dataModel.getSystemId(cfg.name);

        long errors = health.getOrDefault("errors", 0L);
        long memory = health.getOrDefault("memory_mb", 0L);
        long cacheSize = health.getOrDefault("cache_size", 0L);
        long leakSize = health.getOrDefault("leak_size", 0L);

        double score = 1.0;
        score -= Math.min(0.60, errors * 0.04);
        score -= Math.min(0.40, memory / 200.0);
        score -= Math.min(0.35, cacheSize / 20000.0);
        score -= Math.min(0.35, leakSize / 5000.0);
        score = Math.max(0.05, Math.min(1.0, score));
        dataModel.updateHealthScore(systemId, score);

        if (errors > 0 && shouldTrigger(cfg, "errors", 45)) {
            createCodeFixFlow(cfg,
                    "NULL_POINTER_EXCEPTION",
                    "HIGH",
                    "Detected application errors from /health (errors=" + errors + ")",
                    "PaymentService.java",
                    24,
                    "Add defensive null-checks for request payload",
                    "PR-" + shortId());
        }

        if ((memory > 60 || leakSize > 1000) && shouldTrigger(cfg, "memory", 90)) {
            createOperationalFlow(cfg,
                    "MEMORY_LEAK",
                    "HIGH",
                    "Detected memory growth trend (memory=" + memory + "MB, leakSize=" + leakSize + ")",
                    "Restart local process",
                    "RESTART_SERVICE");
        }

        if (cacheSize > 2500 && shouldTrigger(cfg, "cache-size", 90)) {
            createCodeFixFlow(cfg,
                    "CACHE_EVICTION_MISSING",
                    "MEDIUM",
                    "Detected unbounded cache growth (size=" + cacheSize + ")",
                    "CacheService.java",
                    14,
                    "Introduce bounded LRU cache eviction",
                    "PR-" + shortId());
        }
    }

    private void scanNewLogLines(SystemConfig cfg) {
        Path log = Paths.get(cfg.logPath);
        if (!Files.exists(log)) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(log.toFile(), "r")) {
            long len = raf.length();
            if (len < cfg.lastLogOffset) {
                cfg.lastLogOffset = 0;
            }

            raf.seek(cfg.lastLogOffset);
            String line;
            while ((line = raf.readLine()) != null) {
                String normalized = line.toUpperCase(Locale.ROOT);

                // 1) Catalog-based detection (external rules YAML)
                AnomalyRuleRegistry.AnomalyRule matched = anomalyRuleRegistry.findMatchingRule(line);
                if (matched != null && shouldTrigger(cfg, "rule-" + matched.anomalyType, 45)) {
                    handleAnomalyByRule(cfg, matched, line);
                }

                // 2) Unknown exception detection and online learning
                String exceptionClass = anomalyRuleRegistry.extractExceptionClass(line);
                if (exceptionClass != null) {
                    String learnedType = anomalyRuleRegistry.normalizeAnomalyType(exceptionClass);
                    if (shouldTrigger(cfg, "unknown-" + learnedType, 45)) {
                        AnomalyRuleRegistry.AnomalyRule learnedRule = anomalyRuleRegistry.ensureLearnedRule(exceptionClass);
                        handleAnomalyByRule(cfg, learnedRule, line);
                    }
                }

                if (normalized.contains("ERROR") || normalized.contains("EXCEPTION")) {
                    addAlarm(cfg, "ANOMALY_DETECTED", "HIGH", line, null);
                    dataModel.recordAnomalyDetected(dataModel.getSystemId(cfg.name), "LOG_EXCEPTION", "HIGH");
                } else if (normalized.contains("WARN")) {
                    addAlarm(cfg, "WARNING_SIGNAL", "MEDIUM", line, null);
                }

                // UAC self-healing trigger: excessive alarm-debug noise in dashboard logs.
                if ("uac-core".equals(cfg.name)
                        && line.contains("Checking alarm:")
                        && shouldTrigger(cfg, "uac-log-spam", 180)) {
                    createCodeFixFlow(
                            cfg,
                            "UAC_LOG_SPAM",
                            "MEDIUM",
                            "Detected excessive alarm debug logging in dashboard API path",
                            "SimpleDashboard.java",
                            160,
                            "Reduce noisy alarm debug logging",
                            "PR-" + shortId());
                }
            }
            cfg.lastLogOffset = raf.getFilePointer();
        } catch (IOException ignored) {
        }
    }

    private void handleAnomalyByRule(SystemConfig cfg, AnomalyRuleRegistry.AnomalyRule rule, String logLine) {
        String systemId = dataModel.getSystemId(cfg.name);

        addAlarm(cfg, "ANOMALY_DETECTED", defaultIfBlank(rule.severity, "HIGH"), logLine, null);
        dataModel.recordAnomalyDetected(systemId, rule.anomalyType, defaultIfBlank(rule.severity, "HIGH"));

        Map<String, Object> context = new HashMap<>();
        context.put("system", cfg.name);
        context.put("logLine", logLine);
        context.put("sourceFile", defaultIfBlank(rule.sourceFile, "UnknownSource.java"));
        context.put("lineNumber", rule.lineNumber > 0 ? rule.lineNumber : 1);

        if (!aiDecisionEngine.hasExactRecipe(rule.anomalyType)) {
            aiDecisionEngine.registerLearnedRecipe(rule.anomalyType, defaultIfBlank(rule.match, rule.anomalyType));
        }

        AIDecisionEngine.AIDecision decision = aiDecisionEngine.analyze(rule.anomalyType, context);
        if (decision.getRecommendedRecipe() == null) {
            aiDecisionEngine.registerLearnedRecipe(rule.anomalyType, rule.match);
            decision = aiDecisionEngine.analyze(rule.anomalyType, context);
        }

        String executionAction = decision.getRecommendedRecipe() != null
                ? "AI selected recipe: " + decision.getRecommendedRecipe().getName()
                : defaultIfBlank(rule.defaultAction, "AI-driven safe fix recommendation");

        createCodeFixFlow(
                cfg,
                rule.anomalyType,
                defaultIfBlank(rule.severity, "HIGH"),
                "Detected from log rule: " + rule.match,
                defaultIfBlank(rule.sourceFile, "UnknownSource.java"),
                rule.lineNumber > 0 ? rule.lineNumber : 1,
                executionAction,
                "PR-" + shortId()
        );
    }

    private void createCodeFixFlow(
            SystemConfig cfg,
            String anomalyType,
            String severity,
            String message,
            String sourceFile,
            int line,
            String executionAction,
            String prId
    ) {
        String systemId = dataModel.getSystemId(cfg.name);
        String resolvedPrId = realPrMode
                ? executeRealCodeFixAndPr(cfg, anomalyType, sourceFile, message, prId)
                : prId;

        // Build dependency candidates first (used for both new and consolidated flows).
        List<Map<String, Object>> deps = new ArrayList<>();
        Map<String, Object> dep1 = new HashMap<>();
        dep1.put("id", extractPrId(resolvedPrId, "PR-" + shortId()));
        dep1.put("name", anomalyType + " primary fix");
        dep1.put("title", "Primary code fix PR");
        dep1.put("status", "OPEN");
        deps.add(dep1);

        // Keep synthetic follow-up dependency only in simulated mode.
        // In real mode, every shown dependency must map to an actual PR in GitHub.
        if (!realPrMode) {
            Map<String, Object> dep2 = new HashMap<>();
            dep2.put("id", "PR-" + shortId());
            dep2.put("name", anomalyType + " compatibility follow-up");
            dep2.put("title", "Related compatibility PR");
            dep2.put("status", "OPEN");
            deps.add(dep2);
        }

        // Consolidation rule: for the same system, keep a single pending deployment flow
        // and merge all waiting PR dependencies into that one deployment step.
        SelfHealingDashboard.HealingFlow consolidated = findConsolidationCandidate(systemId);
        if (consolidated != null) {
            mergeIntoConsolidatedDeployment(consolidated, anomalyType, message, sourceFile, line, resolvedPrId, deps);
            dataModel.recordAnomalyDetected(systemId, anomalyType, severity);
            addAlarm(cfg, "RECOVERY_STARTED", "HIGH", "Code-fix consolidated into pending deployment: " + message, consolidated.id);
            return;
        }

        boolean waitingDependencies = waitingDependencyCount(deps) > 0;

        // Deployment should run only when dependencies are already unblocked.
        String deployStatus = waitingDependencies ? "waiting-dependencies" : "skipped";
        if (!waitingDependencies && realPrMode && cfg.isDockerDeployment()
                && resolvedPrId != null && resolvedPrId.startsWith("http")) {
            System.out.println("  → Rebuilding Docker image for " + cfg.dockerServiceName + " with fix applied...");
            boolean redeployed = DockerManager.rebuildAndRedeploy(cfg.dockerComposeFile, cfg.dockerServiceName);
            deployStatus = redeployed ? "redeployed" : "rebuild-failed";
        }

        SelfHealingDashboard.HealingFlow flow = new SelfHealingDashboard.HealingFlow(systemId, "CODE_FIX");
        flow.anomaly = new SelfHealingDashboard.AnomalyDetails();
        flow.anomaly.anomalyType = anomalyType;
        flow.anomaly.severity = severity;
        flow.anomaly.message = message;
        flow.anomaly.sourceFile = sourceFile;
        flow.anomaly.lineNumber = line;
        flow.anomaly.detectedAt = Instant.now().toEpochMilli();

        addStep(flow, "SIGNAL",     "Anomaly detected from live system",           "TelemetryMCP",     "anomalyType", anomalyType);
        addStep(flow, "CONTEXT",    "Mapped anomaly to source + config",            "CodeAnalysisMCP",  "sourceFile",  sourceFile);
        addStep(flow, "HYPOTHESIS", "Likely logical/code defect",                   "DynamicAI",        "model",       "RuleBasedModel");
        addStep(flow, "EXECUTION",  executionAction,                                "DevelopmentMCP",   "prId",        resolvedPrId);
        String deployAction = waitingDependencies
                ? "Queued for deploy until PR dependencies are merged"
                : "Rebuild Docker image and redeploy container";
        addStep(flow, "DEPLOY",     deployAction,                                      "DockerMCP",        "status",      deployStatus,
                waitingDependencies ? "PENDING" : "COMPLETED");

        addStep(flow, "VALIDATION", waitingDependencies
                        ? "Validation pending dependency merge"
                        : "Post-fix smoke checks pass",
                "TelemetryMCP", "status", waitingDependencies ? "blocked_by_dependencies" : "success",
                waitingDependencies ? "PENDING" : "COMPLETED");

        flow.deploymentDependencies = deps;
        flow.workflowStatus = waitingDependencies ? "WAITING_DEPENDENCIES" : "COMPLETED";
        flow.journey = buildJourney(
                flow.workflowStatus,
                flow.createdAt,
                flow.createdAt + 300,
                "COMPLETED".equals(flow.workflowStatus) ? flow.createdAt + 450 : null,
                "Anomaly detected by TelemetryMCP",
                "Code fix generated by DevelopmentMCP",
                "Deployment waiting for merge dependencies",
                deps
        );

        flow.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(flow);
        dataModel.recordAnomalyDetected(systemId, anomalyType, severity);
        if ("WAITING_DEPENDENCIES".equals(flow.workflowStatus)) {
            dataModel.recordHealingFailure(systemId);
        } else {
            dataModel.recordHealingSuccess(systemId);
        }

        addAlarm(cfg, "RECOVERY_STARTED", "HIGH", "Code-fix flow started: " + message, flow.id);
    }

    private SelfHealingDashboard.HealingFlow findConsolidationCandidate(String systemId) {
        synchronized (dataModel.allFlows) {
            for (int i = dataModel.allFlows.size() - 1; i >= 0; i--) {
                SelfHealingDashboard.HealingFlow flow = dataModel.allFlows.get(i);
                if (flow != null
                        && "CODE_FIX".equals(flow.type)
                        && systemId.equals(flow.systemId)
                        && "WAITING_DEPENDENCIES".equals(flow.workflowStatus)) {
                    return flow;
                }
            }
        }
        return null;
    }

    private void mergeIntoConsolidatedDeployment(
            SelfHealingDashboard.HealingFlow target,
            String anomalyType,
            String message,
            String sourceFile,
            int line,
            String resolvedPrId,
            List<Map<String, Object>> newDeps
    ) {
        // Keep anomaly lineage in step details so we can inspect all merged anomalies later.
        SelfHealingDashboard.FlowStep consolidationStep = new SelfHealingDashboard.FlowStep(
                "EXECUTION",
                "Consolidated code-fix into pending deployment",
                "DevelopmentMCP"
        );
        consolidationStep.status = "COMPLETED";
        consolidationStep.details.put("anomalyType", anomalyType);
        consolidationStep.details.put("message", message);
        consolidationStep.details.put("sourceFile", sourceFile);
        consolidationStep.details.put("line", line);
        consolidationStep.details.put("prId", resolvedPrId);
        target.steps.add(consolidationStep);

        if (target.deploymentDependencies == null) {
            target.deploymentDependencies = new ArrayList<>();
        }
        mergeDependencies(target.deploymentDependencies, newDeps);

        int waiting = waitingDependencyCount(target.deploymentDependencies);
        target.workflowStatus = waiting > 0 ? "WAITING_DEPENDENCIES" : "COMPLETED";
        target.journey = buildJourney(
                target.workflowStatus,
                target.createdAt,
                target.createdAt + 300,
                "COMPLETED".equals(target.workflowStatus) ? System.currentTimeMillis() : null,
                "Multiple anomalies detected for same system",
                "Fixes consolidated into one deployment queue",
                "Deployment waiting for consolidated PR dependencies",
                target.deploymentDependencies
        );
    }

    private void mergeDependencies(List<Map<String, Object>> existing, List<Map<String, Object>> incoming) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> dep : existing) {
            String id = String.valueOf(dep.getOrDefault("id", ""));
            if (!id.isBlank()) byId.put(id, dep);
        }
        for (Map<String, Object> dep : incoming) {
            String id = String.valueOf(dep.getOrDefault("id", ""));
            if (id.isBlank()) continue;
            if (!byId.containsKey(id)) {
                byId.put(id, new HashMap<>(dep));
            } else {
                // Preserve latest status/title if the same PR appears again.
                byId.get(id).putAll(dep);
            }
        }
        existing.clear();
        existing.addAll(byId.values());
    }

    private void refreshWaitingDependencyStates(SystemConfig cfg) {
        String systemId = dataModel.getSystemId(cfg.name);
        synchronized (dataModel.allFlows) {
            for (SelfHealingDashboard.HealingFlow flow : dataModel.allFlows) {
                if (flow == null || !systemId.equals(flow.systemId)) continue;
                if (flow.deploymentDependencies == null || flow.deploymentDependencies.isEmpty()) continue;

                boolean changed = false;
                changed |= cleanupLegacySyntheticDependencies(flow);
                for (Map<String, Object> dep : flow.deploymentDependencies) {
                    changed |= refreshDependencyStatus(cfg, dep);
                }

                if (changed) {
                    int waiting = waitingDependencyCount(flow.deploymentDependencies);
                    String previousWorkflowStatus = flow.workflowStatus;
                    flow.workflowStatus = waiting > 0 ? "WAITING_DEPENDENCIES" : "COMPLETED";

                    if ("WAITING_DEPENDENCIES".equals(previousWorkflowStatus) && waiting == 0) {
                        finalizeDeploymentAfterDependencies(cfg, flow);
                    }

                    flow.journey = buildJourney(
                            flow.workflowStatus,
                            flow.createdAt,
                            flow.createdAt + 300,
                            waiting == 0 ? System.currentTimeMillis() : null,
                            "Anomaly detected by TelemetryMCP",
                            "Fixes prepared and tracked",
                            waiting == 0 ? "All dependent PRs merged; deployment unblocked"
                                    : "Waiting on dependent PRs",
                            flow.deploymentDependencies
                    );
                }
            }
        }
    }

    private boolean cleanupLegacySyntheticDependencies(SelfHealingDashboard.HealingFlow flow) {
        if (!realPrMode || flow == null || flow.deploymentDependencies == null || flow.deploymentDependencies.isEmpty()) {
            return false;
        }

        int before = flow.deploymentDependencies.size();
        flow.deploymentDependencies.removeIf(dep -> {
            String name = String.valueOf(dep.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
            String title = String.valueOf(dep.getOrDefault("title", "")).toLowerCase(Locale.ROOT);
            return name.contains("compatibility follow-up") || title.contains("related compatibility pr");
        });
        return flow.deploymentDependencies.size() != before;
    }

    private boolean refreshDependencyStatus(SystemConfig cfg, Map<String, Object> dep) {
        String depId = String.valueOf(dep.getOrDefault("id", ""));
        Integer prNum = parsePrNumber(depId);
        if (prNum == null) return false;

        String prev = String.valueOf(dep.getOrDefault("status", "OPEN"));

        // In real PR mode, GitHub is the source of truth for approval/merge visibility.
        if (realPrMode && shouldTrigger(cfg, "pr-sync-" + prNum, 30)) {
            Map<String, String> gh = fetchGitHubPrStatus(cfg, prNum);
            if (!gh.isEmpty()) {
                String mapped = gh.getOrDefault("status", prev);
                dep.put("status", mapped);
                if (gh.containsKey("title") && !gh.get("title").isBlank()) dep.put("title", gh.get("title"));
                if (gh.containsKey("name") && !gh.get("name").isBlank()) dep.put("name", gh.get("name"));
                return !prev.equals(mapped);
            }
        }

        // 1) Internal gate state (fast path)
        if (deploymentGate.findByPrNumber(prNum).isPresent()) {
            PRDeploymentGate.PRRecord rec = deploymentGate.findByPrNumber(prNum).get();
            dep.put("status", toDependencyStatus(rec.getState()));
            dep.put("name", "PR #" + prNum + " " + rec.getBranch() + " -> " + rec.getTargetBranch());
            dep.put("title", "PR #" + prNum + " " + rec.getBranch());
            return !prev.equals(dep.get("status"));
        }

        return false;
    }

    private Integer parsePrNumber(String depId) {
        if (depId == null || depId.isBlank()) return null;
        String s = depId.startsWith("PR-") ? depId.substring(3) : depId;
        if (!s.matches("\\d+")) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toDependencyStatus(PRLifecycleState state) {
        if (state == null) return "OPEN";
        return switch (state) {
            case MERGED, DEPLOYED -> "MERGED";
            case APPROVED, MERGING -> "APPROVED";
            case CONFLICT_DETECTED, MERGE_ABORTED, CHANGES_REQUESTED -> "BLOCKED";
            case CLOSED -> "CLOSED";
            default -> "OPEN";
        };
    }

    private Map<String, String> fetchGitHubPrStatus(SystemConfig cfg, int prNumber) {
        try {
            String[] ownerRepo = parseOwnerRepo(cfg.gitRepository);
            if (ownerRepo == null) return Map.of();
            String repo = ownerRepo[0] + "/" + ownerRepo[1];

            ProcessBuilder pb = new ProcessBuilder(
                    "gh", "pr", "view", String.valueOf(prNumber),
                    "--repo", repo,
                    "--json", "state,reviewDecision,title,isDraft"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            if (code != 0 || out.isBlank()) return Map.of();

            String state = extractJsonValue(out, "state");
            String reviewDecision = extractJsonValue(out, "reviewDecision");
            String isDraft = extractJsonValue(out, "isDraft");
            String title = extractJsonValue(out, "title");

            String status = "OPEN";
            if ("MERGED".equalsIgnoreCase(state)) {
                status = "MERGED";
            } else if ("CLOSED".equalsIgnoreCase(state)) {
                status = "CLOSED";
            } else if ("OPEN".equalsIgnoreCase(state)) {
                boolean approved = "APPROVED".equalsIgnoreCase(reviewDecision);
                boolean draft = "true".equalsIgnoreCase(isDraft);
                status = (!draft && approved) ? "APPROVED" : "OPEN";
            }

            Map<String, String> result = new HashMap<>();
            result.put("status", status);
            if (title != null) {
                result.put("title", title);
                result.put("name", "PR #" + prNumber + " " + title);
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        Pattern raw = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false|null|-?\\d+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE);
        Matcher rawMatcher = raw.matcher(json);
        return rawMatcher.find() ? rawMatcher.group(1) : null;
    }

    private void createOperationalFlow(
            SystemConfig cfg,
            String anomalyType,
            String severity,
            String message,
            String executionAction,
            String action
    ) {
        String systemId = dataModel.getSystemId(cfg.name);

        // Execute the restart right now, before recording the flow
        String restartStatus = "skipped";
        if (realPrMode) {
            if (cfg.isDockerDeployment()) {
                System.out.println("  → Docker restart: " + cfg.dockerContainerName);
                restartStatus = DockerManager.restart(cfg.dockerContainerName) ? "restarted" : "restart-failed";
            } else if (cfg.restartCommand != null && !cfg.restartCommand.isBlank()) {
                try {
                    int rc = new ProcessBuilder("sh", "-c", cfg.restartCommand).start().waitFor();
                    restartStatus = rc == 0 ? "restarted" : "restart-failed";
                } catch (Exception e) {
                    restartStatus = "restart-error";
                }
            }
        }

        SelfHealingDashboard.HealingFlow flow = new SelfHealingDashboard.HealingFlow(systemId, "OPERATIONAL_FIX");
        flow.anomaly = new SelfHealingDashboard.AnomalyDetails();
        flow.anomaly.anomalyType = anomalyType;
        flow.anomaly.severity = severity;
        flow.anomaly.message = message;
        flow.anomaly.detectedAt = Instant.now().toEpochMilli();

        String restartTarget = cfg.isDockerDeployment() ? cfg.dockerContainerName : cfg.restartCommand;
        addStep(flow, "SIGNAL",     "Resource anomaly detected",            "TelemetryMCP",    "anomalyType",     anomalyType);
        addStep(flow, "CONTEXT",    "Restart target identified",            "CodeAnalysisMCP", "restartTarget",   restartTarget);
        addStep(flow, "HYPOTHESIS", "Operational mitigation selected",      "DynamicAI",       "hypothesis",      "OPERATIONAL");
        addStep(flow, "EXECUTION",  executionAction,                        "HealingMCP",      "action",          action);
        addStep(flow, "DEPLOY",     "Container/process restarted",          "DockerMCP",       "restartStatus",   restartStatus);
        addStep(flow, "VALIDATION", "Health recovered after restart",       "TelemetryMCP",    "status",          "success");

        flow.deploymentDependencies = new ArrayList<>();
        flow.workflowStatus = "COMPLETED";
        flow.journey = buildJourney(
                flow.workflowStatus,
                flow.createdAt,
                flow.createdAt + 300,
                flow.createdAt + 450,
                "Operational anomaly detected",
                "Operational mitigation executed",
                "Restart deployment completed",
                flow.deploymentDependencies
        );

        flow.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(flow);
        dataModel.recordAnomalyDetected(systemId, anomalyType, severity);
        dataModel.recordHealingSuccess(systemId);

        addAlarm(cfg, "RECOVERY_STARTED", "HIGH", "Operational flow started: " + message, flow.id);
    }

    private void addStep(SelfHealingDashboard.HealingFlow flow, String phase, String action, String mcp, String key, Object value) {
        addStep(flow, phase, action, mcp, key, value, "COMPLETED");
    }

    private void addStep(SelfHealingDashboard.HealingFlow flow,
                         String phase,
                         String action,
                         String mcp,
                         String key,
                         Object value,
                         String stepStatus) {
        SelfHealingDashboard.FlowStep step = new SelfHealingDashboard.FlowStep(phase, action, mcp);
        step.status = stepStatus;
        step.details.put(key, value);
        flow.steps.add(step);
    }

    private void finalizeDeploymentAfterDependencies(SystemConfig cfg, SelfHealingDashboard.HealingFlow flow) {
        SelfHealingDashboard.FlowStep deployStep = findStep(flow, "DEPLOY");
        SelfHealingDashboard.FlowStep validationStep = findStep(flow, "VALIDATION");
        String prId = extractExecutionPrId(flow);

        String deployStatus = "skipped";
        if (realPrMode && cfg.isDockerDeployment() && prId != null && prId.startsWith("http")) {
            System.out.println("  → Rebuilding Docker image for " + cfg.dockerServiceName + " after dependencies merged...");
            boolean redeployed = DockerManager.rebuildAndRedeploy(cfg.dockerComposeFile, cfg.dockerServiceName);
            deployStatus = redeployed ? "redeployed" : "rebuild-failed";
        }

        if (deployStep != null) {
            deployStep.status = "COMPLETED";
            deployStep.action = "Rebuild Docker image and redeploy container";
            deployStep.details.put("status", deployStatus);
        }
        if (validationStep != null) {
            validationStep.status = "COMPLETED";
            validationStep.action = "Post-fix smoke checks pass";
            validationStep.details.put("status", "success");
        }
    }

    private SelfHealingDashboard.FlowStep findStep(SelfHealingDashboard.HealingFlow flow, String phase) {
        if (flow == null || flow.steps == null) return null;
        for (SelfHealingDashboard.FlowStep step : flow.steps) {
            if (step != null && phase.equals(step.phase)) {
                return step;
            }
        }
        return null;
    }

    private String extractExecutionPrId(SelfHealingDashboard.HealingFlow flow) {
        SelfHealingDashboard.FlowStep execution = findStep(flow, "EXECUTION");
        if (execution == null || execution.details == null) return null;
        Object pr = execution.details.get("prId");
        return pr == null ? null : String.valueOf(pr);
    }

    private void addAlarm(SystemConfig cfg, String type, String severity, String message, String flowId) {
        String systemId = dataModel.getSystemId(cfg.name);
        SelfHealingDashboard.Alarm alarm = new SelfHealingDashboard.Alarm(systemId, type, severity, message);
        alarm.relatedFlowId = flowId;
        dataModel.addAlarm(alarm);
    }

    private int waitingDependencyCount(List<Map<String, Object>> deps) {
        int count = 0;
        for (Map<String, Object> dep : deps) {
            String status = String.valueOf(dep.getOrDefault("status", "OPEN"));
            if (!("MERGED".equals(status) || "DEPLOYED".equals(status) || "COMPLETED".equals(status))) {
                count++;
            }
        }
        return count;
    }

    private String extractPrId(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        if (raw.startsWith("PR-")) return raw;
        int idx = raw.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < raw.length()) {
            String suffix = raw.substring(idx + 1);
            if (suffix.matches("\\d+")) {
                return "PR-" + suffix;
            }
        }
        return fallback;
    }

    private String defaultIfBlank(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }

    private Map<String, Object> buildJourney(String flowStatus,
                                             Long anomalyAt,
                                             Long fixAt,
                                             Long deploymentAt,
                                             String anomalySummary,
                                             String fixSummary,
                                             String deploymentSummary,
                                             List<Map<String, Object>> dependencies) {
        List<Map<String, Object>> deps = dependencies == null ? List.of() : dependencies;
        int waiting = waitingDependencyCount(deps);

        Map<String, Object> anomaly = new LinkedHashMap<>();
        anomaly.put("key", "ANOMALY");
        anomaly.put("label", "Anomaly Detection");
        anomaly.put("status", anomalyAt != null ? "COMPLETED" : "PENDING");
        anomaly.put("timestamp", anomalyAt);
        anomaly.put("summary", anomalySummary);

        Map<String, Object> fix = new LinkedHashMap<>();
        fix.put("key", "FIX");
        fix.put("label", "Fix Applied");
        fix.put("status", fixAt != null ? "COMPLETED" : "PENDING");
        fix.put("timestamp", fixAt);
        fix.put("summary", fixSummary);

        Map<String, Object> deployment = new LinkedHashMap<>();
        deployment.put("key", "DEPLOYMENT");
        deployment.put("label", "Deployment");
        deployment.put("status", deploymentAt != null ? "COMPLETED"
                : (waiting > 0 ? "WAITING_DEPENDENCIES" : ("FAILED".equals(flowStatus) ? "FAILED" : "PENDING")));
        deployment.put("timestamp", deploymentAt);
        deployment.put("summary", deploymentSummary);
        deployment.put("waitingCount", waiting);
        deployment.put("dependencies", deps);

        Map<String, Object> journey = new LinkedHashMap<>();
        journey.put("flowStatus", flowStatus);
        journey.put("stages", List.of(anomaly, fix, deployment));
        return journey;
    }

    private boolean shouldTrigger(SystemConfig cfg, String key, long cooldownSeconds) {
        long now = Instant.now().getEpochSecond();
        Long last = cfg.lastTriggerByKey.get(key);
        if (last == null || now - last >= cooldownSeconds) {
            cfg.lastTriggerByKey.put(key, now);
            return true;
        }
        return false;
    }

    private long extractLong(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String executeRealCodeFixAndPr(SystemConfig cfg, String anomalyType, String sourceFile, String title, String fallbackPrId) {
        try {
            if (cfg.gitRepository == null || cfg.gitRepository.isBlank()) {
                return fallbackPrId + " (simulated:no-git-repo)";
            }

            Path repoPath = resolveLocalRepoPath(cfg);
            if (repoPath == null) {
                return fallbackPrId + " (simulated:no-local-repo)";
            }

            Path targetFile = findFileByName(repoPath, sourceFile);
            if (targetFile == null) {
                return fallbackPrId + " (simulated:file-not-found)";
            }

            // ── Git operations FIRST so the patch lands on a clean fix branch ──
            String branch = "uac/fix-" + shortId();
            List<String> baseCandidates = resolveBaseBranchCandidates(cfg, repoPath);
            String baseBranch = checkoutFirstAvailableBase(repoPath, baseCandidates);
            if (baseBranch == null) {
                return fallbackPrId + " (simulated:checkout-base-failed:" + String.join("|", baseCandidates) + ")";
            }
            if (!git(repoPath, "checkout", "-b", branch)) {
                return fallbackPrId + " (simulated:create-branch-failed)";
            }

            // ── Now apply the patch on the fix branch ──────────────────────────
            boolean patched = applyPatchForAnomaly(targetFile, anomalyType);
            if (!patched) {
                // Unknown or unmatched anomaly: persist a learned recipe artifact
                // so the branch still carries a meaningful change for review.
                patched = createFallbackRecipeArtifact(repoPath, anomalyType, sourceFile, title);
            }
            if (!patched) {
                return fallbackPrId + " (simulated:no-patch-applied)";
            }

            if (!git(repoPath, "add", ".")) {
                return fallbackPrId + " (simulated:git-add-failed)";
            }
            if (!git(repoPath, "commit", "-m", "UAC fix: " + anomalyType)) {
                return fallbackPrId + " (simulated:git-commit-failed)";
            }
            if (!git(repoPath, "push", "-u", "origin", branch)) {
                return fallbackPrId + " (simulated:git-push-failed)";
            }

            String prTitle   = "UAC Fix: " + anomalyType;
            String prBody    = "Automated fix by UAC monitor for system: " + cfg.name;

            // ── 1. Try gh CLI first (uses gh auth / SSH – no token needed) ──
            String cliUrl = createPullRequestViaCLI(repoPath, prTitle, prBody, branch, baseBranch);
            if (cliUrl != null) {
                return cliUrl;
            }

            // ── 2. Fall back to REST API when a token is available ───────────
            String[] ownerRepo = parseOwnerRepo(cfg.gitRepository);
            if (ownerRepo != null) {
                String tokenEnv = (cfg.tokenEnv != null && !cfg.tokenEnv.isBlank()) ? cfg.tokenEnv : "GITHUB_TOKEN";
                String token = System.getenv(tokenEnv);
                if (token != null && !token.isBlank()) {
                    GitHubAPI gh = new GitHubAPI(token, ownerRepo[0], ownerRepo[1], repoPath.toFile());
                    GitHubAPI.PullRequest pr = gh.createPullRequest(prTitle, prBody, branch, baseBranch);
                    if (pr != null) {
                        return pr.getUrl();
                    }
                }
            }

            return "branch:" + branch;
        } catch (Exception ex) {
            return fallbackPrId + " (simulated:real-pr-error:" + ex.getClass().getSimpleName() + ")";
        }
    }

    private List<String> resolveBaseBranchCandidates(SystemConfig cfg, Path repoPath) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        // 1) Configured branch first (if present)
        if (cfg.gitBranch != null && !cfg.gitBranch.isBlank()) {
            candidates.add(cfg.gitBranch.trim());
        }

        // 2) origin/HEAD symbolic ref -> e.g. refs/remotes/origin/develop
        String originHead = gitOutput(repoPath, "symbolic-ref", "--quiet", "refs/remotes/origin/HEAD");
        if (originHead != null && originHead.contains("/")) {
            String b = originHead.substring(originHead.lastIndexOf('/') + 1).trim();
            if (!b.isBlank()) candidates.add(b);
        }

        // 3) Current checked-out branch
        String current = gitOutput(repoPath, "rev-parse", "--abbrev-ref", "HEAD");
        if (current != null) {
            current = current.trim();
            if (!current.isBlank() && !"HEAD".equals(current)) {
                candidates.add(current);
            }
        }

        // 4) Common defaults + common team branch
        candidates.add("develop");
        candidates.add("main");
        candidates.add("master");

        return new ArrayList<>(candidates);
    }

    private String checkoutFirstAvailableBase(Path repoPath, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;

            // 1) Prefer an existing local branch.
            if (git(repoPath, "checkout", candidate)) {
                System.out.println("  ✓ Using base branch: " + candidate);
                return candidate;
            }

            // 2) If local branch doesn't exist, try to materialize it from origin/<candidate>.
            // This handles repos whose default branch exists only as remote-tracking locally.
            if (git(repoPath, "fetch", "origin", candidate)
                    && git(repoPath, "checkout", "-B", candidate, "origin/" + candidate)) {
                System.out.println("  ✓ Using base branch from remote: " + candidate + " (origin/" + candidate + ")");
                return candidate;
            }
        }
        return null;
    }

    private Path resolveLocalRepoPath(SystemConfig cfg) {
        // 1. Derive from log path: /tmp/sample-apps/{repo}/logs/file.log  →  parent-of-parent
        try {
            Path fromLog = Paths.get(cfg.logPath).getParent();
            if (fromLog != null) {
                Path maybeRepo = fromLog.getParent();
                if (maybeRepo != null && Files.isDirectory(maybeRepo)) {
                    ensureGitRepo(maybeRepo, cfg.gitRepository);
                    if (Files.exists(maybeRepo.resolve(".git"))) return maybeRepo;
                }
            }
        } catch (Exception ignored) { }

        // 2. Fallback: /tmp/sample-apps/<repo-name>
        String[] ownerRepo = parseOwnerRepo(cfg.gitRepository);
        if (ownerRepo != null) {
            Path candidate = Paths.get("/tmp/sample-apps", ownerRepo[1]);
            if (Files.isDirectory(candidate)) {
                ensureGitRepo(candidate, cfg.gitRepository);
                if (Files.exists(candidate.resolve(".git"))) return candidate;
            }

            // 3. Development workspace fallback: /Users/<user>/development/<repo>
            Path devCandidate = Paths.get(System.getProperty("user.home"), "development", ownerRepo[1]);
            if (Files.isDirectory(devCandidate) && Files.exists(devCandidate.resolve(".git"))) {
                return devCandidate;
            }
        }

        // 4. Current working directory fallback (useful when monitoring UAC itself).
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve(".git"))) {
            return cwd;
        }

        return null;
    }

    /**
     * If {@code dir} has source files but no {@code .git}, run "git init" + initial
     * commit so UAC can create fix branches on top of it.
     */
    private void ensureGitRepo(Path dir, String remoteUrl) {
        if (Files.exists(dir.resolve(".git"))) return; // already initialised
        try {
            System.out.println("  → Auto-initialising git repo at " + dir);

            // git init (try -b main for Git ≥ 2.28, fall back to plain init + rename)
            int rc = new ProcessBuilder("git", "init", "-b", "main")
                    .directory(dir.toFile()).redirectErrorStream(true).start().waitFor();
            if (rc != 0) {
                new ProcessBuilder("git", "init")
                        .directory(dir.toFile()).redirectErrorStream(true).start().waitFor();
                new ProcessBuilder("git", "checkout", "-b", "main")
                        .directory(dir.toFile()).redirectErrorStream(true).start().waitFor();
            }

            // configure local identity so commits don't fail
            new ProcessBuilder("git", "config", "user.email", "uac@local")
                    .directory(dir.toFile()).start().waitFor();
            new ProcessBuilder("git", "config", "user.name", "UAC")
                    .directory(dir.toFile()).start().waitFor();

            // stage everything and create the initial commit
            new ProcessBuilder("git", "add", "-A")
                    .directory(dir.toFile()).redirectErrorStream(true).start().waitFor();
            new ProcessBuilder("git", "commit", "-m", "chore: initial commit by UAC")
                    .directory(dir.toFile()).redirectErrorStream(true).start().waitFor();

            // wire up the remote if we have a URL
            if (remoteUrl != null && !remoteUrl.isBlank()) {
                new ProcessBuilder("git", "remote", "add", "origin", remoteUrl)
                        .directory(dir.toFile()).redirectErrorStream(true).start().waitFor();
            }

            System.out.println("  ✓ Git repo initialised at " + dir);
        } catch (Exception e) {
            System.out.println("  ⚠ Could not auto-init git repo at " + dir + ": " + e.getMessage());
        }
    }

    private Path findFileByName(Path repoPath, String fileName) throws IOException {
        try (Stream<Path> stream = Files.walk(repoPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private boolean applyPatchForAnomaly(Path targetFile, String anomalyType) throws IOException {
        String content = Files.readString(targetFile, StandardCharsets.UTF_8);
        String updated = content;

        if ("CACHE_EVICTION_MISSING".equals(anomalyType)) {
            // Preferred fix for the new runtime-toggle implementation
            updated = updated.replace(
                    "private volatile boolean evictionEnabled = false;",
                    "private volatile boolean evictionEnabled = true;"
            );

            // Backward-compatible fallback for the older implementation
            updated = updated.replace(
                    "private Map<String, CacheEntry> cache = new HashMap<>();",
                    "private Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(1024, 0.75f, true) {\n" +
                            "        @Override\n" +
                            "        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {\n" +
                            "            return size() > 2000;\n" +
                            "        }\n" +
                            "    };"
            );
        } else if ("NULL_POINTER_EXCEPTION".equals(anomalyType)) {
            // Preferred fix for the new runtime-toggle implementation
            updated = updated.replace(
                    "private volatile boolean nullCustomerFailureEnabled = true;",
                    "private volatile boolean nullCustomerFailureEnabled = false;"
            );

            // Backward-compatible fallback for the older implementation
            updated = updated.replace(
                    "if (request.getCustomer() == null) {\n" +
                            "                log.error(\"Customer object is null\");\n" +
                            "                throw new NullPointerException(\"Customer cannot be null\");\n" +
                            "            }",
                    "if (request.getCustomer() == null) {\n" +
                            "                log.warn(\"[UAC Fix] Customer is null – defaulting to prevent NullPointerException\");\n" +
                            "                request.setCustomer(\"unknown_customer\");\n" +
                            "            }"
            );
        } else if ("UAC_LOG_SPAM".equals(anomalyType)) {
            updated = updated.replace(
                    "System.out.println(\"API: /alarms requested. Selected system: \" + dashboard.selectedSystem + \", resolvedId=\" + (sys != null ? sys.id : \"null\") + \", Total alarms: \" + dashboard.alarms.size());",
                    "if (Boolean.getBoolean(\"uac.verbose.dashboard\")) {\n" +
                            "                    System.out.println(\"API: /alarms requested. Selected system: \" + dashboard.selectedSystem + \", resolvedId=\" + (sys != null ? sys.id : \"null\") + \", Total alarms: \" + dashboard.alarms.size());\n" +
                            "                }"
            );
            updated = updated.replace(
                    "System.out.println(\"  Checking alarm: sysId=\" + alarm.systemId + \", type=\" + alarm.alarmType);",
                    "if (Boolean.getBoolean(\"uac.verbose.dashboard\")) {\n" +
                            "                        System.out.println(\"  Checking alarm: sysId=\" + alarm.systemId + \", type=\" + alarm.alarmType);\n" +
                            "                    }"
            );
        }

        if (updated.equals(content)) {
            return false;
        }

        Files.writeString(targetFile, updated, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        return true;
    }

    private boolean createFallbackRecipeArtifact(Path repoPath,
                                                 String anomalyType,
                                                 String sourceFile,
                                                 String title) {
        try {
            Path dir = repoPath.resolve(".uac").resolve("learned-recipes");
            Files.createDirectories(dir);

            String safeName = anomalyType == null || anomalyType.isBlank()
                    ? "UNKNOWN_ANOMALY"
                    : anomalyType.replaceAll("[^A-Za-z0-9._-]", "_");

            Path file = dir.resolve(safeName + ".md");
            String now = Instant.now().toString();

            String content = "# UAC Learned Recipe: " + safeName + "\n\n"
                    + "- Generated: " + now + "\n"
                    + "- Source file: " + (sourceFile == null ? "UnknownSource.java" : sourceFile) + "\n"
                    + "- Context: " + (title == null ? "n/a" : title) + "\n\n"
                    + "## Suggested Safe Actions\n"
                    + "1. Add defensive guards around risky operations.\n"
                    + "2. Add structured error logging and correlation IDs.\n"
                    + "3. Add regression tests for this anomaly signature.\n";

            String existing = Files.exists(file)
                    ? Files.readString(file, StandardCharsets.UTF_8)
                    : "";

            if (existing.equals(content)) {
                return false;
            }

            Files.writeString(
                    file,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create a GitHub pull request via the GitHub CLI (gh pr create).
     * This uses the existing "gh auth login" / SSH credential chain so no
     * GITHUB_TOKEN environment variable is required.
     *
     * @return the PR URL on success, or {@code null} if gh is unavailable / failed.
     */
    private String createPullRequestViaCLI(Path repoPath, String title, String body,
                                            String headBranch, String baseBranch) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "gh", "pr", "create",
                "--title", title,
                "--body",  body,
                "--head",  headBranch,
                "--base",  baseBranch
            );
            pb.directory(repoPath.toFile());
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            String stdout = new String(proc.getInputStream().readAllBytes()).trim();
            proc.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream()); // drain stderr
            int exitCode = proc.waitFor();

            if (exitCode == 0 && stdout.contains("github.com")) {
                System.out.println("  ✓ PR created via gh CLI: " + stdout);
                return stdout;
            }
            return null;
        } catch (Exception e) {
            // gh not installed or not authenticated – caller will fall back
            return null;
        }
    }

    private boolean git(Path repoPath, String... args) {
        try {
            List<String> cmd = new java.util.ArrayList<>();
            cmd.add("git");
            cmd.add("-C");
            cmd.add(repoPath.toString());
            for (String arg : args) {
                cmd.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String gitOutput(Path repoPath, String... args) {
        try {
            List<String> cmd = new java.util.ArrayList<>();
            cmd.add("git");
            cmd.add("-C");
            cmd.add(repoPath.toString());
            for (String arg : args) {
                cmd.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int code = p.waitFor();
            return code == 0 ? output : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String[] parseOwnerRepo(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }

        String normalized = repoUrl.trim();
        // https://github.com/owner/repo.git
        int idx = normalized.indexOf("github.com/");
        if (idx >= 0) {
            String rest = normalized.substring(idx + "github.com/".length());
            if (rest.endsWith(".git")) {
                rest = rest.substring(0, rest.length() - 4);
            }
            String[] parts = rest.split("/");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }

        // git@github.com:owner/repo.git
        idx = normalized.indexOf(":");
        if (normalized.startsWith("git@github.com:") && idx >= 0) {
            String rest = normalized.substring(idx + 1);
            if (rest.endsWith(".git")) {
                rest = rest.substring(0, rest.length() - 4);
            }
            String[] parts = rest.split("/");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }

        return null;
    }

    private SystemConfig parseYaml(Path file) throws IOException {
        String currentSection = "";
        SystemConfig cfg = new SystemConfig();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.endsWith(":")) {
                currentSection = line.substring(0, line.length() - 1).trim();
                continue;
            }

            String[] parts = line.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String key = parts[0].trim();
            String value = parts[1].trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            if ("system".equals(currentSection) && "name".equals(key)) {
                cfg.name = value;
            } else if ("logs".equals(currentSection) && "location".equals(key)) {
                cfg.logPath = value;
            } else if ("health_endpoint".equals(currentSection) && "url".equals(key)) {
                cfg.healthUrl = value;
            } else if ("deployment".equals(currentSection) && "restart_command".equals(key)) {
                cfg.restartCommand = value;
            } else if ("deployment".equals(currentSection) && "container_name".equals(key)) {
                cfg.dockerContainerName = value;
            } else if ("deployment".equals(currentSection) && "service_name".equals(key)) {
                cfg.dockerServiceName = value;
            } else if ("deployment".equals(currentSection) && "compose_file".equals(key)) {
                cfg.dockerComposeFile = value;
            } else if ("git".equals(currentSection) && "repository".equals(key)) {
                cfg.gitRepository = value;
            } else if ("git".equals(currentSection) && "branch".equals(key)) {
                cfg.gitBranch = value;
            } else if ("git".equals(currentSection) && "token_env".equals(key)) {
                cfg.tokenEnv = value;
            }
        }

        if (cfg.name == null || cfg.logPath == null || cfg.healthUrl == null) {
            return null;
        }

        Path log = Paths.get(cfg.logPath);
        cfg.lastLogOffset = Files.exists(log) ? Files.size(log) : 0;
        return cfg;
    }

    static class SystemConfig {
        String name;
        String healthUrl;
        String logPath;
        String restartCommand;
        String gitRepository;
        String gitBranch;
        String tokenEnv = "GITHUB_TOKEN";
        // Docker deployment fields
        String dockerContainerName;
        String dockerServiceName;
        String dockerComposeFile;
        long lastLogOffset;
        Map<String, Long> lastTriggerByKey = new ConcurrentHashMap<>();

        boolean isDockerDeployment() {
            return dockerContainerName != null && !dockerContainerName.isBlank();
        }
    }
}

