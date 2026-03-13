package com.blacklight.uac.docker;

import java.io.*;
import java.util.List;

/**
 * DockerManager – thin wrapper around the Docker CLI used by UAC's self-healing
 * flows to restart or rebuild-and-redeploy containerised sample applications.
 *
 * All methods are static so they can be called from anywhere without wiring.
 */
public class DockerManager {

    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Restart an already-running container without rebuilding the image.
     * Used for operational fixes (e.g. memory-leak mitigation via restart).
     *
     * @param containerName e.g. "uac-cache-service"
     */
    public static boolean restart(String containerName) {
        System.out.println(CYAN + "🐳 Restarting container: " + containerName + RESET);
        boolean ok = run("docker", "restart", containerName);
        if (ok) System.out.println(GREEN + "  ✓ Container restarted: " + containerName + RESET);
        else    System.out.println(RED   + "  ✗ Failed to restart: "  + containerName + RESET);
        return ok;
    }

    /**
     * Rebuild the Docker image from source and bring the service back up.
     * Used after a code-fix has been committed – deploys the fix immediately.
     *
     * @param composeFile  absolute path to docker-compose.yml
     * @param serviceName  Compose service name (e.g. "cache-service")
     */
    public static boolean rebuildAndRedeploy(String composeFile, String serviceName) {
        System.out.println(CYAN + "🐳 Rebuilding and redeploying: " + serviceName + RESET);
        System.out.println(YELLOW + "  → docker compose up -d --build " + serviceName + RESET);
        boolean ok = run("docker", "compose", "-f", composeFile, "up", "-d", "--build", serviceName);
        if (ok) System.out.println(GREEN + "  ✓ Service redeployed: " + serviceName + RESET);
        else    System.out.println(RED   + "  ✗ Redeploy failed: "    + serviceName + RESET);
        return ok;
    }

    /**
     * Return the current lifecycle state of a container.
     *
     * @return "running", "exited", "restarting", "unknown", etc.
     */
    public static String status(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "inspect", "--format={{.State.Status}}", containerName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return out.isEmpty() ? "unknown" : out;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Fetch the last {@code tail} lines of a container's stdout/stderr.
     */
    public static String logs(String containerName, int tail) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "logs", "--tail", String.valueOf(tail), containerName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return out;
        } catch (Exception e) {
            return "(could not fetch logs: " + e.getMessage() + ")";
        }
    }

    /**
     * Check whether Docker daemon is reachable.
     */
    public static boolean isDaemonRunning() {
        return run("docker", "info");
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private static boolean run(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(List.of(cmd));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // drain stdout so the process doesn't block
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor() == 0;
        } catch (Exception e) {
            System.out.println(RED + "  ✗ Docker command failed: " + e.getMessage() + RESET);
            return false;
        }
    }
}

