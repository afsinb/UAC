package com.blacklight.uac.demo;

import com.blacklight.uac.ui.SelfHealingDashboard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSystemsMonitorDemoFeatureImplementationTest {

    @Test
    void appliesPaymentIdempotencyFeatureImplementation() throws Exception {
        Path repo = Files.createTempDirectory("uac-payment-feature");
        Files.writeString(repo.resolve("PaymentController.java"), paymentControllerSource(), StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("PaymentService.java"), paymentServiceSource(), StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("PaymentRequest.java"), paymentRequestSource(), StandardCharsets.UTF_8);

        boolean changed = invokeFeatureImplementation(repo, "payment-api", "Add idempotency key support to charge endpoint");

        assertTrue(changed);
        assertTrue(Files.readString(repo.resolve("PaymentController.java")).contains("Idempotency-Key"));
        assertTrue(Files.readString(repo.resolve("PaymentService.java")).contains("idempotencyCache"));
        assertTrue(Files.readString(repo.resolve("PaymentRequest.java")).contains("idempotencyKey"));
    }

    @Test
    void appliesCacheTopKeysFeatureImplementation() throws Exception {
        Path repo = Files.createTempDirectory("uac-cache-feature");
        Files.writeString(repo.resolve("CacheController.java"), cacheControllerSource(), StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("CacheService.java"), cacheServiceSource(), StandardCharsets.UTF_8);

        boolean changed = invokeFeatureImplementation(repo, "cache-service", "Expose cache stats endpoint with top keys");

        assertTrue(changed);
        assertTrue(Files.readString(repo.resolve("CacheController.java")).contains("/stats/top-keys"));
        assertTrue(Files.readString(repo.resolve("CacheController.java")).contains("top_keys"));
        assertTrue(Files.readString(repo.resolve("CacheService.java")).contains("getTopKeys(int limit)"));
    }

    @Test
    void appliesWorkerDeadLetterSummaryFeatureImplementation() throws Exception {
        Path repo = Files.createTempDirectory("uac-worker-feature");
        Files.writeString(repo.resolve("HealthController.java"), workerControllerSource(), StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("WorkerService.java"), workerServiceSource(), StandardCharsets.UTF_8);

        boolean changed = invokeFeatureImplementation(repo, "worker-service", "Add dead-letter retry dashboard summary");

        assertTrue(changed);
        assertTrue(Files.readString(repo.resolve("HealthController.java")).contains("/dead-letter/summary"));
        assertTrue(Files.readString(repo.resolve("HealthController.java")).contains("dead_letter_summary"));
        assertTrue(Files.readString(repo.resolve("WorkerService.java")).contains("getDeadLetterSummary"));
        assertTrue(Files.readString(repo.resolve("WorkerService.java")).contains("deadLetterJobs"));
    }

    private boolean invokeFeatureImplementation(Path repoPath, String systemName, String title) throws Exception {
        LocalSystemsMonitorDemo demo = new LocalSystemsMonitorDemo(0, 0);
        LocalSystemsMonitorDemo.SystemConfig cfg = new LocalSystemsMonitorDemo.SystemConfig();
        cfg.name = systemName;

        SelfHealingDashboard.HealingFlow flow = new SelfHealingDashboard.HealingFlow("system-1", "FEATURE_DELIVERY");
        SelfHealingDashboard.AnomalyDetails anomaly = new SelfHealingDashboard.AnomalyDetails();
        anomaly.message = title;
        flow.anomaly = anomaly;

        Method method = LocalSystemsMonitorDemo.class.getDeclaredMethod(
                "applyFeatureImplementation",
                Path.class,
                LocalSystemsMonitorDemo.SystemConfig.class,
                SelfHealingDashboard.HealingFlow.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(demo, repoPath, cfg, flow);
    }

    private String paymentControllerSource() {
        return """
                package com.afsinb.payment;

                import lombok.RequiredArgsConstructor;
                import lombok.extern.slf4j.Slf4j;
                import org.springframework.web.bind.annotation.*;

                import java.util.Map;

                @Slf4j
                @RestController
                @RequestMapping("/api/payments")
                @RequiredArgsConstructor
                public class PaymentController {

                    private final PaymentService paymentService;

                    @PostMapping
                    public Payment processPayment(@RequestBody PaymentRequest request) {
                        return paymentService.processPayment(request);
                    }

                    @GetMapping("/health")
                    public Map<String, Object> health() {
                        return Map.of(
                            "status", "UP",
                            "service", "payment-api",
                            "errors", paymentService.getErrorCount(),
                            "error_rate", paymentService.getErrorRate()
                        );
                    }
                }
                """;
    }

    private String paymentServiceSource() {
        return """
                package com.afsinb.payment;

                import lombok.extern.slf4j.Slf4j;
                import org.springframework.stereotype.Service;

                import java.math.BigDecimal;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.UUID;

                @Slf4j
                @Service
                public class PaymentService {

                    private volatile boolean nullCustomerFailureEnabled = false;
                    private volatile boolean divisionByZeroEnabled = true;
                    private volatile int forcedFailuresRemaining = 0;

                    private final List<String> recentErrors = new ArrayList<>();

                    public Payment processPayment(PaymentRequest request) {
                        try {
                            log.info("Processing payment: amount={}, currency={}", request.getAmount(), request.getCurrency());

                            if (forcedFailuresRemaining > 0) {
                                forcedFailuresRemaining--;
                                log.error("Forced payment failure injected by anomaly toggle. Remaining={}", forcedFailuresRemaining);
                                throw new IllegalStateException("Injected payment failure");
                            }

                            if (request.getCustomer() == null) {
                                if (nullCustomerFailureEnabled) {
                                    log.error("Customer object is null");
                                    throw new NullPointerException("Customer cannot be null");
                                }
                                log.warn("[UAC Fix] Customer is null - defaulting to prevent NullPointerException");
                                request.setCustomer("unknown_customer");
                            }

                            if (request.getExchangeRate() == 0) {
                                if (divisionByZeroEnabled) {
                                    log.error("Exchange rate is zero - division error");
                                    new BigDecimal(request.getAmount()).divide(new BigDecimal(0));
                                }
                                log.warn("[UAC Fix] Exchange rate was zero - defaulting to 1.0");
                                request.setExchangeRate(1.0);
                            }

                            Payment payment = new Payment();
                            payment.setId(UUID.randomUUID().toString());
                            payment.setAmount(request.getAmount());
                            payment.setStatus("SUCCESS");
                            payment.setTimestamp(System.currentTimeMillis());

                            log.info("Payment processed successfully: id={}, amount={}", payment.getId(), payment.getAmount());
                            return payment;

                        } catch (Exception e) {
                            log.error("Payment processing failed", e);
                            recentErrors.add(e.getMessage());
                            if (recentErrors.size() > 100) {
                                recentErrors.remove(0);
                            }
                            throw e;
                        }
                    }
                }
                """;
    }

    private String paymentRequestSource() {
        return """
                package com.afsinb.payment;

                import lombok.AllArgsConstructor;
                import lombok.Data;
                import lombok.NoArgsConstructor;

                @Data
                @NoArgsConstructor
                @AllArgsConstructor
                public class PaymentRequest {
                    private String customer;
                    private double amount;
                    private String currency;
                    private double exchangeRate;
                }
                """;
    }

    private String cacheControllerSource() {
        return """
                package com.afsinb.cache;

                import lombok.RequiredArgsConstructor;
                import lombok.extern.slf4j.Slf4j;
                import org.springframework.web.bind.annotation.*;

                import java.util.Map;

                @Slf4j
                @RestController
                @RequestMapping("/api/cache")
                @RequiredArgsConstructor
                public class CacheController {

                    private final CacheService cacheService;

                    @GetMapping("/stats")
                    public Map<String, Object> stats() {
                        return Map.of(
                            "size", cacheService.getCacheSize(),
                            "memory", cacheService.getMemoryUsage(),
                            "hit_ratio", cacheService.getHitRatio(),
                            "timestamp", System.currentTimeMillis()
                        );
                    }
                }
                """;
    }

    private String cacheServiceSource() {
        return """
                package com.afsinb.cache;

                import lombok.extern.slf4j.Slf4j;
                import org.springframework.stereotype.Service;
                import java.util.*;

                @Slf4j
                @Service
                public class CacheService {
                    private Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(1024, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                            return size() > 2000;
                        }
                    };
                    private long hits = 0;
                    private long misses = 0;

                    public long getCacheSize() {
                        return cache.size();
                    }

                    public long getMemoryUsage() {
                        return 1;
                    }

                    public double getHitRatio() {
                        long total = hits + misses;
                        return total == 0 ? 0 : (double) hits / total;
                    }

                    static class CacheEntry {
                    }
                }
                """;
    }

    private String workerControllerSource() {
        return """
                package com.afsinb.worker;

                import lombok.RequiredArgsConstructor;
                import lombok.extern.slf4j.Slf4j;
                import org.springframework.web.bind.annotation.*;
                import java.util.Map;

                @Slf4j
                @RestController
                @RequestMapping("/api/worker")
                @RequiredArgsConstructor
                public class HealthController {

                    private final WorkerService workerService;

                    @GetMapping("/stats")
                    public Map<String, Object> stats() {
                        return Map.of(
                            "processed", workerService.getJobsProcessed(),
                            "failed", workerService.getJobsFailed(),
                            "memory", workerService.getMemoryUsage(),
                            "leak_size", workerService.getLeakSize(),
                            "timestamp", System.currentTimeMillis()
                        );
                    }
                }
                """;
    }

    private String workerServiceSource() {
        return """
                package com.afsinb.worker;

                import lombok.extern.slf4j.Slf4j;
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Service;
                import java.util.*;

                @Slf4j
                @Service
                public class WorkerService {

                    private List<String> memoryLeak = new ArrayList<>();
                    private volatile int leakMultiplier = 1;
                    private volatile long extraDelayMs = 0;
                    private volatile boolean failureModeEnabled = false;
                    private long jobsProcessed = 0;
                    private long jobsFailed = 0;
                    private long processingTime = 0;

                    @Scheduled(fixedRate = 5000)
                    public void processJobs() {
                        try {
                            for (int i = 0; i < 10; i++) {
                                if (failureModeEnabled && i % 4 == 0) {
                                    jobsFailed++;
                                    log.error("Injected worker failure while processing job batch (i={})", i);
                                }
                                jobsProcessed++;
                            }
                        } catch (Exception e) {
                            jobsFailed++;
                            log.error("Job processing failed", e);
                        }
                    }

                    public long getJobsProcessed() {
                        return jobsProcessed;
                    }

                    public long getJobsFailed() {
                        return jobsFailed;
                    }

                    public long getMemoryUsage() {
                        return 1;
                    }

                    public long getLeakSize() {
                        return memoryLeak.size();
                    }
                }
                """;
    }
}

