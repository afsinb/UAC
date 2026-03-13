package com.blacklight.uac.mcp;

import java.util.*;

/**
 * MCPServer - Model Context Protocol server interface
 * Provides standardized interface for AI model interactions
 */
public interface MCPServer {
    
    /**
     * Get server name
     */
    String getName();
    
    /**
     * Get server capabilities
     */
    List<String> getCapabilities();
    
    /**
     * Handle a request
     */
    MCPResponse handleRequest(MCPRequest request);
    
    /**
     * Check if server is healthy
     */
    boolean isHealthy();
    
    /**
     * MCP Request
     */
    class MCPRequest {
        private final String id;
        private final String method;
        private final Map<String, Object> params;
        private final long timestamp;
        
        public MCPRequest(String id, String method, Map<String, Object> params) {
            this.id = id;
            this.method = method;
            this.params = params != null ? new HashMap<>(params) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getId() { return id; }
        public String getMethod() { return method; }
        public Map<String, Object> getParams() { return params; }
        public long getTimestamp() { return timestamp; }
        
        public Object getParam(String key) { return params.get(key); }
        public String getStringParam(String key) { return (String) params.get(key); }
        public Integer getIntParam(String key) { return (Integer) params.get(key); }
        public Double getDoubleParam(String key) { return (Double) params.get(key); }
    }
    
    /**
     * MCP Response
     */
    class MCPResponse {
        private final String id;
        private final boolean success;
        private final Object result;
        private final String error;
        private final Map<String, Object> metadata;
        
        public MCPResponse(String id, boolean success, Object result, String error) {
            this.id = id;
            this.success = success;
            this.result = result;
            this.error = error;
            this.metadata = new HashMap<>();
        }
        
        public static MCPResponse success(String id, Object result) {
            return new MCPResponse(id, true, result, null);
        }
        
        public static MCPResponse error(String id, String error) {
            return new MCPResponse(id, false, null, error);
        }
        
        public String getId() { return id; }
        public boolean isSuccess() { return success; }
        public Object getResult() { return result; }
        public String getError() { return error; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
    }
}
