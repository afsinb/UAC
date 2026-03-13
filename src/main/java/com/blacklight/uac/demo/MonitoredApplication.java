package com.blacklight.uac.demo;

import com.blacklight.uac.config.ApplicationConfig;
import com.blacklight.uac.config.BeanConfiguration;
import com.blacklight.uac.core.coordinator.Brain;
import com.blacklight.uac.monitoring.LiveMonitor;

import java.util.*;
import java.util.concurrent.*;

/**
 * MonitoredApplication - Demo application that UAC monitors and heals
 * Simulates a real application with configurable stress scenarios
 */
public class MonitoredApplication {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    private final LiveMonitor monitor;
    private final ExecutorService stressExecutor;
    private final List<byte[]> memoryHog;
    private volatile boolean running;
    
    public MonitoredApplication() {
        ApplicationConfig config = new ApplicationConfig();
        BeanConfiguration beans = new BeanConfiguration(config);
        Brain brain = beans.getBrain();
        
        this.monitor = new LiveMonitor(brain, config);
        this.stressExecutor = Executors.newFixedThreadPool(4);
        this.memoryHog = Collections.synchronizedList(new ArrayList<>());
        this.running = true;
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC LIVE MONITORING DEMO - REAL APPLICATION HEALING           ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  This demo shows UAC monitoring a real application and automatically  ║");
        System.out.println("║  healing it when issues are detected.                                 ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        MonitoredApplication app = new MonitoredApplication();
        app.run();
    }
    
    public void run() throws Exception {
        // Start monitoring with 2-second intervals
        monitor.start(2000);
        
        printMenu();
        
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.print(CYAN + "\nEnter command (h for help): " + RESET);
            String input = scanner.nextLine().trim().toLowerCase();
            
            switch (input) {
                case "1", "cpu" -> stressCPU();
                case "2", "memory" -> stressMemory();
                case "3", "threads" -> stressThreads();
                case "4", "all" -> stressAll();
                case "5", "status" -> showStatus();
                case "6", "clear" -> clearStress();
                case "h", "help" -> printMenu();
                case "q", "quit", "exit" -> shutdown();
                default -> System.out.println(YELLOW + "Unknown command. Type 'h' for help." + RESET);
            }
        }
        
        scanner.close();
    }
    
    private void printMenu() {
        System.out.println();
        System.out.println(CYAN + "┌─────────────────────────────────────────────────────────────────────┐" + RESET);
        System.out.println(CYAN + "│                        STRESS TEST MENU                             │" + RESET);
        System.out.println(CYAN + "├─────────────────────────────────────────────────────────────────────┤" + RESET);
        System.out.println(CYAN + "│" + RESET + "  1 or cpu     - Stress CPU (high computation)                      " + CYAN + "│" + RESET);
        System.out.println(CYAN + "│" + RESET + "  2 or memory  - Stress Memory (allocate large arrays)              " + CYAN + "│" + RESET);
        System.out.println(CYAN + "│" + RESET + "  3 or threads - Stress Threads (create many threads)               " + CYAN + "│" + RESET);
        System.out.println(CYAN + "│" + RESET + "  4 or all     - Stress All (combine all stressors)                  " + CYAN + "│" + RESET);
        System.out.println(CYAN + "│" + RESET + "  5 or status  - Show current system status                         " + CYAN + "│" + RESET);
        System.out.println(CYAN + "│" + RESET + "  6 or clear   - Clear all stress (release resources)               " + CYAN + "│" + RESET);
        System.out.println(CYAN + "│" + RESET + "  h or help    - Show this menu                                     " + CYAN + "│" + RESET);
        System.out.println(CYAN + "│" + RESET + "  q or quit    - Exit application                                   " + CYAN + "│" + RESET);
        System.out.println(CYAN + "└─────────────────────────────────────────────────────────────────────┘" + RESET);
        System.out.println();
        System.out.println(YELLOW + "Note: UAC will automatically detect stress and attempt to heal the system!" + RESET);
        System.out.println();
    }
    
