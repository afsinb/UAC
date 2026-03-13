package com.blacklight.uac.demo.sampleapp;

import java.util.*;
import java.util.concurrent.*;

/**
 * SampleApplication - A sample web application with intentional anomalies
 * UAC will monitor this app and automatically detect/fix issues
 */
public class SampleApplication {
    
    private final Map<String, User> userDatabase;
    private final List<Order> orderHistory;
    private final Map<String, Session> activeSessions;
    private final ExecutorService requestHandler;
    private volatile boolean running;
    
    // Metrics
    private int totalRequests;
    private int failedRequests;
    private int nullPointerErrors;
    private int arrayIndexErrors;
    private int classCastErrors;
    private long totalResponseTime;
    
    public SampleApplication() {
        this.userDatabase = new HashMap<>();
        this.orderHistory = new ArrayList<>();
        this.activeSessions = new ConcurrentHashMap<>();
        this.requestHandler = Executors.newFixedThreadPool(10);
        this.running = true;
        
        // Initialize with some data
        initializeData();
    }
    
    private void initializeData() {
        userDatabase.put("user1", new User("user1", "Alice", "alice@example.com"));
        userDatabase.put("user2", new User("user2", "Bob", "bob@example.com"));
        userDatabase.put("user3", new User("user3", "Charlie", null)); // BUG: null email
    }
    
    /**
     * BUG #1: NullPointerException - doesn't check for null user
     */
    public String getUserEmail(String userId) {
        User user = userDatabase.get(userId);
        return user.getEmail(); // NPE if user not found or email is null
    }
    
    /**
     * BUG #2: ArrayIndexOutOfBoundsException - no bounds checking
     */
    public String getRecentOrder(int index) {
        Order[] orders = orderHistory.toArray(new Order[0]);
        return orders[index].toString(); // AIOOBE if index >= length
    }
    
    /**
     * BUG #3: ClassCastException - unsafe cast
     */
    public String processRequest(Object request) {
        // Assumes all requests are strings
        String req = (String) request; // CCE if request is not a String
        return "Processed: " + req.toUpperCase();
    }
    
    /**
     * BUG #4: Connection leak - doesn't close connections
     */
    public String queryDatabase(String query) {
        // Simulate database connection
        Connection conn = new Connection();
        conn.open();
        String result = conn.execute(query);
        // BUG: Connection never closed!
        return result;
    }
    
    /**
     * BUG #5: Thread safety issue
     */
    public void incrementCounter() {
        // Not synchronized - race condition
        totalRequests++;
        // Should be: synchronized(this) { totalRequests++; }
    }
    
    /**
     * BUG #6: Memory leak - sessions never cleaned up
     */
    public void createSession(String userId) {
        Session session = new Session(userId, System.currentTimeMillis());
        activeSessions.put(userId + "_" + System.currentTimeMillis(), session);
        // BUG: Old sessions never removed!
    }
    
    /**
     * BUG #7: Infinite loop potential
     */
    public List<String> searchUsers(String pattern) {
        List<String> results = new ArrayList<>();
        Iterator<User> it = userDatabase.values().iterator();
        while (it.hasNext()) {
            User user = it.next();
            if (user.getName().contains(pattern)) {
                results.add(user.getName());
                // BUG: Modifying during iteration can cause issues
                if (pattern.equals("admin")) {
                    userDatabase.put("admin", new User("admin", "Admin", "admin@example.com"));
                }
            }
        }
        return results;
    }
    
    /**
     * BUG #8: Resource exhaustion - creates too many threads
     */
    public CompletableFuture<String> handleRequestAsync(String request) {
        // Creates new thread for each request instead of using pool
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
                return "Response to: " + request;
            } catch (InterruptedException e) {
                return "Error";
            }
        }); // BUG: Should use custom executor with bounded pool
    }
    
    /**
     * BUG #9: SQL Injection vulnerability (simulated)
     */
    public User findUserByName(String name) {
        // Direct string concatenation - SQL injection risk
        String query = "SELECT * FROM users WHERE name = '" + name + "'";
        // In real app, this would execute the query
        return userDatabase.values().stream()
            .filter(u -> u.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * BUG #10: Unhandled exception
     */
    public double calculateAverage(List<Integer> numbers) {
        int sum = 0;
        for (int num : numbers) {
            sum += num;
        }
        return sum / numbers.size(); // ArithmeticException if list is empty
    }
    
    // Metrics collection
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalRequests", totalRequests);
        metrics.put("failedRequests", failedRequests);
        metrics.put("nullPointerErrors", nullPointerErrors);
        metrics.put("arrayIndexErrors", arrayIndexErrors);
        metrics.put("classCastErrors", classCastErrors);
        metrics.put("errorRate", totalRequests > 0 ? (double) failedRequests / totalRequests : 0);
        metrics.put("avgResponseTime", totalRequests > 0 ? totalResponseTime / totalRequests : 0);
        metrics.put("activeSessions", activeSessions.size());
        metrics.put("userCount", userDatabase.size());
        metrics.put("orderCount", orderHistory.size());
        return metrics;
    }
    
    public void shutdown() {
        running = false;
        requestHandler.shutdown();
    }
    
    // Inner classes
    public static class User {
        private String id;
        private String name;
        private String email;
        
        public User(String id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
    }
    
    public static class Order {
        private String orderId;
        private String userId;
        private double amount;
        
        public Order(String orderId, String userId, double amount) {
            this.orderId = orderId;
            this.userId = userId;
            this.amount = amount;
        }
        
        @Override
        public String toString() {
            return String.format("Order{id='%s', user='%s', amount=%.2f}", orderId, userId, amount);
        }
    }
    
    public static class Session {
        private String userId;
        private long createdAt;
        
        public Session(String userId, long createdAt) {
            this.userId = userId;
            this.createdAt = createdAt;
        }
    }
    
    public static class Connection {
        private boolean open = false;
        
        public void open() { open = true; }
        public void close() { open = false; }
        public String execute(String query) { return "Result of: " + query; }
    }
}
