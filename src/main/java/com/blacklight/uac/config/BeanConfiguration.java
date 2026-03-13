package com.blacklight.uac.config;

import com.blacklight.uac.api.rest.AnomalyController;
import com.blacklight.uac.api.rest.HealthController;
import com.blacklight.uac.api.rest.HealingController;
import com.blacklight.uac.core.coordinator.Brain;
import com.blacklight.uac.domain.anomaly.AnomalyDetectionService;
import com.blacklight.uac.domain.healing.HealingService;
import com.blacklight.uac.evolver.Evolver;
import com.blacklight.uac.infrastructure.service.AnomalyDetectionServiceImpl;
import com.blacklight.uac.infrastructure.service.HealingServiceImpl;
import com.blacklight.uac.infrastructure.service.MetricsAggregatorImpl;
import com.blacklight.uac.infrastructure.strategy.RestartHealingStrategy;
import com.blacklight.uac.infrastructure.strategy.ScalingHealingStrategy;
import com.blacklight.uac.observer.Observer;
import com.blacklight.uac.resolver.Resolver;
import com.blacklight.uac.mcp.DynamicMCPServer;
import com.blacklight.uac.ai.models.OpenRouterModel;

/**
 * BeanConfiguration - Dependency injection configuration
 * Wires together all UAC components
 */
public class BeanConfiguration {
    private final ApplicationConfig config;
    
    // Core components
    private Observer observer;
    private Resolver resolver;
    private Evolver evolver;
    private Brain brain;
    
    // Services
    private HealingService healingService;
    private AnomalyDetectionService anomalyDetectionService;
    
    // Controllers
    private HealthController healthController;
    private AnomalyController anomalyController;
    private HealingController healingController;
    
    // AI/MCP
    private DynamicMCPServer mcpServer;
    
    public BeanConfiguration(ApplicationConfig config) {
        this.config = config;
        initialize();
    }
    
    private void initialize() {
        // Initialize infrastructure services
        MetricsAggregatorImpl metricsAggregator = new MetricsAggregatorImpl();
        anomalyDetectionService = new AnomalyDetectionServiceImpl(metricsAggregator);
        
        // Initialize healing service with strategies
        healingService = new HealingServiceImpl();
        ((HealingServiceImpl) healingService).registerStrategy(new RestartHealingStrategy());
        ((HealingServiceImpl) healingService).registerStrategy(new ScalingHealingStrategy());
        
        // Initialize core components
        observer = new Observer();
        resolver = new Resolver();
        evolver = new Evolver(config.getRepositoryPath(), config.getSandboxPath());
        brain = new Brain(observer, resolver, evolver);
        
        // Initialize controllers
        healthController = new HealthController(observer);
        anomalyController = new AnomalyController(anomalyDetectionService);
        healingController = new HealingController(healingService);
        
        // Initialize MCP server with AI models
        mcpServer = new DynamicMCPServer();
        
        // Register OpenRouter if API key is available
        String openRouterKey = config.getOpenRouterApiKey();
        if (openRouterKey != null && !openRouterKey.isEmpty()) {
            String model = config.getOpenRouterModel();
            OpenRouterModel openRouter = new OpenRouterModel(openRouterKey, model);
            mcpServer.registerModel(openRouter);
            System.out.println("✓ OpenRouter.ai registered with model: " + model);
        }
    }
    
    // Getters for all components
    public ApplicationConfig getConfig() { return config; }
    
    public Observer getObserver() { return observer; }
    
    public Resolver getResolver() { return resolver; }
    
    public Evolver getEvolver() { return evolver; }
    
    public Brain getBrain() { return brain; }
    
    public HealingService getHealingService() { return healingService; }
    
    public AnomalyDetectionService getAnomalyDetectionService() { return anomalyDetectionService; }
    
    public HealthController getHealthController() { return healthController; }
    
    public AnomalyController getAnomalyController() { return anomalyController; }
    
    public HealingController getHealingController() { return healingController; }
    
    public DynamicMCPServer getMcpServer() { return mcpServer; }
    
    /**
     * Shutdown all components gracefully
     */
    public void shutdown() {
        if (brain != null) {
            brain.shutdown();
        }
    }
}