    /**
     * Stress CPU with intensive computation
     */
    private void stressCPU() {
        System.out.println(RED + "🔥 Starting CPU stress test..." + RESET);
        
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            stressExecutor.submit(() -> {
                while (running) {
                    // Intensive computation
                    double result = 0;
                    for (int j = 0; j < 1000000; j++) {
                        result += Math.sqrt(j) * Math.sin(j) * Math.cos(j);
                    }
                    try {
                        Thread.sleep(10); // Small delay to prevent complete lockup
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        }
        
        System.out.println(YELLOW + "  → CPU stress active on " + Runtime.getRuntime().availableProcessors() + " cores" + RESET);
        System.out.println(YELLOW + "  → Watch UAC detect and respond to high CPU!" + RESET);
    }
    
    /**
     * Stress memory by allocating large arrays
     */
    private void stressMemory() {
        System.out.println(RED + "🔥 Starting Memory stress test..." + RESET);
        
        stressExecutor.submit(() -> {
            while (running) {
                try {
                    // Allocate 10MB chunks
                    byte[] chunk = new byte[10 * 1024 * 1024];
                    memoryHog.add(chunk);
                    Thread.sleep(500);
                } catch (OutOfMemoryError e) {
                    System.out.println(RED + "  ✗ Out of Memory! UAC should detect this." + RESET);
                    break;
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        System.out.println(YELLOW + "  → Memory stress active (allocating 10MB every 500ms)" + RESET);
        System.out.println(YELLOW + "  → Watch UAC detect and respond to high memory!" + RESET);
    }
    
    /**
     * Stress threads by creating many threads
     */
    private void stressThreads() {
        System.out.println(RED + "🔥 Starting Thread stress test..." + RESET);
        
        for (int i = 0; i < 100; i++) {
            stressExecutor.submit(() -> {
                while (running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        }
        
        System.out.println(YELLOW + "  → Thread stress active (100 additional threads)" + RESET);
        System.out.println(YELLOW + "  → Watch UAC detect and respond to thread pressure!" + RESET);
    }
    
    /**
     * Stress all resources
     */
    private void stressAll() {
        System.out.println(RED + "🔥 Starting FULL stress test (CPU + Memory + Threads)..." + RESET);
        stressCPU();
        stressMemory();
        stressThreads();
    }
    
    /**
     * Show current system status
     */
    private void showStatus() {
        var metrics = monitor.getCurrentMetrics();
        double healthScore = new com.blacklight.uac.monitoring.SystemMetricsCollector()
            .calculateHealthScore(metrics);
        
        System.out.println();
        System.out.println(CYAN + "┌─────────────────────────────────────────────────────────────────────┐" + RESET);
        System.out.println(CYAN + "│                        SYSTEM STATUS                                │" + RESET);
        System.out.println(CYAN + "├─────────────────────────────────────────────────────────────────────┤" + RESET);
        
        String healthColor = healthScore > 0.7 ? GREEN : (healthScore > 0.4 ? YELLOW : RED);
        System.out.printf(CYAN + "│" + RESET + "  Health Score:    " + healthColor + "%.2f" + RESET + 
                         "                                              " + CYAN + "│" + RESET + "%n", healthScore);
        System.out.printf(CYAN + "│" + RESET + "  CPU Usage:       %.1f%%" + 
                         "                                             " + CYAN + "│" + RESET + "%n", metrics.cpuUsage * 100);
        System.out.printf(CYAN + "│" + RESET + "  Memory Usage:    %.1f%%" + 
                         "                                             " + CYAN + "│" + RESET + "%n", metrics.memoryUsage * 100);
        System.out.printf(CYAN + "│" + RESET + "  Heap Usage:      %.1f%%" + 
                         "                                             " + CYAN + "│" + RESET + "%n", metrics.heapUsage * 100);
        System.out.printf(CYAN + "│" + RESET + "  Thread Count:    %d" + 
                         "                                               " + CYAN + "│" + RESET + "%n", metrics.threadCount);
        System.out.printf(CYAN + "│" + RESET + "  System Load:     %.2f" + 
                         "                                              " + CYAN + "│" + RESET + "%n", metrics.systemLoad);
        System.out.printf(CYAN + "│" + RESET + "  Memory Hog:      %d MB allocated" + 
                         "                                    " + CYAN + "│" + RESET + "%n", memoryHog.size() * 10);
        
        System.out.println(CYAN + "└─────────────────────────────────────────────────────────────────────┘" + RESET);
        System.out.println();
    }
    
    /**
     * Clear all stress and release resources
     */
    private void clearStress() {
        System.out.println(GREEN + "🧹 Clearing stress and releasing resources..." + RESET);
        
        // Clear memory hog
        memoryHog.clear();
        System.gc();
        
        System.out.println(GREEN + "  ✓ Memory released" + RESET);
        System.out.println(GREEN + "  ✓ Note: CPU/Thread stress will stop on next iteration" + RESET);
        System.out.println(GREEN + "  ✓ System should recover - watch health score improve!" + RESET);
    }
    
    /**
     * Shutdown the application
     */
    private void shutdown() {
        System.out.println(YELLOW + "Shutting down..." + RESET);
        running = false;
        monitor.stop();
        stressExecutor.shutdownNow();
        System.out.println(GREEN + "Goodbye!" + RESET);
        System.exit(0);
    }
}
