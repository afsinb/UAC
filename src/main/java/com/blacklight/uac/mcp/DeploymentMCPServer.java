package com.blacklight.uac.mcp;

import java.util.*;

/**
 * DeploymentMCPServer - MCP server for deployment decisions
 * Provides AI-powered deployment strategy recommendations
 */
public class DeploymentMCPServer implements MCPServer {
    
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
            "schedule_deployment"
        );
    }
    
    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "recommend_strategy" -> recommendStrategy(request);
            case "assess_risk" -> assessRisk(request);
            case "calculate_rollback_time" -> calculateRollbackTime(request);
            case "validate_deployment" -> validateDeployment(request);
            case "schedule_deployment" -> scheduleDeployment(request);
            default -> MCPResponse.error(request.getId(), "Unknown method: " + request.getMethod());
        };
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
        
        // Validate deployment configuration
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
        result.put("isValid", isValid);
        result.put("validations", validations);
        result.put("warnings", warnings);
        result.put("environment", environment);
        
        return isValid ? 
            MCPResponse.success(request.getId(), result) :
            MCPResponse.error(request.getId(), "Deployment validation failed");
    }
    
    private MCPResponse scheduleDeployment(MCPRequest request) {
        String environment = request.getStringParam("environment");
        String preferredTime = request.getStringParam("preferredTime");
        
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
        
        schedule.put("environment", environment);
        schedule.put("preferredTime", preferredTime);
        
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
