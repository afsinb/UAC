package com.blacklight.uac.mcp;

import com.blacklight.uac.evolver.PRDeploymentGate;

import java.util.*;

/**
 * DeploymentMCPServer - MCP server for deployment decisions.
 *
 * Provides AI-powered deployment strategy recommendations and enforces the
 * two safety rules introduced in developV2:
 *
 *   Rule 1 – PR Gate: deployment is BLOCKED unless the PR is APPROVED + MERGED.
 *   Rule 2 – Deployment Sync: only ONE deployment runs at a time globally.
 *            Events are submitted to DeploymentSyncManager and processed
 *            serially with consolidation and cooldown.
 *
 * New capabilities (sync):
 *   submit_deployment      – enqueue a deployment event (non-blocking)
 *   get_active_deployment  – inspect the currently running deployment
 *   get_deployment_queue   – inspect pending events
 *   cancel_deployment      – cancel a queued event by eventId
 *   get_deployment_history – last N completed/failed/cancelled events
 */
public class DeploymentMCPServer implements MCPServer {

    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    private final PRDeploymentGate     gate    = PRDeploymentGate.getInstance();
    private final DeploymentSyncManager syncMgr = DeploymentSyncManager.getInstance();

    @Override
    public String getName() {
        return "Deployment";
    }

