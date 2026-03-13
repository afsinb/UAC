package com.blacklight.uac.resolver;

import java.util.HashMap;
import java.util.Map;

public class RecoveryAction {
        public enum ActionType {
            SCALE, RESTART, ROLLBACK
        }
        
        private ActionType actionType;
        private String targetResource;
        private Map<String, Object> parameters;
        private boolean idempotent;
        
        public RecoveryAction(ActionType actionType, String targetResource) {
            this.actionType = actionType;
            this.targetResource = targetResource;
            this.parameters = new HashMap<>();
            this.idempotent = true; // Default to idempotent
        }
        
        // Getters and setters
        public ActionType getActionType() { return actionType; }
        public void setActionType(ActionType actionType) { this.actionType = actionType; }
        public String getTargetResource() { return targetResource; }
        public void setTargetResource(String targetResource) { this.targetResource = targetResource; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public boolean isIdempotent() { return idempotent; }
        public void setIdempotent(boolean idempotent) { this.idempotent = idempotent; }
    }