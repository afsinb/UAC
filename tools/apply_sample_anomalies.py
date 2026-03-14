from pathlib import Path

FILES = {
    "/tmp/sample-apps/payment-api/src/main/java/com/afsinb/payment/PaymentService.java": """package com.afsinb.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private volatile boolean nullCustomerFailureEnabled = false;
    private volatile boolean divisionByZeroEnabled = true;
    private volatile int forcedFailuresRemaining = 0;
    private volatile boolean autoChaosEnabled = true;

    private final Random random = new Random();
    private final List<String> recentErrors = new ArrayList<>();

    public Payment processPayment(PaymentRequest request) {
        try {
            maybeInjectNaturalAnomaly(request);
            log.info("Processing payment: amount={}, currency={}", request.getAmount(), request.getCurrency());

            if (forcedFailuresRemaining > 0) {
                forcedFailuresRemaining--;
                log.error("Forced payment failure triggered. Remaining={}", forcedFailuresRemaining);
                throw new IllegalStateException("Injected payment failure");
            }

            if (request.getCustomer() == null) {
                if (nullCustomerFailureEnabled) {
                    log.error("Customer object is null");
                    throw new NullPointerException("Customer cannot be null");
                }
                log.warn("Customer is null. Applying fallback customer id");
                request.setCustomer("unknown_customer");
            }

            if (request.getExchangeRate() == 0) {
                if (divisionByZeroEnabled) {
                    log.error("Exchange rate is zero. Triggering arithmetic failure");
                    new BigDecimal(request.getAmount()).divide(new BigDecimal(0));
                }
                log.warn("Exchange rate was zero. Applying safe default 1.0");
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
            recentErrors.add(String.valueOf(e.getMessage()));
            if (recentErrors.size() > 100) {
                recentErrors.remove(0);
            }
            throw e;
        }
    }

    private void maybeInjectNaturalAnomaly(PaymentRequest request) {
        if (!autoChaosEnabled) {
            return;
        }

        int roll = random.nextInt(100);
        if (roll >= 30) {
            return;
        }

        int anomaly = random.nextInt(4);
        if (anomaly == 0) {
            nullCustomerFailureEnabled = true;
            request.setCustomer(null);
            log.warn("Natural anomaly: null customer payload introduced");
            return;
        }

        if (anomaly == 1) {
            divisionByZeroEnabled = true;
            request.setExchangeRate(0.0);
            log.warn("Natural anomaly: zero exchange rate introduced");
            return;
        }

        if (anomaly == 2) {
            log.error("Unknown anomaly: PaymentLedgerMismatchException");
            throw new PaymentLedgerMismatchException("Ledger mismatch detected while reconciling payment");
        }

        forcedFailuresRemaining = Math.max(forcedFailuresRemaining, 1);
        log.warn("Natural anomaly: forced failure scheduled for this request");
    }

    public int getErrorCount() {
        return recentErrors.size();
    }

    public double getErrorRate() {
        return recentErrors.size() * 0.01;
    }

    public long getMemoryUsageMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    }

    public void setNullCustomerFailureEnabled(boolean enabled) {
        this.nullCustomerFailureEnabled = enabled;
        log.warn("Anomaly toggle changed: nullCustomerFailureEnabled={}", enabled);
    }

    public void setDivisionByZeroEnabled(boolean enabled) {
        this.divisionByZeroEnabled = enabled;
        log.warn("Anomaly toggle changed: divisionByZeroEnabled={}", enabled);
    }

    public void injectForcedFailures(int count) {
        this.forcedFailuresRemaining = Math.max(0, count);
        log.warn("Forced payment failures scheduled: {}", this.forcedFailuresRemaining);
    }

    public void setAutoChaosEnabled(boolean enabled) {
        this.autoChaosEnabled = enabled;
        log.warn("Anomaly toggle changed: autoChaosEnabled={}", enabled);
    }

    public void clearRecentErrors() {
        recentErrors.clear();
        log.info("Recent payment error history cleared");
    }

    public Map<String, Object> anomalyState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("null_customer_failure_enabled", nullCustomerFailureEnabled);
        state.put("division_by_zero_enabled", divisionByZeroEnabled);
        state.put("forced_failures_remaining", forcedFailuresRemaining);
        state.put("auto_chaos_enabled", autoChaosEnabled);
        state.put("recent_errors", recentErrors.size());
        return state;
    }

    static class PaymentLedgerMismatchException extends RuntimeException {
        PaymentLedgerMismatchException(String message) {
            super(message);
        }
    }
}
""",
    "/tmp/sample-apps/payment-api/src/main/java/com/afsinb/payment/PaymentController.java": """package com.afsinb.payment;

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
            "error_rate", paymentService.getErrorRate(),
            "memory_mb", paymentService.getMemoryUsageMb(),
            "cache_size", 0,
            "leak_size", 0
        );
    }

    @GetMapping("/admin/anomalies")
    public Map<String, Object> anomalyState() {
        return paymentService.anomalyState();
    }

    @PostMapping("/admin/anomalies")
    public Map<String, Object> configureAnomalies(
            @RequestParam(required = false) Boolean nullCustomerFailureEnabled,
            @RequestParam(required = false) Boolean divisionByZeroEnabled,
            @RequestParam(required = false) Integer forcedFailures,
            @RequestParam(required = false) Boolean autoChaosEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean clearErrors
    ) {
        if (nullCustomerFailureEnabled != null) {
            paymentService.setNullCustomerFailureEnabled(nullCustomerFailureEnabled);
        }
        if (divisionByZeroEnabled != null) {
            paymentService.setDivisionByZeroEnabled(divisionByZeroEnabled);
        }
        if (forcedFailures != null) {
            paymentService.injectForcedFailures(forcedFailures);
        }
        if (autoChaosEnabled != null) {
            paymentService.setAutoChaosEnabled(autoChaosEnabled);
        }
        if (clearErrors) {
            paymentService.clearRecentErrors();
        }
        return paymentService.anomalyState();
    }
}
""",
    "/tmp/sample-apps/cache-service/src/main/java/com/afsinb/cache/CacheService.java": """package com.afsinb.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class CacheService {

    private volatile boolean evictionEnabled = true;
    private volatile int generationBurstMultiplier = 1;
    private volatile boolean warningStormEnabled = false;
    private volatile boolean raceModeEnabled = true;
    private volatile boolean autoChaosEnabled = true;

    private final Random random = new Random();
    private long anomalyErrors = 0;

    private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return evictionEnabled && size() > 2000;
        }
    };

    private long hits = 0;
    private long misses = 0;

    @Scheduled(fixedRate = 2000)
    public void generateCacheEntries() {
        try {
            int effectiveBurst = Math.max(1, generationBurstMultiplier);
            if (autoChaosEnabled && random.nextInt(100) < 20) {
                effectiveBurst = Math.max(effectiveBurst, 30);
                evictionEnabled = false;
                log.warn("Natural anomaly: burst write with eviction disabled");
            }

            int entryCount = 100 * effectiveBurst;
            for (int i = 0; i < entryCount; i++) {
                String key = "cache_" + UUID.randomUUID();
                cache.put(key, new CacheEntry(key, "value_" + i, System.currentTimeMillis()));
            }

            if (warningStormEnabled && cache.size() % 500 == 0) {
                log.warn("Cache size exceeding threshold: {} entries", cache.size());
            }

            if (raceModeEnabled && cache.size() > 1500 && random.nextInt(100) < 25) {
                runRaceProbe();
            }

            if (autoChaosEnabled && random.nextInt(100) < 8) {
                throw new CacheCorruptionWindowException("Cache index drift detected");
            }

            log.info("Cache entries added. Total={}, memory={}MB, errors={}",
                    cache.size(), getMemoryUsage(), anomalyErrors);

        } catch (Exception e) {
            anomalyErrors++;
            log.error("Cache operation failed", e);
        }
    }

    private void runRaceProbe() {
        try {
            for (String key : cache.keySet()) {
                if (key.hashCode() % 97 == 0) {
                    cache.remove(key);
                }
            }
        } catch (ConcurrentModificationException e) {
            anomalyErrors++;
            log.error("Race condition anomaly detected in cache iteration", e);
        }
    }

    public String get(String key) {
        if (cache.containsKey(key)) {
            hits++;
            CacheEntry entry = cache.get(key);
            log.debug("Cache HIT for key: {}", key);
            return entry.getValue();
        }
        misses++;
        log.debug("Cache MISS for key: {}", key);
        return null;
    }

    public void put(String key, String value) {
        cache.put(key, new CacheEntry(key, value, System.currentTimeMillis()));
        log.debug("Cache PUT: {} = {}", key, value);
    }

    public long getCacheSize() {
        return cache.size();
    }

    public long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    }

    public double getHitRatio() {
        long total = hits + misses;
        return total == 0 ? 0 : (double) hits / total;
    }

    public long getErrorCount() {
        return anomalyErrors;
    }

    public void clearCache() {
        cache.clear();
        anomalyErrors = 0;
        log.info("Cache cleared");
    }

    public void setEvictionEnabled(boolean enabled) {
        this.evictionEnabled = enabled;
        log.warn("Anomaly toggle changed: evictionEnabled={}", enabled);
    }

    public void setGenerationBurstMultiplier(int multiplier) {
        this.generationBurstMultiplier = Math.max(1, multiplier);
        log.warn("Anomaly toggle changed: generationBurstMultiplier={}", this.generationBurstMultiplier);
    }

    public void setWarningStormEnabled(boolean enabled) {
        this.warningStormEnabled = enabled;
        log.warn("Anomaly toggle changed: warningStormEnabled={}", enabled);
    }

    public void setRaceModeEnabled(boolean enabled) {
        this.raceModeEnabled = enabled;
        log.warn("Anomaly toggle changed: raceModeEnabled={}", enabled);
    }

    public void setAutoChaosEnabled(boolean enabled) {
        this.autoChaosEnabled = enabled;
        log.warn("Anomaly toggle changed: autoChaosEnabled={}", enabled);
    }

    public Map<String, Object> anomalyState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("eviction_enabled", evictionEnabled);
        state.put("generation_burst_multiplier", generationBurstMultiplier);
        state.put("warning_storm_enabled", warningStormEnabled);
        state.put("race_mode_enabled", raceModeEnabled);
        state.put("auto_chaos_enabled", autoChaosEnabled);
        state.put("cache_size", cache.size());
        state.put("error_count", anomalyErrors);
        return state;
    }

    static class CacheEntry {
        private final String key;
        private final String value;
        private final long timestamp;

        CacheEntry(String key, String value, long timestamp) {
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        public String getValue() {
            return value;
        }
    }

    static class CacheCorruptionWindowException extends RuntimeException {
        CacheCorruptionWindowException(String message) {
            super(message);
        }
    }
}
""",
    "/tmp/sample-apps/cache-service/src/main/java/com/afsinb/cache/CacheController.java": """package com.afsinb.cache;

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

    @GetMapping("/{key}")
    public String get(@PathVariable String key) {
        return cacheService.get(key);
    }

    @PostMapping("/{key}")
    public void put(@PathVariable String key, @RequestBody String value) {
        cacheService.put(key, value);
    }

    @DeleteMapping
    public void clear() {
        cacheService.clearCache();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "cache-service",
            "errors", cacheService.getErrorCount(),
            "cache_size", cacheService.getCacheSize(),
            "memory_mb", cacheService.getMemoryUsage(),
            "leak_size", 0,
            "hit_ratio", String.format("%.2f", cacheService.getHitRatio())
        );
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
            "size", cacheService.getCacheSize(),
            "memory", cacheService.getMemoryUsage(),
            "errors", cacheService.getErrorCount(),
            "hit_ratio", cacheService.getHitRatio(),
            "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/admin/anomalies")
    public Map<String, Object> anomalyState() {
        return cacheService.anomalyState();
    }

    @PostMapping("/admin/anomalies")
    public Map<String, Object> configureAnomalies(
            @RequestParam(required = false) Boolean evictionEnabled,
            @RequestParam(required = false) Integer generationBurstMultiplier,
            @RequestParam(required = false) Boolean warningStormEnabled,
            @RequestParam(required = false) Boolean raceModeEnabled,
            @RequestParam(required = false) Boolean autoChaosEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean clearCache
    ) {
        if (evictionEnabled != null) {
            cacheService.setEvictionEnabled(evictionEnabled);
        }
        if (generationBurstMultiplier != null) {
            cacheService.setGenerationBurstMultiplier(generationBurstMultiplier);
        }
        if (warningStormEnabled != null) {
            cacheService.setWarningStormEnabled(warningStormEnabled);
        }
        if (raceModeEnabled != null) {
            cacheService.setRaceModeEnabled(raceModeEnabled);
        }
        if (autoChaosEnabled != null) {
            cacheService.setAutoChaosEnabled(autoChaosEnabled);
        }
        if (clearCache) {
            cacheService.clearCache();
        }
        return cacheService.anomalyState();
    }
}
""",
    "/tmp/sample-apps/worker-service/src/main/java/com/afsinb/worker/WorkerService.java": """package com.afsinb.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class WorkerService {

    private final List<String> memoryLeak = new ArrayList<>();
    private final Map<String, String> inFlight = new HashMap<>();

    private volatile int leakMultiplier = 1;
    private volatile long extraDelayMs = 0;
    private volatile boolean failureModeEnabled = false;
    private volatile int poisonBatchFailuresRemaining = 0;
    private volatile boolean autoChaosEnabled = true;

    private final Random random = new Random();

    private long jobsProcessed = 0;
    private long jobsFailed = 0;
    private long processingTime = 0;

    @Scheduled(fixedRate = 5000)
    public void processJobs() {
        try {
            long startTime = System.currentTimeMillis();
            int effectiveLeakMultiplier = Math.max(1, leakMultiplier);

            if (autoChaosEnabled && random.nextInt(100) < 35) {
                effectiveLeakMultiplier = Math.max(effectiveLeakMultiplier, 40);
                log.warn("Natural anomaly: leak spike enabled for this batch");
            }

            if (autoChaosEnabled && random.nextInt(100) < 12) {
                poisonBatchFailuresRemaining = Math.max(poisonBatchFailuresRemaining, 1);
            }

            if (poisonBatchFailuresRemaining > 0) {
                poisonBatchFailuresRemaining--;
                log.error("Unknown anomaly triggered: WorkerPoisonBatchException. Remaining={}",
                        poisonBatchFailuresRemaining);
                throw new WorkerPoisonBatchException("Poison batch payload detected in worker queue");
            }

            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < effectiveLeakMultiplier; j++) {
                    memoryLeak.add("Job_" + UUID.randomUUID());
                }

                inFlight.put("job-" + i, "RUNNING");

                if (i % 3 == 0) {
                    Thread.sleep(100 + extraDelayMs);
                }

                if (failureModeEnabled && i % 4 == 0) {
                    jobsFailed++;
                    log.error("Injected worker failure while processing batch item={}", i);
                }

                if (autoChaosEnabled && i == 5 && random.nextInt(100) < 15) {
                    Map<String, String> broken = null;
                    broken.get("npe");
                }

                jobsProcessed++;
            }

            if (autoChaosEnabled && random.nextInt(100) < 18) {
                runRaceProbe();
            }

            processingTime = System.currentTimeMillis() - startTime;
            log.info("Batch completed. Time={}ms, leakSize={}, jobsFailed={}",
                    processingTime, memoryLeak.size(), jobsFailed);

        } catch (Exception e) {
            jobsFailed++;
            log.error("Job processing failed", e);
        }
    }

    private void runRaceProbe() {
        try {
            for (String key : inFlight.keySet()) {
                if (key.endsWith("1")) {
                    inFlight.remove(key);
                }
            }
        } catch (ConcurrentModificationException e) {
            jobsFailed++;
            log.error("Race condition anomaly detected in worker inFlight map", e);
        }
    }

    public long getJobsProcessed() {
        return jobsProcessed;
    }

    public long getJobsFailed() {
        return jobsFailed;
    }

    public long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public long getLeakSize() {
        return memoryLeak.size();
    }

    public void setLeakMultiplier(int leakMultiplier) {
        this.leakMultiplier = Math.max(1, leakMultiplier);
        log.warn("Anomaly toggle changed: leakMultiplier={}", this.leakMultiplier);
    }

    public void setExtraDelayMs(long extraDelayMs) {
        this.extraDelayMs = Math.max(0, extraDelayMs);
        log.warn("Anomaly toggle changed: extraDelayMs={}", this.extraDelayMs);
    }

    public void setFailureModeEnabled(boolean failureModeEnabled) {
        this.failureModeEnabled = failureModeEnabled;
        log.warn("Anomaly toggle changed: failureModeEnabled={}", this.failureModeEnabled);
    }

    public void setAutoChaosEnabled(boolean enabled) {
        this.autoChaosEnabled = enabled;
        log.warn("Anomaly toggle changed: autoChaosEnabled={}", this.autoChaosEnabled);
    }

    public void injectPoisonBatchFailures(int count) {
        this.poisonBatchFailuresRemaining = Math.max(0, count);
        log.warn("Unknown anomaly failures scheduled: {}", this.poisonBatchFailuresRemaining);
    }

    public void clearLeak() {
        memoryLeak.clear();
        inFlight.clear();
        log.info("Worker memory buffers cleared");
    }

    public Map<String, Object> anomalyState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("leak_multiplier", leakMultiplier);
        state.put("extra_delay_ms", extraDelayMs);
        state.put("failure_mode_enabled", failureModeEnabled);
        state.put("poison_batch_failures_remaining", poisonBatchFailuresRemaining);
        state.put("auto_chaos_enabled", autoChaosEnabled);
        state.put("leak_size", memoryLeak.size());
        state.put("jobs_failed", jobsFailed);
        return state;
    }

    static class WorkerPoisonBatchException extends RuntimeException {
        WorkerPoisonBatchException(String message) {
            super(message);
        }
    }
}
""",
    "/tmp/sample-apps/worker-service/src/main/java/com/afsinb/worker/HealthController.java": """package com.afsinb.worker;

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

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "worker-service",
            "errors", workerService.getJobsFailed(),
            "jobs_processed", workerService.getJobsProcessed(),
            "jobs_failed", workerService.getJobsFailed(),
            "memory_mb", workerService.getMemoryUsage(),
            "cache_size", 0,
            "leak_size", workerService.getLeakSize(),
            "processing_time_ms", workerService.getProcessingTime()
        );
    }

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

    @GetMapping("/admin/anomalies")
    public Map<String, Object> anomalyState() {
        return workerService.anomalyState();
    }

    @PostMapping("/admin/anomalies")
    public Map<String, Object> configureAnomalies(
            @RequestParam(required = false) Integer leakMultiplier,
            @RequestParam(required = false) Long extraDelayMs,
            @RequestParam(required = false) Boolean failureModeEnabled,
            @RequestParam(required = false) Integer poisonBatchFailures,
            @RequestParam(required = false) Boolean autoChaosEnabled,
            @RequestParam(required = false, defaultValue = "false") boolean clearLeak
    ) {
        if (leakMultiplier != null) {
            workerService.setLeakMultiplier(leakMultiplier);
        }
        if (extraDelayMs != null) {
            workerService.setExtraDelayMs(extraDelayMs);
        }
        if (failureModeEnabled != null) {
            workerService.setFailureModeEnabled(failureModeEnabled);
        }
        if (poisonBatchFailures != null) {
            workerService.injectPoisonBatchFailures(poisonBatchFailures);
        }
        if (autoChaosEnabled != null) {
            workerService.setAutoChaosEnabled(autoChaosEnabled);
        }
        if (clearLeak) {
            workerService.clearLeak();
        }
        return workerService.anomalyState();
    }
}
""",
}


def main() -> int:
    for path, content in FILES.items():
        file_path = Path(path)
        if not file_path.parent.exists():
            raise FileNotFoundError(f"Missing parent directory: {file_path.parent}")
        file_path.write_text(content)
        print(f"updated: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