    @Override
    public List<String> getCapabilities() {
        return Arrays.asList(
            "recommend_strategy",
            "assess_risk",
            "calculate_rollback_time",
            "validate_deployment",
            "schedule_deployment",
            "check_pr_gate",
            // ── Deployment sync capabilities ──
            "submit_deployment",
            "get_active_deployment",
            "get_deployment_queue",
            "cancel_deployment",
            "get_deployment_history"
        );
    }

    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "recommend_strategy"      -> recommendStrategy(request);
            case "assess_risk"             -> assessRisk(request);
            case "calculate_rollback_time" -> calculateRollbackTime(request);
            case "validate_deployment"     -> validateDeployment(request);
            case "schedule_deployment"     -> scheduleDeployment(request);
            case "check_pr_gate"           -> checkPrGate(request);
            // ── Sync handlers ──
            case "submit_deployment"       -> submitDeployment(request);
            case "get_active_deployment"   -> getActiveDeployment(request);
            case "get_deployment_queue"    -> getDeploymentQueue(request);
            case "cancel_deployment"       -> cancelDeployment(request);
            case "get_deployment_history"  -> getDeploymentHistory(request);
            default -> MCPResponse.error(request.getId(),
                    "Unknown method: " + request.getMethod());
        };
    }

    // ── Deployment Sync handlers ──────────────────────────────────────────

    /**
     * submit_deployment – enqueue a deployment event (returns immediately).
     *
     * Required params: prNumber (int), pipelineId (String), environment (String)
     * Optional params: version (String), priority (int, default 5)
     */
    private MCPResponse submitDeployment(MCPRequest request) {
        Integer prNumber  = request.getIntParam("prNumber");
        String pipelineId  = request.getStringParam("pipelineId");
        String environment = request.getStringParam("environment");

        if (prNumber == null || pipelineId == null || environment == null) {
            return MCPResponse.error(request.getId(),
                    "Missing required params: prNumber, pipelineId, environment");
        }

        String version  = Objects.requireNonNullElse(request.getStringParam("version"), "latest");
        int    priority = Objects.requireNonNullElse(request.getIntParam("priority"), 5);

        DeploymentEvent event = new DeploymentEvent(prNumber, pipelineId, environment,
                                                    version, priority);
        DeploymentSyncManager.SubmitResult result = syncMgr.submitEvent(event);

        if (result.isAccepted()) {
            System.out.println(CYAN + "[DeploymentMCPServer] 📬 Deployment event accepted: "
                    + result.getEventId() + RESET);
            return MCPResponse.success(request.getId(), result.toMap());
        } else {
            return MCPResponse.error(request.getId(), result.getMessage());
        }
    }

    /**
     * get_active_deployment – returns the currently running deployment event,
     * or an empty object if no deployment is active.
     */
    private MCPResponse getActiveDeployment(MCPRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        syncMgr.getActiveDeployment().ifPresentOrElse(
            evt -> {
                result.put("active", true);
                result.putAll(evt.toMap());
            },
            () -> result.put("active", false)
        );
        return MCPResponse.success(request.getId(), result);
    }

    /**
     * get_deployment_queue – returns all queued (pending) deployment events.
     */
    private MCPResponse getDeploymentQueue(MCPRequest request) {
        List<Map<String, Object>> queued = syncMgr.getQueueSnapshot()
                .stream().map(DeploymentEvent::toMap).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queueSize",        queued.size());
        result.put("isDeploymentActive", syncMgr.isDeploymentActive());
        result.put("events",           queued);
        syncMgr.getActiveDeployment().ifPresent(a -> result.put("activeEvent", a.toMap()));

        return MCPResponse.success(request.getId(), result);
    }

    /**
     * cancel_deployment – cancel a queued event.
     *
     * Required params: eventId (String)
     * Optional params: reason (String)
     */
    private MCPResponse cancelDeployment(MCPRequest request) {
        String eventId = request.getStringParam("eventId");
        if (eventId == null) {
            return MCPResponse.error(request.getId(), "Missing required param: eventId");
        }
        boolean cancelled = syncMgr.cancelEvent(eventId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cancelled", cancelled);
        result.put("eventId",   eventId);
        result.put("message",   cancelled
                ? "Event " + eventId + " cancelled."
                : "Event " + eventId + " not found in queue (may already be active or completed).");
        return cancelled
                ? MCPResponse.success(request.getId(), result)
                : MCPResponse.error(request.getId(), (String) result.get("message"));
    }

    /**
     * get_deployment_history – returns the last N completed/failed/cancelled events.
     *
     * Optional params: limit (int, default 10)
     */
    private MCPResponse getDeploymentHistory(MCPRequest request) {
        int limit = Objects.requireNonNullElse(request.getIntParam("limit"), 10);
        List<Map<String, Object>> events = syncMgr.getHistory(limit)
                .stream().map(DeploymentEvent::toMap).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count",  events.size());
        result.put("events", events);
        return MCPResponse.success(request.getId(), result);
    }

    // ── PR Gate check ─────────────────────────────────────────────────────

    /**
     * check_pr_gate – explicitly verify whether a PR has been approved and
     * merged, making deployment eligible.
     *
     * Required params: prNumber (int)
     */
    private MCPResponse checkPrGate(MCPRequest request) {
        Integer prNumber = request.getIntParam("prNumber");
        if (prNumber == null) {
            return MCPResponse.error(request.getId(), "Missing required param: prNumber");
        }

        boolean allowed = gate.isDeploymentAllowed(prNumber);
        Optional<PRDeploymentGate.PRRecord> optRecord = gate.findByPrNumber(prNumber);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prNumber",           prNumber);
        result.put("deploymentAllowed",  allowed);

        optRecord.ifPresentOrElse(
            record -> {
                result.put("prState",   record.getState().name());
                result.put("branch",    record.getBranch());
                result.put("targetBranch", record.getTargetBranch());
            },
            () -> result.put("prState", "NOT_REGISTERED")
        );

        if (allowed) {
            System.out.println(GREEN + "[DeploymentMCPServer] 🟢 PR gate OPEN for PR #" + prNumber + RESET);
            return MCPResponse.success(request.getId(), result);
        } else {
            String msg = "Deployment BLOCKED: PR #" + prNumber + " state="
                    + result.get("prState") + ". Must be MERGED into master first.";
            System.out.println(RED + "[DeploymentMCPServer] 🚫 " + msg + RESET);
            result.put("blockReason", msg);
            return MCPResponse.error(request.getId(), msg);
        }
    }
    
    @Override
    public boolean isHealthy() {
        return true;
    }
    
    private MCPResponse recommendStrategy(MCPRequest request) {
        String changeType = request.getStringParam("changeType");
        String environment = request.getStringParam("environment");
        Double riskLevel = request.getDoubleParam("riskLevel");
        
        if (changeType == null || environment == null) {
            return MCPResponse.error(request.getId(), "Missing required parameters");
        }
        
        Map<String, Object> recommendation = new HashMap<>();
        
        // Determine deployment strategy based on context
        if ("production".equals(environment)) {
            if (riskLevel != null && riskLevel > 0.7) {
                recommendation.put("strategy", "CANARY");
                recommendation.put("description", "Deploy to 5% of users first, monitor, then gradually increase");
                recommendation.put("rollbackTime", "30 seconds");
                recommendation.put("monitoringPeriod", "15 minutes");
            } else if ("hotfix".equals(changeType)) {
                recommendation.put("strategy", "ROLLING");
                recommendation.put("description", "Rolling update with health checks");
                recommendation.put("rollbackTime", "60 seconds");
                recommendation.put("monitoringPeriod", "5 minutes");
            } else {
                recommendation.put("strategy", "BLUE_GREEN");
                recommendation.put("description", "Switch traffic to new version after validation");
                recommendation.put("rollbackTime", "10 seconds");
                recommendation.put("monitoringPeriod", "10 minutes");
            }
        } else {
            recommendation.put("strategy", "DIRECT");
            recommendation.put("description", "Direct deployment to non-production environment");
            recommendation.put("rollbackTime", "60 seconds");
            recommendation.put("monitoringPeriod", "2 minutes");
        }
        
        recommendation.put("environment", environment);
        recommendation.put("changeType", changeType);
        recommendation.put("riskLevel", riskLevel != null ? riskLevel : 0.5);
        
        return MCPResponse.success(request.getId(), recommendation);
    }
    
    private MCPResponse assessRisk(MCPRequest request) {
        String changeType = request.getStringParam("changeType");
        Integer linesChanged = request.getIntParam("linesChanged");
        Boolean hasTests = (Boolean) request.getParam("hasTests");
        Boolean hasBreakingChanges = (Boolean) request.getParam("hasBreakingChanges");
        
        Map<String, Object> riskAssessment = new HashMap<>();
        
        double riskScore = 0.0;
        List<String> riskFactors = new ArrayList<>();
        
        // Assess risk factors
        if (linesChanged != null) {
            if (linesChanged > 500) {
                riskScore += 0.3;
                riskFactors.add("Large change set (>500 lines)");
            } else if (linesChanged > 100) {
                riskScore += 0.15;
                riskFactors.add("Medium change set (>100 lines)");
            }
        }
        
        if (hasTests == null || !hasTests) {
            riskScore += 0.25;
            riskFactors.add("No test coverage");
        }
        
        if (hasBreakingChanges != null && hasBreakingChanges) {
            riskScore += 0.3;
            riskFactors.add("Contains breaking changes");
        }
        
        if ("database".equals(changeType)) {
            riskScore += 0.2;
            riskFactors.add("Database migration");
        }
        
        // Normalize to 0-1
        riskScore = Math.min(1.0, riskScore);
        
        String riskLevel = riskScore < 0.3 ? "LOW" : (riskScore < 0.6 ? "MEDIUM" : "HIGH");
        
        riskAssessment.put("riskScore", riskScore);
        riskAssessment.put("riskLevel", riskLevel);
        riskAssessment.put("riskFactors", riskFactors);
        riskAssessment.put("recommendation", getRiskRecommendation(riskLevel));
        
        return MCPResponse.success(request.getId(), riskAssessment);
    }
    
    private MCPResponse calculateRollbackTime(MCPRequest request) {
        String strategy = request.getStringParam("strategy");
        String environment = request.getStringParam("environment");
        
        Map<String, Object> result = new HashMap<>();
        
        int rollbackSeconds = switch (strategy != null ? strategy : "DIRECT") {
            case "CANARY" -> 30;
            case "BLUE_GREEN" -> 10;
            case "ROLLING" -> 60;
            case "DIRECT" -> 120;
            default -> 60;
        };
        
        // Production takes longer
        if ("production".equals(environment)) {
            rollbackSeconds *= 1.5;
        }
        
        result.put("rollbackTimeSeconds", rollbackSeconds);
        result.put("strategy", strategy);
        result.put("environment", environment);
        result.put("automated", true);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    private MCPResponse validateDeployment(MCPRequest request) {
        String environment = request.getStringParam("environment");
        Map<String, Object> deploymentConfig = (Map<String, Object>) request.getParam("config");

        List<String> validations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;

        // ── PR Gate enforcement ───────────────────────────────────────────
        Integer prNumber = request.getIntParam("prNumber");
        if (prNumber != null) {
            boolean gateOpen = gate.isDeploymentAllowed(prNumber);
            if (!gateOpen) {
                Optional<PRDeploymentGate.PRRecord> optRecord = gate.findByPrNumber(prNumber);
                String state = optRecord.map(r -> r.getState().name()).orElse("NOT_REGISTERED");
                isValid = false;
                validations.add("PR #" + prNumber + " has not been approved and merged "
                        + "(current state: " + state + "). Deployment is blocked.");
                System.out.println(RED + "[DeploymentMCPServer] 🚫 PR gate check FAILED "
                        + "for PR #" + prNumber + " (state=" + state + ")" + RESET);
            } else {
                System.out.println(GREEN + "[DeploymentMCPServer] ✅ PR gate check PASSED "
                        + "for PR #" + prNumber + RESET);
            }
        } else {
            // No prNumber supplied – warn but don't block (legacy call path)
            warnings.add("No prNumber provided; skipping PR gate check. "
                    + "Supply prNumber to enforce the deploy gate.");
        }

        // ── Standard configuration checks ────────────────────────────────
        if (deploymentConfig == null) {
            isValid = false;
            validations.add("Missing deployment configuration");
        } else {
            if (!deploymentConfig.containsKey("version")) {
                warnings.add("No version specified");
            }
            if (!deploymentConfig.containsKey("healthCheck")) {
                warnings.add("No health check endpoint configured");
            }
            if ("production".equals(environment) && !deploymentConfig.containsKey("rollbackPlan")) {
                isValid = false;
                validations.add("Production deployments require a rollback plan");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("isValid",      isValid);
        result.put("validations",  validations);
        result.put("warnings",     warnings);
        result.put("environment",  environment);
        result.put("prGateChecked", prNumber != null);

        return isValid ?
            MCPResponse.success(request.getId(), result) :
            MCPResponse.error(request.getId(), "Deployment validation failed");
    }
    
    private MCPResponse scheduleDeployment(MCPRequest request) {
        String environment  = request.getStringParam("environment");
        String preferredTime = request.getStringParam("preferredTime");

        // ── PR Gate: production scheduling is blocked until PR is merged ──
        Integer prNumber = request.getIntParam("prNumber");
        if ("production".equals(environment) && prNumber != null) {
            if (!gate.isDeploymentAllowed(prNumber)) {
                Optional<PRDeploymentGate.PRRecord> optRecord = gate.findByPrNumber(prNumber);
                String state = optRecord.map(r -> r.getState().name()).orElse("NOT_REGISTERED");
                String msg = "Cannot schedule production deployment: PR #" + prNumber
                        + " state=" + state + " (must be MERGED first).";
                System.out.println(RED + "[DeploymentMCPServer] 🚫 " + msg + RESET);
                return MCPResponse.error(request.getId(), msg);
            }
        }

        Map<String, Object> schedule = new HashMap<>();

        // Recommend optimal deployment windows
        if ("production".equals(environment)) {
            schedule.put("recommendedWindow", "Tuesday-Thursday, 10:00-14:00 UTC");
            schedule.put("avoidTimes", Arrays.asList(
                "Monday mornings (high traffic)",
                "Friday afternoons (limited support)",
                "Weekends (reduced monitoring)"
            ));
            schedule.put("requiresApproval", true);
            schedule.put("notifyTeams", Arrays.asList("ops", "support", "engineering"));
        } else {
            schedule.put("recommendedWindow", "Any time");
            schedule.put("requiresApproval", false);
            schedule.put("notifyTeams", Arrays.asList("engineering"));
        }

        schedule.put("environment",   environment);
        schedule.put("preferredTime", preferredTime);
        schedule.put("prGatePassed",  prNumber == null || gate.isDeploymentAllowed(prNumber));

        return MCPResponse.success(request.getId(), schedule);
    }
    
    private String getRiskRecommendation(String riskLevel) {
        return switch (riskLevel) {
            case "LOW" -> "Safe to deploy with standard monitoring";
            case "MEDIUM" -> "Deploy with enhanced monitoring and staged rollout";
            case "HIGH" -> "Requires additional review, staged rollout, and extended monitoring";
            default -> "Manual review required";
        };
    }
}
