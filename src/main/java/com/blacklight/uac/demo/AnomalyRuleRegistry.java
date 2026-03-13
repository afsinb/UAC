package com.blacklight.uac.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * External anomaly rule registry backed by YAML files.
 *
 * Purpose:
 * - keep anomaly detection rules out of hardcoded Java branches
 * - support runtime learning of unknown exception signatures
 */
public class AnomalyRuleRegistry {

    public static class AnomalyRule {
        public String id;
        public String anomalyType;
        public String severity;
        public String match;
        public String sourceFile;
        public int lineNumber;
        public String defaultAction;
        public boolean learned;

        public boolean matches(String line) {
            if (line == null || match == null || match.isBlank()) return false;
            return line.toUpperCase(Locale.ROOT).contains(match.toUpperCase(Locale.ROOT));
        }
    }

    private static final Pattern THROWABLE_CANDIDATE_PATTERN =
            Pattern.compile("([A-Za-z_$][A-Za-z0-9_$.]*)");

    private final Path rulesFile;
    private final Path learnedRulesFile;
    private final List<AnomalyRule> rules;

    public AnomalyRuleRegistry(Path rulesFile, Path learnedRulesFile) {
        this.rulesFile = rulesFile;
        this.learnedRulesFile = learnedRulesFile;
        this.rules = new ArrayList<>();
        reload();
    }

    public synchronized void reload() {
        rules.clear();
        rules.addAll(loadYamlRules(rulesFile, false));
        rules.addAll(loadYamlRules(learnedRulesFile, true));
    }

    public synchronized List<AnomalyRule> allRules() {
        return Collections.unmodifiableList(rules);
    }

    public synchronized AnomalyRule findMatchingRule(String logLine) {
        for (AnomalyRule rule : rules) {
            if (rule.matches(logLine)) return rule;
        }
        return null;
    }

    public String extractExceptionClass(String logLine) {
        if (logLine == null) return null;
        Matcher m = THROWABLE_CANDIDATE_PATTERN.matcher(logLine);
        while (m.find()) {
            String token = sanitizeToken(m.group(1));
            if (token.isBlank()) continue;

            // Fast-path for common throwable naming conventions.
            if (token.endsWith("Exception") || token.endsWith("Error") || token.endsWith("Throwable")) {
                return token;
            }

            // Broader path: any loadable class assignable to Throwable is learnable.
            if (isThrowableType(token)) {
                return token;
            }
        }
        return null;
    }

    private boolean isThrowableType(String className) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = getClass().getClassLoader();
            Class<?> c = Class.forName(className, false, cl);
            return Throwable.class.isAssignableFrom(c);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String sanitizeToken(String token) {
        if (token == null) return "";
        String t = token.trim();
        while (!t.isEmpty() && !Character.isLetterOrDigit(t.charAt(t.length() - 1)) && t.charAt(t.length() - 1) != '$') {
            t = t.substring(0, t.length() - 1);
        }
        while (!t.isEmpty() && !Character.isLetterOrDigit(t.charAt(0)) && t.charAt(0) != '_') {
            t = t.substring(1);
        }
        return t;
    }

    public String normalizeAnomalyType(String exceptionClass) {
        if (exceptionClass == null || exceptionClass.isBlank()) return "UNKNOWN_EXCEPTION";
        String simple = exceptionClass.contains(".")
                ? exceptionClass.substring(exceptionClass.lastIndexOf('.') + 1)
                : exceptionClass;
        return simple.replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_");
    }

    public synchronized AnomalyRule ensureLearnedRule(String exceptionClass) {
        String anomalyType = normalizeAnomalyType(exceptionClass);
        for (AnomalyRule r : rules) {
            if (anomalyType.equals(r.anomalyType)) return r;
        }

        AnomalyRule learned = new AnomalyRule();
        learned.id = "RULE-" + anomalyType;
        learned.anomalyType = anomalyType;
        learned.severity = "HIGH";
        learned.match = exceptionClass;
        learned.sourceFile = "UnknownSource.java";
        learned.lineNumber = 1;
        learned.defaultAction = "AI-driven safe fix recommendation";
        learned.learned = true;
        rules.add(learned);

        persistLearnedRules();
        return learned;
    }

    private List<AnomalyRule> loadYamlRules(Path file, boolean learned) {
        if (file == null || !Files.exists(file)) return List.of();

        List<AnomalyRule> loaded = new ArrayList<>();
        AnomalyRule current = null;

        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || "rules:".equals(line)) continue;

                if (line.startsWith("-")) {
                    if (current != null && current.anomalyType != null) {
                        current.learned = learned;
                        loaded.add(current);
                    }
                    current = new AnomalyRule();
                    String rest = line.substring(1).trim();
                    if (!rest.isEmpty() && rest.contains(":")) {
                        String[] kv = rest.split(":", 2);
                        apply(current, kv[0].trim(), strip(kv[1].trim()));
                    }
                    continue;
                }

                if (current != null && line.contains(":")) {
                    String[] kv = line.split(":", 2);
                    apply(current, kv[0].trim(), strip(kv[1].trim()));
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }

        if (current != null && current.anomalyType != null) {
            current.learned = learned;
            loaded.add(current);
        }
        return loaded;
    }

    private void apply(AnomalyRule r, String key, String val) {
        switch (key) {
            case "id" -> r.id = val;
            case "anomalyType" -> r.anomalyType = val;
            case "severity" -> r.severity = val;
            case "match" -> r.match = val;
            case "sourceFile" -> r.sourceFile = val;
            case "lineNumber" -> {
                try { r.lineNumber = Integer.parseInt(val); }
                catch (NumberFormatException e) { r.lineNumber = 1; }
            }
            case "defaultAction" -> r.defaultAction = val;
            default -> { }
        }
    }

    private String strip(String v) {
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private void persistLearnedRules() {
        try {
            if (learnedRulesFile.getParent() != null) {
                Files.createDirectories(learnedRulesFile.getParent());
            }
            StringBuilder out = new StringBuilder();
            out.append("rules:\n");
            for (AnomalyRule r : rules) {
                if (!r.learned) continue;
                out.append("  - id: \"").append(escape(r.id)).append("\"\n");
                out.append("    anomalyType: \"").append(escape(r.anomalyType)).append("\"\n");
                out.append("    severity: \"").append(escape(defaultIfBlank(r.severity, "HIGH"))).append("\"\n");
                out.append("    match: \"").append(escape(defaultIfBlank(r.match, r.anomalyType))).append("\"\n");
                out.append("    sourceFile: \"").append(escape(defaultIfBlank(r.sourceFile, "UnknownSource.java"))).append("\"\n");
                out.append("    lineNumber: ").append(r.lineNumber > 0 ? r.lineNumber : 1).append("\n");
                out.append("    defaultAction: \"").append(escape(defaultIfBlank(r.defaultAction, "AI-driven safe fix recommendation"))).append("\"\n");
            }
            Files.writeString(
                    learnedRulesFile,
                    out.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }

    private String defaultIfBlank(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

