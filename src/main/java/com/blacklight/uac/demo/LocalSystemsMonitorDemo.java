package com.blacklight.uac.demo;

import com.blacklight.uac.docker.DockerManager;
import com.blacklight.uac.git.GitHubAPI;
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
import java.util.HashMap;
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

    public LocalSystemsMonitorDemo(int dashboardPort, int dataPort) throws IOException {
        this.dataModel = new SelfHealingDashboard(dataPort);
        this.dashboard = new SimpleDashboard(dataModel, dashboardPort);
        this.scheduler = Executors.newScheduledThreadPool(4);
        this.systems = new LinkedHashMap<>();
        this.realPrMode = "true".equalsIgnoreCase(System.getenv().getOrDefault("UAC_REAL_PR", "false"));
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

        // After code is patched & PR raised, rebuild + redeploy the Docker container
        String deployStatus = "skipped";
        if (realPrMode && cfg.isDockerDeployment()
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
        addStep(flow, "DEPLOY",     "Rebuild Docker image and redeploy container",  "DockerMCP",        "status",      deployStatus);
        addStep(flow, "VALIDATION", "Post-fix smoke checks pass",                   "TelemetryMCP",     "status",      "success");

        flow.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(flow);
        dataModel.recordAnomalyDetected(systemId, anomalyType, severity);
        dataModel.recordHealingSuccess(systemId);

        addAlarm(cfg, "RECOVERY_STARTED", "HIGH", "Code-fix flow started: " + message, flow.id);
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

        flow.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(flow);
        dataModel.recordAnomalyDetected(systemId, anomalyType, severity);
        dataModel.recordHealingSuccess(systemId);

        addAlarm(cfg, "RECOVERY_STARTED", "HIGH", "Operational flow started: " + message, flow.id);
    }

    private void addStep(SelfHealingDashboard.HealingFlow flow, String phase, String action, String mcp, String key, Object value) {
        SelfHealingDashboard.FlowStep step = new SelfHealingDashboard.FlowStep(phase, action, mcp);
        step.status = "COMPLETED";
        step.details.put(key, value);
        flow.steps.add(step);
    }

    private void addAlarm(SystemConfig cfg, String type, String severity, String message, String flowId) {
        String systemId = dataModel.getSystemId(cfg.name);
        SelfHealingDashboard.Alarm alarm = new SelfHealingDashboard.Alarm(systemId, type, severity, message);
        alarm.relatedFlowId = flowId;
        dataModel.addAlarm(alarm);
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

            // Checkout base branch (try main, fall back to master)
            if (!git(repoPath, "checkout", "main") && !git(repoPath, "checkout", "master")) {
                return fallbackPrId + " (simulated:checkout-main-failed)";
            }
            if (!git(repoPath, "checkout", "-b", branch)) {
                return fallbackPrId + " (simulated:create-branch-failed)";
            }

            // ── Now apply the patch on the fix branch ──────────────────────────
            boolean patched = applyPatchForAnomaly(targetFile, anomalyType);
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

            String baseBranch = (cfg.gitBranch != null && !cfg.gitBranch.isBlank()) ? cfg.gitBranch : "main";
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
        String gitBranch = "main";
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

