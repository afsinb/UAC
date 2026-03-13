package com.blacklight.uac.application.usecase;

import com.blacklight.uac.domain.healing.HealingAction.HealingType;
import com.blacklight.uac.domain.healing.HealingContext;

/**
 * HealingExecutionRequest - Application DTO
 * Input for healing execution use case
 */
public class HealingExecutionRequest {
    private final String anomalyId;
    private final HealingType healingType;
    private final String targetComponent;
    private final HealingContext healingContext;
    
    public HealingExecutionRequest(String anomalyId, HealingType healingType, 
                                  String targetComponent, HealingContext context) {
        this.anomalyId = anomalyId;
        this.healingType = healingType;
        this.targetComponent = targetComponent;
        this.healingContext = context;
    }
    
    public String getAnomalyId() { return anomalyId; }
    public HealingType getHealingType() { return healingType; }
    public String getTargetComponent() { return targetComponent; }
    public HealingContext getHealingContext() { return healingContext; }
}

