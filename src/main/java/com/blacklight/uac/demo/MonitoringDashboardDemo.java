package com.blacklight.uac.demo;

import com.blacklight.uac.config.ApplicationConfig;
import com.blacklight.uac.config.BeanConfiguration;
import com.blacklight.uac.ui.MonitoringDashboard;

import java.util.Scanner;

/**
 * MonitoringDashboardDemo - Runs the UAC monitoring dashboard
 */
public class MonitoringDashboardDemo {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC MONITORING DASHBOARD                                      ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Real-time visibility into the self-healing system                    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        // Initialize UAC
        ApplicationConfig config = new ApplicationConfig();
        BeanConfiguration beans = new BeanConfiguration(config);
        
        // Start dashboard on port 8080
        int port = 8080;
        MonitoringDashboard dashboard = new MonitoringDashboard(beans, port);
        dashboard.start();
        
        // Log some events
        dashboard.logEvent("INFO", "UAC system initialized");
        dashboard.logEvent("INFO", "Observer MCP Server online");
        dashboard.logEvent("INFO", "Resolver MCP Server online");
        dashboard.logEvent("INFO", "Evolver MCP Server online");
        dashboard.logEvent("INFO", "DynamicAI MCP Server online");
        
        System.out.println(GREEN + "✓ Dashboard is running!" + RESET);
        System.out.println(YELLOW + "→ Open http://localhost:" + port + " in your browser" + RESET);
        System.out.println();
        System.out.println(CYAN + "Press Enter to stop the dashboard..." + RESET);
        
        // Wait for user input
        new Scanner(System.in).nextLine();
        
        dashboard.stop();
        beans.shutdown();
        
        System.out.println(GREEN + "Dashboard stopped." + RESET);
    }
}
