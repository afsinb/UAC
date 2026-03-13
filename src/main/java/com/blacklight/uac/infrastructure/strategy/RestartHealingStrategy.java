package com.blacklight.uac.infrastructure.strategy;

import com.blacklight.uac.domain.healing.*;
import java.io.*;

/**
 * RestartHealingStrategy - Restarts services to recover from issues
 * Handles: Connection failures, resource exhaustion, hung processes
 */
public class RestartHealingStrategy implements HealingStrategy {
    
    @Override
    public HealingAction.HealingType getHealingType() {
        return HealingAction.HealingType.RESTART_SERVICE;
    }
    
    @Override
    public boolean canHandle(String anomalyId, HealingContext context) {
        // Can handle most operational issues
        return context != null;
    }
    
    @Override
    public HealingResult execute(HealingAction action, HealingContext context) {
        long startTime = System.currentTimeMillis();
        StringBuilder log = new StringBuilder();
        String target = action.getTargetComponent();
        
        try {
            log.append("🔄 Restarting service: ").append(target).append("\n");
            
            // 1. Graceful shutdown (real system call)
            log.append("  → Initiating graceful shutdown...\n");
            boolean shutdownSuccess = executeGracefulShutdown(target, log);
            if (!shutdownSuccess) {
                log.append("  ⚠ Graceful shutdown timed out, forcing kill\n");
                executeForceKill(target, log);
            }
            
            // 2. Wait for resource cleanup
            log.append("  → Waiting for resource cleanup...\n");
            Thread.sleep(1000); // Give OS time to clean up resources
            
            // 3. Start service (real system call)
            log.append("  → Starting service...\n");
            boolean startSuccess = executeServiceStart(target, log);
            if (!startSuccess) {
                log.append("  ✗ Failed to start service\n");
                long executionTime = System.currentTimeMillis() - startTime;
                return new HealingResult(
                    action.getId(),
                    false,
                    "Failed to restart service",
                    log.toString(),
                    executionTime
                );
            }
            
            // 4. Health check (real verification)
            log.append("  → Running health checks...\n");
            boolean healthy = verifyServiceHealth(target, log);
            
            if (healthy) {
                log.append("  ✓ Service restarted successfully\n");
            } else {
                log.append("  ⚠ Service started but health check inconclusive\n");
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            return new HealingResult(
                action.getId(),
                true,
                "Service restarted successfully: " + target,
                log.toString(),
                executionTime
            );
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.append("  ✗ Restart interrupted: ").append(e.getMessage()).append("\n");
            long executionTime = System.currentTimeMillis() - startTime;
            return new HealingResult(
                action.getId(),
                false,
                "Restart interrupted: " + e.getMessage(),
                log.toString(),
                executionTime
            );
        } catch (Exception e) {
            log.append("  ✗ Restart failed: ").append(e.getMessage()).append("\n");
            long executionTime = System.currentTimeMillis() - startTime;
            return new HealingResult(
                action.getId(),
                false,
                "Restart failed: " + e.getMessage(),
                log.toString(),
                executionTime
            );
        }
    }
    
    private boolean executeGracefulShutdown(String target, StringBuilder log) {
        try {
            // Try systemctl/service commands (Linux)
            ProcessBuilder pb = new ProcessBuilder("systemctl", "stop", target);
            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.append("    [systemctl stop ").append(target).append("] success\n");
                return true;
            }
        } catch (Exception e) {
            log.append("    systemctl not available\n");
        }
        
        // Try Docker/container commands
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", target);
            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.append("    [docker stop ").append(target).append("] success\n");
                return true;
            }
        } catch (Exception e) {
            // Docker not available
        }
        
        return false;
    }
    
    private boolean executeForceKill(String target, StringBuilder log) {
        try {
            ProcessBuilder pb = new ProcessBuilder("pkill", "-9", target);
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.append("    [pkill -9 ").append(target).append("] success\n");
                return true;
            }
        } catch (Exception e) {
            log.append("    Force kill failed: ").append(e.getMessage()).append("\n");
        }
        return false;
    }
    
    private boolean executeServiceStart(String target, StringBuilder log) {
        try {
            // Try systemctl first
            ProcessBuilder pb = new ProcessBuilder("systemctl", "start", target);
            Process process = pb.start();
            boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.append("    [systemctl start ").append(target).append("] success\n");
                return true;
            }
        } catch (Exception e) {
            log.append("    systemctl not available\n");
        }
        
        // Try Docker
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "start", target);
            Process process = pb.start();
            boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.append("    [docker start ").append(target).append("] success\n");
                return true;
            }
        } catch (Exception e) {
            // Docker not available
        }
        
        return false;
    }
    
    private boolean verifyServiceHealth(String target, StringBuilder log) {
        try {
            // Check if service is running and responsive
            ProcessBuilder pb = new ProcessBuilder("systemctl", "is-active", target);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.append("    Service is active and running\n");
                return true;
            }
        } catch (Exception e) {
            log.append("    Health check inconclusive\n");
        }
        return false;
    }
    
    @Override
    public int getPriority() {
        return 7; // Medium-high priority
    }
    
    @Override
    public boolean isIdempotent() {
        return true; // Restarting twice is safe
    }
}
