package com.blacklight.uac.demo;

import com.blacklight.uac.config.ApplicationConfig;
import com.blacklight.uac.config.BeanConfiguration;
import com.blacklight.uac.ui.RealTimeDashboard;

import java.util.Scanner;

/**
 * RealTimeDashboardDemo - Runs the real-time monitoring dashboard
 */
public class RealTimeDashboardDemo {
    
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    public static void main(String[] args) throws Exception {
        System.out.println(CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         UAC REAL-TIME MONITORING DASHBOARD                            ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Live monitoring of the self-healing system                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println(RESET);
        System.out.println();
        
        // Initialize UAC
        ApplicationConfig config = new ApplicationConfig();
        BeanConfiguration beans = new BeanConfiguration(config);
        
        // Start dashboard on port 8080
        int port = 8080;
        RealTimeDashboard dashboard = new RealTimeDashboard(beans, port);
        dashboard.start();
        
        System.out.println();
        System.out.println(GREEN + "✓ Dashboard is running!" + RESET);
        System.out.println(CYAN + "→ Open http://localhost:" + port + " in your browser" + RESET);
        System.out.println();
        System.out.println("Press Enter to stop the dashboard...");
        
        // Wait for user input
        new Scanner(System.in).nextLine();
        
        dashboard.stop();
        beans.shutdown();
        
        System.out.println(GREEN + "Dashboard stopped." + RESET);
    }
}
