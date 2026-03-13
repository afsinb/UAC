package com.blacklight.uac.demo;

import com.blacklight.uac.ui.SelfHealingDashboard;
import com.blacklight.uac.ui.SimpleDashboard;
import com.blacklight.uac.logmonitor.*;

import java.util.*;

/**
 * WorkingDemo - Comprehensive demo with 4 diverse systems, rich detailed flows and alarms
 */
public class WorkingDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║      SELF-HEALING SYSTEM - COMPREHENSIVE DEMO (4 SYSTEMS)      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        SelfHealingDashboard dataModel = new SelfHealingDashboard(8889);
        SimpleDashboard dashboard = new SimpleDashboard(dataModel, 8888);
        dashboard.start();

        System.out.println("✓ Dashboard initialized");
        System.out.println("✓ URL: http://localhost:8888\n");

        // Register 4 diverse systems
        dataModel.registerSystem("payment-service");
        dataModel.registerSystem("auth-service");
        dataModel.registerSystem("recommendation-engine");
        dataModel.registerSystem("admin-portal");

        // Each system has distinct health levels
        dataModel.systems.get("payment-service").healthScore = 0.68;
        dataModel.systems.get("auth-service").healthScore = 0.55;
        dataModel.systems.get("recommendation-engine").healthScore = 0.82;
        dataModel.systems.get("admin-portal").healthScore = 0.91;

        System.out.println("✓ 4 Systems Registered:\n");
        System.out.println("  • payment-service       (68%) – transaction processor, high-priority bugs");
        System.out.println("  • auth-service          (55%) – authentication, operational issues");
        System.out.println("  • recommendation-engine (82%) – ML service, data processing");
        System.out.println("  • admin-portal          (91%) – internal tool, stable\n");

        addPaymentServiceData(dataModel);
        addAuthServiceData(dataModel);
        addRecommendationEngineData(dataModel);
        addAdminPortalData(dataModel);

        System.out.println("\n✓ Sample flows and alarms loaded\n");
        System.out.println("✓ Total Flows Created: " + dataModel.allFlows.size());
        System.out.println("✓ Total Alarms Created: " + dataModel.alarms.size() + "\n");
        System.out.println("🚀 Open http://localhost:8888 in your browser");
        System.out.println("   • Select a system to view its flows and alarms");
        System.out.println("   • Click any FIX to see the complete healing flow with all steps");
        System.out.println("   • Click any ALARM to see its details and related fix\n");

        while (true) Thread.sleep(1000);
    }

    private static void addPaymentServiceData(SelfHealingDashboard dataModel) {
        SelfHealingDashboard.SystemMonitor sys = dataModel.systems.get("payment-service");
        if (sys == null) return;
        String id = sys.id;
        System.out.println("  Adding data for: payment-service (ID: " + id.substring(0, 8) + "...)");

        // FLOW 1 – CODE FIX: NullPointerException in transaction processor
        SelfHealingDashboard.HealingFlow f1 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f1.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f1.anomaly.anomalyType = "NULL_POINTER_EXCEPTION";
        f1.anomaly.severity = "CRITICAL";
        f1.anomaly.sourceFile = "TransactionProcessor.java";
        f1.anomaly.lineNumber = 287;
        f1.anomaly.message = "NullPointerException when processing refunds – customer object not loaded before accessing account";
        f1.anomaly.stackTrace = "java.lang.NullPointerException\n  at TransactionProcessor.processRefund(TransactionProcessor.java:287)\n  at RefundService.handleReturn(RefundService.java:142)\n  at PaymentController.postRefund(PaymentController.java:89)";
        addFlowStep(f1, "SIGNAL", "NullPointerException detected in refund flow", "TelemetryMCP", "anomalyCount", "143 in last 1hr");
        addFlowStep(f1, "CONTEXT", "Code analyzed: customer object nullable at line 287", "CodeAnalysisMCP", "codeBlame", "Commit abc1234 (2 days ago)");
        addFlowStep(f1, "HYPOTHESIS", "AI recommendation: Add null safety check", "DynamicAI", "aiModel", "RuleBasedModel (confidence 0.94)");
        addFlowStep(f1, "EXECUTION", "Patch applied: add null guard + fallback to guest", "DevelopmentMCP", "prUrl", "github.com/payment/pr/5421");
        addFlowStep(f1, "VALIDATION", "Sandbox: 50k refunds processed, 0 NPE, 99.8% success", "TelemetryMCP", "testResult", "PASS");
        f1.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f1);

        // FLOW 2 – OPERATIONAL: High memory on payment pods
        SelfHealingDashboard.HealingFlow f2 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f2.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f2.anomaly.anomalyType = "HIGH_MEMORY_USAGE";
        f2.anomaly.severity = "HIGH";
        f2.anomaly.message = "Memory trending up on payment-prod-1: 2.1/2.5 GB, GC unable to reclaim old sessions";
        addFlowStep(f2, "SIGNAL", "Memory alert: payment-prod-1 at 84% capacity", "TelemetryMCP", "memPercent", "84%");
        addFlowStep(f2, "CONTEXT", "Heap dump shows 1.2 GB in old sessions, duplicate GUIDs in pool", "CodeAnalysisMCP", "poolStatus", "1847 idle sessions");
        addFlowStep(f2, "HYPOTHESIS", "Session pool not cleaning up: likely leak in SessionManager", "DynamicAI", "hypothesis", "OPERATIONAL");
        addFlowStep(f2, "EXECUTION", "Action: Rolling restart payment pods + clear old sessions", "HealingMCP", "action", "RESTART + CLEAR_POOL");
        addFlowStep(f2, "VALIDATION", "Mem dropped to 640 MB after restart, GC working normally", "TelemetryMCP", "memAfter", "640 MB");
        f2.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f2);

        // FLOW 3 – CODE FIX: Division by zero in fee calculation
        SelfHealingDashboard.HealingFlow f3 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f3.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f3.anomaly.anomalyType = "ARITHMETIC_EXCEPTION";
        f3.anomaly.severity = "HIGH";
        f3.anomaly.sourceFile = "FeeCalculator.java";
        f3.anomaly.lineNumber = 156;
        f3.anomaly.message = "Division by zero in fee calculation when merchant has 0% markup";
        addFlowStep(f3, "SIGNAL", "ArithmeticException: / by zero detected", "TelemetryMCP", "errorCount", "287 errors/min");
        addFlowStep(f3, "CONTEXT", "Triggered by new batch import: 12k merchants with 0% markup", "CodeAnalysisMCP", "rootCause", "Missing bounds check");
        addFlowStep(f3, "HYPOTHESIS", "Edge case: markup threshold should have min value 0.01%", "DynamicAI", "suggestion", "HeuristicModel");
        addFlowStep(f3, "EXECUTION", "Code: add markup min bound check before division", "DevelopmentMCP", "prId", "PR-5422 - Line 156");
        addFlowStep(f3, "VALIDATION", "Import rerun: 12k merchants processed, 0 errors, revenue correct", "TelemetryMCP", "importStatus", "SUCCESS");
        f3.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f3);

        // FLOW 4 – OPERATIONAL: Circuit breaker on payment gateway
        SelfHealingDashboard.HealingFlow f4 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f4.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f4.anomaly.anomalyType = "CIRCUIT_BREAKER_OPEN";
        f4.anomaly.severity = "CRITICAL";
        f4.anomaly.message = "Stripe API circuit breaker OPEN: 52% timeout rate, payment processing blocked";
        addFlowStep(f4, "SIGNAL", "Circuit breaker opened for Stripe integration", "TelemetryMCP", "timeoutRate", "52%");
        addFlowStep(f4, "CONTEXT", "Stripe API v1 sunset detected - requests now 2-3s slower", "CodeAnalysisMCP", "apiVersion", "v1 → v2 migration");
        addFlowStep(f4, "HYPOTHESIS", "Gateway timeout too strict: v2 API is slower, adjust threshold", "DynamicAI", "fix", "CONFIGURATION");
        addFlowStep(f4, "EXECUTION", "Updated Stripe timeout 500ms→2000ms, switched to v2 endpoint", "HealingMCP", "configChange", "stripe.timeout");
        addFlowStep(f4, "VALIDATION", "New p99: 1.8s, success rate recovered to 99.6%", "TelemetryMCP", "successRate", "99.6%");
        f4.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f4);

        // FLOW 5 – CODE FIX: Currency conversion rounding error
        SelfHealingDashboard.HealingFlow f5 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f5.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f5.anomaly.anomalyType = "PRECISION_LOSS";
        f5.anomaly.severity = "CRITICAL";
        f5.anomaly.sourceFile = "CurrencyConverter.java";
        f5.anomaly.lineNumber = 94;
        f5.anomaly.message = "Float used for currency conversion: GBP→JPY loses precision, $100 → ¥13,123.5 instead of ¥13,123.45";
        addFlowStep(f5, "SIGNAL", "Audit: 847 transactions with <1 JPY rounding errors", "TelemetryMCP", "auditCount", "847 affected");
        addFlowStep(f5, "CONTEXT", "Root cause: float precision limit, need BigDecimal", "CodeAnalysisMCP", "typeUsed", "float → BigDecimal");
        addFlowStep(f5, "HYPOTHESIS", "Finance domain requires arbitrary precision decimals", "DynamicAI", "solution", "Use BigDecimal");
        addFlowStep(f5, "EXECUTION", "Refactored: float → BigDecimal, added test for 100s of rates", "DevelopmentMCP", "testCoverage", "126 new tests");
        addFlowStep(f5, "VALIDATION", "All historical transactions re-calculated correctly, 0 precision loss", "TelemetryMCP", "result", "AUDIT_PASS");
        f5.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f5);

        // FLOW 6 – OPERATIONAL: Database connection pool exhaustion
        SelfHealingDashboard.HealingFlow f6 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f6.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f6.anomaly.anomalyType = "CONNECTION_POOL_EXHAUSTED";
        f6.anomaly.severity = "CRITICAL";
        f6.anomaly.message = "DB connection pool maxed out: 200/200 connections held, new transactions timeout";
        addFlowStep(f6, "SIGNAL", "Alert: connection pool at capacity, 1240 waiting requests", "TelemetryMCP", "queuedRequests", "1240");
        addFlowStep(f6, "CONTEXT", "Batch job running long queries: avg 28s each, holding connections", "CodeAnalysisMCP", "query", "SELECT * WHERE date > X (O(n) scan)");
        addFlowStep(f6, "HYPOTHESIS", "Unindexed query causing long TX hold times, pool starvation", "DynamicAI", "diagnosis", "INDEX_MISSING");
        addFlowStep(f6, "EXECUTION", "Added index on date column, increased pool to 250, killed long TXs", "HealingMCP", "actions", "INDEX + POOL_SCALE");
        addFlowStep(f6, "VALIDATION", "Queries now 140ms avg, pool 60/250, queue empty, throughput +340%", "TelemetryMCP", "throughput", "+340%");
        f6.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f6);

        // FLOW 7 – CODE FIX: Concurrency bug in transaction lock
        SelfHealingDashboard.HealingFlow f7 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f7.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f7.anomaly.anomalyType = "RACE_CONDITION";
        f7.anomaly.severity = "CRITICAL";
        f7.anomaly.sourceFile = "TransactionLock.java";
        f7.anomaly.lineNumber = 203;
        f7.anomaly.message = "Double-charge detected: 2 threads process same transaction simultaneously, bypassing duplicate check";
        addFlowStep(f7, "SIGNAL", "Financial anomaly: 34 duplicate charges detected (same TX ID)", "TelemetryMCP", "duplicates", "34 txs");
        addFlowStep(f7, "CONTEXT", "Root cause: lock released before idempotency key check completes", "CodeAnalysisMCP", "raceWindow", "12ms window");
        addFlowStep(f7, "HYPOTHESIS", "Lock must extend to idempotency verification step", "DynamicAI", "fix", "PatternMatchingModel");
        addFlowStep(f7, "EXECUTION", "Synchronized idempotency check with transaction lock, added TX journal log", "DevelopmentMCP", "prId", "PR-5423 - Critical");
        addFlowStep(f7, "VALIDATION", "Chaos test: 100k concurrent TXs, 0 duplicates, idempotency verified", "TelemetryMCP", "chaosTest", "PASS");
        f7.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f7);

        // ALARMS (10 diverse alarms)
        SelfHealingDashboard.Alarm a1 = new SelfHealingDashboard.Alarm(id, "ANOMALY_DETECTED", "CRITICAL",
                "NullPointerException in refund flow – customers unable to process returns");
        a1.relatedFlowId = f1.id;
        dataModel.addAlarm(a1);

        SelfHealingDashboard.Alarm a2 = new SelfHealingDashboard.Alarm(id, "MEMORY_PRESSURE", "HIGH",
                "payment-prod-1 memory at 84% – session pool leak suspected");
        a2.relatedFlowId = f2.id;
        dataModel.addAlarm(a2);

        SelfHealingDashboard.Alarm a3 = new SelfHealingDashboard.Alarm(id, "BATCH_FAILURE", "HIGH",
                "Merchant import failed: 12k records, 287 errors/min – fee calculation bug");
        a3.relatedFlowId = f3.id;
        dataModel.addAlarm(a3);

        SelfHealingDashboard.Alarm a4 = new SelfHealingDashboard.Alarm(id, "PAYMENT_GATEWAY_DOWN", "CRITICAL",
                "Stripe API timeout: 52% failure rate, checkout blocked");
        a4.relatedFlowId = f4.id;
        dataModel.addAlarm(a4);

        SelfHealingDashboard.Alarm a5 = new SelfHealingDashboard.Alarm(id, "FINANCIAL_AUDIT", "CRITICAL",
                "847 currency conversion errors detected – precision loss in forex calculations");
        a5.relatedFlowId = f5.id;
        dataModel.addAlarm(a5);

        SelfHealingDashboard.Alarm a6 = new SelfHealingDashboard.Alarm(id, "CONNECTION_POOL_EXHAUSTED", "CRITICAL",
                "DB connection pool maxed – 1240 transactions queued, response time degraded");
        a6.relatedFlowId = f6.id;
        dataModel.addAlarm(a6);

        SelfHealingDashboard.Alarm a7 = new SelfHealingDashboard.Alarm(id, "DOUBLE_CHARGE_DETECTED", "CRITICAL",
                "Race condition: 34 duplicate charges identified – same transaction processed twice");
        a7.relatedFlowId = f7.id;
        dataModel.addAlarm(a7);

        SelfHealingDashboard.Alarm a8 = new SelfHealingDashboard.Alarm(id, "RECOVERY_IN_PROGRESS", "HIGH",
                "Applying automatic fixes: 7 flows running – refund NullPointerException, memory leak, etc.");
        dataModel.addAlarm(a8);

        SelfHealingDashboard.Alarm a9 = new SelfHealingDashboard.Alarm(id, "RECONCILIATION", "MEDIUM",
                "Disputed transaction count: $3,247 in potential chargebacks – investigating");
        dataModel.addAlarm(a9);

        SelfHealingDashboard.Alarm a10 = new SelfHealingDashboard.Alarm(id, "RECOVERY_SUCCESS", "INFO",
                "6/7 fixes deployed: payment health recovered to 68% – one fix pending review");
        dataModel.addAlarm(a10);

        System.out.println("    ✓ Added 7 flows and 10 alarms");
    }

    private static void addAuthServiceData(SelfHealingDashboard dataModel) {
        SelfHealingDashboard.SystemMonitor sys = dataModel.systems.get("auth-service");
        if (sys == null) return;
        String id = sys.id;
        System.out.println("  Adding data for: auth-service (ID: " + id.substring(0, 8) + "...)");

        // FLOW 1 – CODE FIX: Expired tokens not rejected
        SelfHealingDashboard.HealingFlow f1 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f1.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f1.anomaly.anomalyType = "SECURITY_VULNERABILITY";
        f1.anomaly.severity = "CRITICAL";
        f1.anomaly.sourceFile = "TokenValidator.java";
        f1.anomaly.lineNumber = 178;
        f1.anomaly.message = "Expired JWT tokens accepted due to missing expiry check – users stay logged in indefinitely";
        addFlowStep(f1, "SIGNAL", "Security scan: 18% of active tokens are expired", "TelemetryMCP", "expiredTokens", "18%");
        addFlowStep(f1, "CONTEXT", "Code review: expiry check commented out in TokenValidator line 178", "CodeAnalysisMCP", "rootCause", "TODO_NEVER_REMOVED");
        addFlowStep(f1, "HYPOTHESIS", "Uncomment expiry check, add validation test for expired tokens", "DynamicAI", "fix", "RuleBasedModel");
        addFlowStep(f1, "EXECUTION", "Reactivated expiry validation, added 24 test cases for edge cases", "DevelopmentMCP", "tests", "24 new + 12 updated");
        addFlowStep(f1, "VALIDATION", "All expired tokens now rejected within 50ms, security audit pass", "TelemetryMCP", "auditResult", "PASS");
        f1.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f1);

        // FLOW 2 – OPERATIONAL: CPU spike during token refresh storm
        SelfHealingDashboard.HealingFlow f2 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f2.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f2.anomaly.anomalyType = "HIGH_CPU_USAGE";
        f2.anomaly.severity = "HIGH";
        f2.anomaly.message = "CPU on auth-prod spiked to 91% during token refresh window – 12k/sec refresh rate";
        addFlowStep(f2, "SIGNAL", "CPU threshold exceeded: 91% on auth-prod for 14 minutes", "TelemetryMCP", "cpuUsage", "91%");
        addFlowStep(f2, "CONTEXT", "Correlated: 12k refresh tokens/sec, refresh endpoint not optimized", "CodeAnalysisMCP", "bottleneck", "Refresh cache miss");
        addFlowStep(f2, "HYPOTHESIS", "Operational: add caching layer to refresh token responses", "DynamicAI", "solution", "CACHE");
        addFlowStep(f2, "EXECUTION", "Deployed Redis-backed refresh token cache (TTL: 30s), 10 replicas", "HealingMCP", "action", "DEPLOY_CACHE");
        addFlowStep(f2, "VALIDATION", "CPU dropped to 34%, cache hit rate 87%, refresh latency 10ms→2ms", "TelemetryMCP", "perf", "10ms→2ms");
        f2.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f2);

        // FLOW 3 – CODE FIX: LDAP injection in username validation
        SelfHealingDashboard.HealingFlow f3 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f3.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f3.anomaly.anomalyType = "INJECTION_VULNERABILITY";
        f3.anomaly.severity = "CRITICAL";
        f3.anomaly.sourceFile = "LDAPAuthenticator.java";
        f3.anomaly.lineNumber = 142;
        f3.anomaly.message = "LDAP injection vulnerability: username not escaped before LDAP query – attacker can bypass auth";
        addFlowStep(f3, "SIGNAL", "Penetration test: LDAP injection successful, auth bypassed", "TelemetryMCP", "vulnSeverity", "CRITICAL");
        addFlowStep(f3, "CONTEXT", "Username directly interpolated into LDAP filter without escaping", "CodeAnalysisMCP", "vulnerability", "CWE-90");
        addFlowStep(f3, "HYPOTHESIS", "Use LDAPUtils.escapeFilter() for all user inputs", "DynamicAI", "fix", "SecurityModel");
        addFlowStep(f3, "EXECUTION", "Applied LDAP escaping to all user inputs, added input validation", "DevelopmentMCP", "securityUpdate", "PR-6001");
        addFlowStep(f3, "VALIDATION", "Pentest round 2: injection attack fails, LDAP login still works", "TelemetryMCP", "pentestResult", "FAIL_OK");
        f3.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f3);

        // FLOW 4 – OPERATIONAL: Excessive password reset emails
        SelfHealingDashboard.HealingFlow f4 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f4.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f4.anomaly.anomalyType = "RATE_LIMIT_EXCEEDED";
        f4.anomaly.severity = "MEDIUM";
        f4.anomaly.message = "User receiving 47 password reset emails in 5 minutes – no rate limiting on reset endpoint";
        addFlowStep(f4, "SIGNAL", "Alert: 47 reset emails to same user in 5 min, potential abuse", "TelemetryMCP", "emailCount", "47");
        addFlowStep(f4, "CONTEXT", "No rate limit on /auth/reset endpoint, attacker using API directly", "CodeAnalysisMCP", "rateLimit", "NONE");
        addFlowStep(f4, "HYPOTHESIS", "Add IP-based rate limiting: 3 requests per 5 min per IP", "DynamicAI", "mitigate", "RateLimitModel");
        addFlowStep(f4, "EXECUTION", "Deployed rate limiter: 3/5min per IP, 5/hour per email address", "HealingMCP", "limits", "IP:3/5m, EMAIL:5/h");
        addFlowStep(f4, "VALIDATION", "Test: single IP limited correctly, spam attack blocked, legit users unaffected", "TelemetryMCP", "testResult", "PASS");
        f4.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f4);

        // FLOW 5 – CODE FIX: Weak password hashing algorithm
        SelfHealingDashboard.HealingFlow f5 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f5.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f5.anomaly.anomalyType = "WEAK_CRYPTOGRAPHY";
        f5.anomaly.severity = "HIGH";
        f5.anomaly.sourceFile = "PasswordHasher.java";
        f5.anomaly.lineNumber = 67;
        f5.anomaly.message = "MD5 used for password hashing (deprecated since 1996) – 5.2M user passwords at risk";
        addFlowStep(f5, "SIGNAL", "Security audit: 5.2M passwords hashed with MD5", "TelemetryMCP", "affectedUsers", "5.2M");
        addFlowStep(f5, "CONTEXT", "Legacy code: MD5 in PasswordHasher.java – database has no salt", "CodeAnalysisMCP", "cryptoUsed", "MD5 no salt");
        addFlowStep(f5, "HYPOTHESIS", "Migrate to bcrypt with adaptive cost factor (12)", "DynamicAI", "solution", "CryptoModel");
        addFlowStep(f5, "EXECUTION", "Migration: old MD5 → bcrypt, trigger re-hash on next login for 5.2M", "DevelopmentMCP", "migration", "2-phase");
        addFlowStep(f5, "VALIDATION", "2M users re-hashed, all new passwords use bcrypt, audit pass", "TelemetryMCP", "migrationStatus", "2M/5.2M");
        f5.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f5);

        // FLOW 6 – OPERATIONAL: Cascading auth service outage
        SelfHealingDashboard.HealingFlow f6 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f6.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f6.anomaly.anomalyType = "SERVICE_UNAVAILABLE";
        f6.anomaly.severity = "CRITICAL";
        f6.anomaly.message = "All 5 auth-service pods crashed due to database connection timeout – recovery in progress";
        addFlowStep(f6, "SIGNAL", "Alert: 5/5 auth pods down, 100% authentication failure", "TelemetryMCP", "podsDown", "5/5");
        addFlowStep(f6, "CONTEXT", "Root cause: database restart killed all existing connections", "CodeAnalysisMCP", "dbEvent", "DB_RESTART");
        addFlowStep(f6, "HYPOTHESIS", "DB connection pool not recovering after DB restart", "DynamicAI", "issue", "CONNECTION_POOL");
        addFlowStep(f6, "EXECUTION", "Force restart all auth pods, increased DB connection timeout 30s→60s", "HealingMCP", "recovery", "POD_RESTART");
        addFlowStep(f6, "VALIDATION", "All 5 pods healthy, connections reestablished, auth restored in 45s", "TelemetryMCP", "recoveryTime", "45s");
        f6.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f6);

        // ALARMS (8 diverse alarms)
        SelfHealingDashboard.Alarm a1 = new SelfHealingDashboard.Alarm(id, "SECURITY_ISSUE", "CRITICAL",
                "Expired JWT tokens accepted – 18% of sessions have expired but still active");
        a1.relatedFlowId = f1.id;
        dataModel.addAlarm(a1);

        SelfHealingDashboard.Alarm a2 = new SelfHealingDashboard.Alarm(id, "HIGH_CPU", "HIGH",
                "CPU spike to 91% during refresh token storm – 12k/sec refresh rate detected");
        a2.relatedFlowId = f2.id;
        dataModel.addAlarm(a2);

        SelfHealingDashboard.Alarm a3 = new SelfHealingDashboard.Alarm(id, "PENETRATION_TEST_FAILED", "CRITICAL",
                "LDAP injection vulnerability confirmed – attacker can bypass authentication");
        a3.relatedFlowId = f3.id;
        dataModel.addAlarm(a3);

        SelfHealingDashboard.Alarm a4 = new SelfHealingDashboard.Alarm(id, "ABUSE_DETECTED", "MEDIUM",
                "47 password reset emails sent to user in 5 minutes – no rate limiting");
        a4.relatedFlowId = f4.id;
        dataModel.addAlarm(a4);

        SelfHealingDashboard.Alarm a5 = new SelfHealingDashboard.Alarm(id, "WEAK_CRYPTOGRAPHY", "HIGH",
                "5.2M user passwords stored with MD5 (no salt) – compliance violation");
        a5.relatedFlowId = f5.id;
        dataModel.addAlarm(a5);

        SelfHealingDashboard.Alarm a6 = new SelfHealingDashboard.Alarm(id, "SERVICE_OUTAGE", "CRITICAL",
                "All auth service pods down – authentication unavailable, incident mitigation active");
        a6.relatedFlowId = f6.id;
        dataModel.addAlarm(a6);

        SelfHealingDashboard.Alarm a7 = new SelfHealingDashboard.Alarm(id, "SECURITY_UPDATE", "HIGH",
                "Security fixes deployed: token validation, LDAP escaping, bcrypt migration (2M/5.2M)");
        dataModel.addAlarm(a7);

        SelfHealingDashboard.Alarm a8 = new SelfHealingDashboard.Alarm(id, "RECOVERY_SUCCESS", "INFO",
                "Auth service health recovered to 55% – 6/6 critical fixes deployed, monitoring...");
        dataModel.addAlarm(a8);

        System.out.println("    ✓ Added 6 flows and 8 alarms");
    }

    private static void addRecommendationEngineData(SelfHealingDashboard dataModel) {
        SelfHealingDashboard.SystemMonitor sys = dataModel.systems.get("recommendation-engine");
        if (sys == null) return;
        String id = sys.id;
        System.out.println("  Adding data for: recommendation-engine (ID: " + id.substring(0, 8) + "...)");

        // FLOW 1 – CODE FIX: NaN propagation in ML scoring
        SelfHealingDashboard.HealingFlow f1 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f1.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f1.anomaly.anomalyType = "INVALID_FLOAT_VALUE";
        f1.anomaly.severity = "HIGH";
        f1.anomaly.sourceFile = "MLScorer.java";
        f1.anomaly.lineNumber = 312;
        f1.anomaly.message = "NaN propagates through scoring pipeline – recommendations return NaN scores, top results are garbage";
        addFlowStep(f1, "SIGNAL", "ML pipeline alert: 3.2% of scores are NaN", "TelemetryMCP", "nanPercent", "3.2%");
        addFlowStep(f1, "CONTEXT", "Root: divide by zero in feature normalization when variance=0", "CodeAnalysisMCP", "bugLocation", "Variance calc");
        addFlowStep(f1, "HYPOTHESIS", "Add epsilon to variance to prevent division by zero", "DynamicAI", "fix", "PatternMatchingModel");
        addFlowStep(f1, "EXECUTION", "Added epsilon=1e-10 to variance calculation, handle NaN → default score", "DevelopmentMCP", "prId", "PR-7001");
        addFlowStep(f1, "VALIDATION", "ML test suite: 0% NaN scores, recommendation quality restored", "TelemetryMCP", "testResult", "100% valid");
        f1.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f1);

        // FLOW 2 – OPERATIONAL: Model inference latency spike
        SelfHealingDashboard.HealingFlow f2 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f2.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f2.anomaly.anomalyType = "LATENCY_DEGRADATION";
        f2.anomaly.severity = "HIGH";
        f2.anomaly.message = "Model inference latency: 450ms → 2800ms, SLA miss 89% of requests";
        addFlowStep(f2, "SIGNAL", "Latency alert: p99 inference time 2.8s (SLA: 500ms)", "TelemetryMCP", "latency", "2800ms");
        addFlowStep(f2, "CONTEXT", "Model checkpoint loaded: v3 model 8x larger than v2, no GPU acceleration", "CodeAnalysisMCP", "bottleneck", "Model size");
        addFlowStep(f2, "HYPOTHESIS", "Operational: enable GPU inference, add request batching", "DynamicAI", "solution", "SCALE");
        addFlowStep(f2, "EXECUTION", "Deployed GPU inference pods (4x A100), batch size 32, v3 quantized", "HealingMCP", "action", "GPU_SCALE");
        addFlowStep(f2, "VALIDATION", "Inference: 2800ms → 145ms, GPU util 72%, SLA compliance 99.8%", "TelemetryMCP", "perfGain", "19.3x");
        f2.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f2);

        // FLOW 3 – CODE FIX: Off-by-one in ranking algorithm
        SelfHealingDashboard.HealingFlow f3 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f3.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f3.anomaly.anomalyType = "LOGIC_ERROR";
        f3.anomaly.severity = "MEDIUM";
        f3.anomaly.sourceFile = "RankingAlgorithm.java";
        f3.anomaly.lineNumber = 189;
        f3.anomaly.message = "Off-by-one error: rank 50 item returned as rank 49, top-10 results contaminated";
        addFlowStep(f3, "SIGNAL", "Ranking validation: top-50 contamination detected", "TelemetryMCP", "contaminationRate", "8%");
        addFlowStep(f3, "CONTEXT", "Loop condition: i <= n instead of i < n, boundary check fail", "CodeAnalysisMCP", "codeIssue", "Loop boundary");
        addFlowStep(f3, "HYPOTHESIS", "Fix loop: i <= n → i < n, add boundary unit tests", "DynamicAI", "fix", "RuleBasedModel");
        addFlowStep(f3, "EXECUTION", "Fixed boundary condition, added 48 ranking edge case tests", "DevelopmentMCP", "tests", "48 new tests");
        addFlowStep(f3, "VALIDATION", "Ranking accuracy restored: 99.7%, all top-10 items verified correct", "TelemetryMCP", "accuracy", "99.7%");
        f3.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f3);

        // FLOW 4 – OPERATIONAL: Memory leak in cache eviction
        SelfHealingDashboard.HealingFlow f4 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f4.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f4.anomaly.anomalyType = "MEMORY_LEAK";
        f4.anomaly.severity = "HIGH";
        f4.anomaly.message = "Cache memory grows unbounded: 1.2GB → 3.6GB in 48 hours, GC unable to reclaim";
        addFlowStep(f4, "SIGNAL", "OOM risk: cache memory 3.6/4GB, trending up +15MB/hr", "TelemetryMCP", "memUsage", "3.6GB/4GB");
        addFlowStep(f4, "CONTEXT", "Cache eviction policy broken: LRU not removing old items", "CodeAnalysisMCP", "policy", "LRU_BROKEN");
        addFlowStep(f4, "HYPOTHESIS", "Cache eviction callback not triggering, old entries held", "DynamicAI", "cause", "CALLBACK");
        addFlowStep(f4, "EXECUTION", "Refactored eviction callback, manual clear of 60% stale entries", "HealingMCP", "action", "CACHE_CLEAR");
        addFlowStep(f4, "VALIDATION", "Cache memory: 3.6GB → 680MB, eviction rate nominal 2.1MB/min", "TelemetryMCP", "result", "STABLE");
        f4.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f4);

        // ALARMS (6 diverse alarms)
        SelfHealingDashboard.Alarm a1 = new SelfHealingDashboard.Alarm(id, "ML_ANOMALY", "HIGH",
                "NaN scores detected: 3.2% of recommendations have invalid scores");
        a1.relatedFlowId = f1.id;
        dataModel.addAlarm(a1);

        SelfHealingDashboard.Alarm a2 = new SelfHealingDashboard.Alarm(id, "LATENCY_SLA_MISS", "HIGH",
                "Model inference SLA breached: p99 latency 2.8s vs 500ms target (89% miss rate)");
        a2.relatedFlowId = f2.id;
        dataModel.addAlarm(a2);

        SelfHealingDashboard.Alarm a3 = new SelfHealingDashboard.Alarm(id, "RANKING_CONTAMINATION", "MEDIUM",
                "Top-50 ranking results contaminated due to boundary condition bug");
        a3.relatedFlowId = f3.id;
        dataModel.addAlarm(a3);

        SelfHealingDashboard.Alarm a4 = new SelfHealingDashboard.Alarm(id, "MEMORY_LEAK", "HIGH",
                "Cache memory leak: 3.6GB/4GB capacity, trending up 15MB/hr – OOM risk in 6 hours");
        a4.relatedFlowId = f4.id;
        dataModel.addAlarm(a4);

        SelfHealingDashboard.Alarm a5 = new SelfHealingDashboard.Alarm(id, "RECOVERY_IN_PROGRESS", "MEDIUM",
                "Applying fixes: NaN handling, GPU inference scaling, ranking correction");
        dataModel.addAlarm(a5);

        SelfHealingDashboard.Alarm a6 = new SelfHealingDashboard.Alarm(id, "RECOVERY_SUCCESS", "INFO",
                "Recommendation engine health improved to 82% – all 4 flows deployed successfully");
        dataModel.addAlarm(a6);

        System.out.println("    ✓ Added 4 flows and 6 alarms");
    }

    private static void addAdminPortalData(SelfHealingDashboard dataModel) {
        SelfHealingDashboard.SystemMonitor sys = dataModel.systems.get("admin-portal");
        if (sys == null) return;
        String id = sys.id;
        System.out.println("  Adding data for: admin-portal (ID: " + id.substring(0, 8) + "...)");

        // FLOW 1 – CODE FIX: XSS vulnerability in user display
        SelfHealingDashboard.HealingFlow f1 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f1.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f1.anomaly.anomalyType = "XSS_VULNERABILITY";
        f1.anomaly.severity = "HIGH";
        f1.anomaly.sourceFile = "AdminUserView.java";
        f1.anomaly.lineNumber = 234;
        f1.anomaly.message = "HTML not escaped in user display – admin can inject JavaScript via user profile";
        addFlowStep(f1, "SIGNAL", "Security scan: XSS vector found in user display endpoint", "TelemetryMCP", "vulnType", "Stored XSS");
        addFlowStep(f1, "CONTEXT", "User input directly rendered in HTML without escaping", "CodeAnalysisMCP", "location", "AdminUserView:234");
        addFlowStep(f1, "HYPOTHESIS", "Apply HTML escaping to all user-controlled output", "DynamicAI", "fix", "SecurityModel");
        addFlowStep(f1, "EXECUTION", "Applied OWASP HTML encoding, added CSP header", "DevelopmentMCP", "secFix", "PR-8001");
        addFlowStep(f1, "VALIDATION", "Pentest: XSS payload neutralized, admin panel functional", "TelemetryMCP", "testResult", "PASS");
        f1.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f1);

        // FLOW 2 – OPERATIONAL: Scheduled report generation timeout
        SelfHealingDashboard.HealingFlow f2 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f2.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f2.anomaly.anomalyType = "TIMEOUT";
        f2.anomaly.severity = "MEDIUM";
        f2.anomaly.message = "Monthly admin report generation timing out after 45 min – dataset grew 10x";
        addFlowStep(f2, "SIGNAL", "Report job timeout: 45min → 2 hours needed", "TelemetryMCP", "timeout", "45min");
        addFlowStep(f2, "CONTEXT", "Dataset size: 2M records → 20M records, report query O(n²)", "CodeAnalysisMCP", "complexity", "O(n²)");
        addFlowStep(f2, "HYPOTHESIS", "Refactor report query: add indexes, use streaming", "DynamicAI", "optimize", "QUERY");
        addFlowStep(f2, "EXECUTION", "Added date index, converted to streaming aggregation", "HealingMCP", "optimization", "INDEX + STREAM");
        addFlowStep(f2, "VALIDATION", "Report time: 2hr → 8min for 20M records, completes by deadline", "TelemetryMCP", "perfGain", "15x");
        f2.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f2);

        // FLOW 3 – CODE FIX: CSRF token validation bypass
        SelfHealingDashboard.HealingFlow f3 = new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
        f3.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f3.anomaly.anomalyType = "CSRF_VULNERABILITY";
        f3.anomaly.severity = "HIGH";
        f3.anomaly.sourceFile = "AdminActionController.java";
        f3.anomaly.lineNumber = 156;
        f3.anomaly.message = "CSRF token validation disabled in production (TODO flag left on)";
        addFlowStep(f3, "SIGNAL", "Code review: CSRF protection disabled for admin actions", "TelemetryMCP", "vulnSeverity", "HIGH");
        addFlowStep(f3, "CONTEXT", "TODO flag in production code: csrfEnabled = false", "CodeAnalysisMCP", "flag", "CSRF_DISABLED");
        addFlowStep(f3, "HYPOTHESIS", "Enable CSRF validation, remove TODO flag", "DynamicAI", "fix", "RuleBasedModel");
        addFlowStep(f3, "EXECUTION", "Enabled CSRF validation, added token rotation on each request", "DevelopmentMCP", "secFix", "PR-8002");
        addFlowStep(f3, "VALIDATION", "Admin actions require valid CSRF token, compliance audit pass", "TelemetryMCP", "auditResult", "PASS");
        f3.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f3);

        // FLOW 4 – OPERATIONAL: Slow backup process
        SelfHealingDashboard.HealingFlow f4 = new SelfHealingDashboard.HealingFlow(id, "OPERATIONAL_FIX");
        f4.anomaly = new SelfHealingDashboard.AnomalyDetails();
        f4.anomaly.anomalyType = "SLOW_BACKUP";
        f4.anomaly.severity = "MEDIUM";
        f4.anomaly.message = "Daily backup window: 8 hours needed, only 6-hour window available";
        addFlowStep(f4, "SIGNAL", "Backup alert: 8 hours needed but only 6-hour window", "TelemetryMCP", "backupTime", "8hrs");
        addFlowStep(f4, "CONTEXT", "Backup parallelization: sequential dump of all tables", "CodeAnalysisMCP", "process", "SEQUENTIAL");
        addFlowStep(f4, "HYPOTHESIS", "Operational: parallelize backup for 8 tables", "DynamicAI", "optimize", "PARALLEL");
        addFlowStep(f4, "EXECUTION", "Parallel backup: 8 tables in 8 threads, incremental snapshots", "HealingMCP", "config", "PARALLEL_8");
        addFlowStep(f4, "VALIDATION", "Backup time: 8hr → 1.2hr, fits in maintenance window", "TelemetryMCP", "result", "1.2hr");
        f4.status = SelfHealingDashboard.FlowStatus.VALIDATION_COMPLETE;
        dataModel.addHealingFlow(f4);

        // ALARMS (5 diverse alarms)
        SelfHealingDashboard.Alarm a1 = new SelfHealingDashboard.Alarm(id, "SECURITY_ISSUE", "HIGH",
                "XSS vulnerability discovered in admin user display panel");
        a1.relatedFlowId = f1.id;
        dataModel.addAlarm(a1);

        SelfHealingDashboard.Alarm a2 = new SelfHealingDashboard.Alarm(id, "TIMEOUT", "MEDIUM",
                "Monthly report generation exceeds backup window (45min timeout vs 6hr window)");
        a2.relatedFlowId = f2.id;
        dataModel.addAlarm(a2);

        SelfHealingDashboard.Alarm a3 = new SelfHealingDashboard.Alarm(id, "COMPLIANCE_FAILURE", "HIGH",
                "CSRF protection disabled in AdminActionController – compliance violation");
        a3.relatedFlowId = f3.id;
        dataModel.addAlarm(a3);

        SelfHealingDashboard.Alarm a4 = new SelfHealingDashboard.Alarm(id, "BACKUP_FAILURE", "MEDIUM",
                "Backup process exceeds maintenance window – data backup at risk");
        a4.relatedFlowId = f4.id;
        dataModel.addAlarm(a4);

        SelfHealingDashboard.Alarm a5 = new SelfHealingDashboard.Alarm(id, "RECOVERY_SUCCESS", "INFO",
                "Admin portal health at 91% – all 4 security and performance fixes deployed");
        dataModel.addAlarm(a5);

        System.out.println("    ✓ Added 4 flows and 5 alarms");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Shared helper
    // ──────────────────────────────────────────────────────────────────
    private static void addFlowStep(SelfHealingDashboard.HealingFlow flow, String phase, String action,
                                    String mcp, String detailKey, Object detailValue) {
        SelfHealingDashboard.FlowStep step = new SelfHealingDashboard.FlowStep(phase, action, mcp);
        step.status = "COMPLETED";
        step.details.put(detailKey, detailValue);
        flow.steps.add(step);
    }
}

