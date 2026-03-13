package com.blacklight.uac;

import com.blacklight.uac.application.usecase.*;
import com.blacklight.uac.domain.anomaly.*;
import com.blacklight.uac.domain.healing.*;
import com.blacklight.uac.domain.metrics.*;
import com.blacklight.uac.infrastructure.event.*;
import com.blacklight.uac.infrastructure.service.*;

/**
 * SelfHealingMonitor - Main Orchestrator
 * 
 * AI-Supported Self-Healing Monitoring System
 * 
 * Architecture:
 * - Domain Layer: Pure business logic (anomalies, healing, metrics)
 * - Application Layer: Use cases coordinating domain services
 * - Infrastructure Layer: Implementations and external integrations
 * 
 * Design Patterns Used:
 * - Clean Architecture (Layered)
 * - Domain-Driven Design
 * - Event-Driven Architecture
 * - Strategy Pattern (Healing strategies)
 * - Factory Pattern (Anomaly creation)
 * - Repository Pattern (Anomaly storage)
 * - Dependency Injection
 */
public class SelfHealingMonitor {
    private final EventBus eventBus;
    private final AnomalyDetectionUseCase anomalyDetectionUseCase;
    private final HealingExecutionUseCase healingExecutionUseCase;
    private final MetricsAggregator metricsAggregator;
    private final HealingService healingService;
    private boolean isRunning;
    
    private SelfHealingMonitor(Builder builder) {
        this.eventBus = builder.eventBus;
        this.anomalyDetectionUseCase = builder.anomalyDetectionUseCase;
        this.healingExecutionUseCase = builder.healingExecutionUseCase;
        this.metricsAggregator = builder.metricsAggregator;
        this.healingService = builder.healingService;
        this.isRunning = false;
        
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        eventBus.subscribe(AnomalyDetectedEvent.class, event -> {
            System.out.println("📡 Anomaly detected: " + event.getAnomalyType() + 
                             " (Severity: " + event.getSeverity() + ")");
        });
    }
    
    /**
     * Start monitoring the system
     */
    public void start() {
        this.isRunning = true;
        System.out.println("🚀 Self-Healing Monitor started");
    }
    
    /**
     * Stop monitoring
     */
    public void stop() {
        this.isRunning = false;
        eventBus.shutdown();
        System.out.println("🛑 Self-Healing Monitor stopped");
    }
    
    /**
     * Perform monitoring cycle
     */
    public void monitorCycle() {
        if (!isRunning) {
            throw new IllegalStateException("Monitor is not running");
        }
        
        // Get current metrics
        HealthScore healthScore = metricsAggregator.calculateHealthScore(
            new MetricsContext(
                metricsAggregator.getCurrentMetrics(),
                null,
                5 * 60 * 1000  // 5 minute window
            )
        );
        
        System.out.println("📊 Current Health Score: " + healthScore);
    }
    
    /**
     * Execute anomaly detection
     */
    public AnomalyDetectionResult detectAnomalies(AnomalyDetectionRequest request) {
        AnomalyDetectionResult result = anomalyDetectionUseCase.execute(request);
        
        // Publish events for each anomaly
        result.getAnomalies().forEach(anomaly -> {
            AnomalyDetectedEvent event = new AnomalyDetectedEvent(anomaly);
            eventBus.publish(event);
        });
        
        return result;
    }
    
    /**
     * Execute healing
     */
    public HealingExecutionResult executeHealing(HealingExecutionRequest request) {
        return healingExecutionUseCase.execute(request);
    }
    
    public EventBus getEventBus() {
        return eventBus;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    // Builder Pattern
    public static class Builder {
        private EventBus eventBus;
        private AnomalyDetectionUseCase anomalyDetectionUseCase;
        private HealingExecutionUseCase healingExecutionUseCase;
        private MetricsAggregator metricsAggregator;
        private HealingService healingService;
        
        public Builder withEventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }
        
        public Builder withAnomalyDetectionUseCase(AnomalyDetectionUseCase useCase) {
            this.anomalyDetectionUseCase = useCase;
            return this;
        }
        
        public Builder withHealingExecutionUseCase(HealingExecutionUseCase useCase) {
            this.healingExecutionUseCase = useCase;
            return this;
        }
        
        public Builder withMetricsAggregator(MetricsAggregator aggregator) {
            this.metricsAggregator = aggregator;
            return this;
        }
        
        public Builder withHealingService(HealingService service) {
            this.healingService = service;
            return this;
        }
        
        public SelfHealingMonitor build() {
            validateRequiredFields();
            return new SelfHealingMonitor(this);
        }
        
        private void validateRequiredFields() {
            if (eventBus == null) {
                throw new IllegalArgumentException("EventBus is required");
            }
            if (anomalyDetectionUseCase == null) {
                throw new IllegalArgumentException("AnomalyDetectionUseCase is required");
            }
            if (healingExecutionUseCase == null) {
                throw new IllegalArgumentException("HealingExecutionUseCase is required");
            }
            if (metricsAggregator == null) {
                throw new IllegalArgumentException("MetricsAggregator is required");
            }
            if (healingService == null) {
                throw new IllegalArgumentException("HealingService is required");
            }
        }
    }
}

