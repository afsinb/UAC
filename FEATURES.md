# FEATURES.md - Complete Feature List & Demo Data

## Recent Platform Updates (March 2026)

- **Real PR fallback for unknown-source anomalies**: if a direct source file patch cannot be applied, UAC commits a learned fallback artifact and can still raise a real PR branch instead of stopping at `simulated:file-not-found`.
- **Per-anomaly ticketing in consolidated deployment queues**: code-fix anomalies can share one deployment instance while each anomaly execution step tracks its own ticket lifecycle details.
- **OpenProject numbering alignment**: ticket keys now follow native OpenProject work package numbering (`#<id>`) and link extraction resolves against work package endpoints.
- **Ticket diagnostics on execution steps**: when a ticket cannot be created/updated (or is disabled), execution details now expose explicit reasons (e.g. `ticket-create-failed`, `ticketing-disabled`).
- **Strict PR truth mapping (GitHub source of truth)**: dependency status now maps from real GH PR fields (`state`, `reviewDecision`, `isDraft`) instead of optimistic local defaults.
- **Dependency-aware deployment lifecycle**: CODE_FIX flows can remain in `WAITING_DEPENDENCIES` with explicit queued deployment semantics until dependent PRs are merged.
- **Cleaner real-mode dependencies**: synthetic compatibility follow-up PRs are no longer shown in real PR mode; only traceable PR dependencies are tracked.
- **Auto-learning catalogs persisted**: new unknown anomaly signatures and fallback fix recipes are written to `config/next/anomaly-learned.yaml` and `config/next/fix-recipes-learned.yaml`.
- **Expanded MCP and PR flow surface**: task dependency graph, PR lifecycle gating, and merger coordination capabilities were added for multi-PR deployment safety.

## Dashboard Features

### 🎯 Interactive Filtering
**Click stat cards to filter healing flows:**

- **All Fixes** - Show all healing flows (default)
- **Code Fixes** - Show only CODE_FIX type (source code changes)
- **Ops Fixes** - Show only OPERATIONAL_FIX type (infrastructure changes)

**Visual Feedback:**
- Active filter card highlighted with blue border + blue background
- Inactive cards shown in gray
- List below updates instantly when filter changes

**Example Interactions:**
```
Dashboard View
├─ All Fixes (7) → Click → Shows all 7 flows
├─ Code Fixes (4) → Click → Shows only 4 code patches
└─ Ops Fixes (3) → Click → Shows only 3 infrastructure fixes
```

### 📊 System Selection
**Sidebar allows selection of any system:**

- payment-service (68% health, 17 items)
- auth-service (55% health, 14 items)
- recommendation-engine (82% health, 10 items)
- admin-portal (91% health, 9 items)

**Updates include:**
- Dashboard summary cards
- Recent/all flows list
- Active/all alarms list
- Health score color-coded (green >70%, yellow >40%, red ≤40%)

### 🔍 Detailed Flow Inspection
**Click any healing flow to see complete 5-phase workflow:**

#### SIGNAL Phase
- **What:** Anomaly detected with metrics
- **Shows:** Error count, failure rate, threshold exceeded, performance degradation
- **Example:** "NullPointerException detected: 143 errors in last 1hr"

#### CONTEXT Phase
- **What:** Code analyzed, metrics gathered
- **Shows:** Source file, line number, metrics location, blame information
- **Example:** "UserService.java:142 analyzed by CodeAnalysisMCP"

#### HYPOTHESIS Phase
- **What:** AI recommends fix with confidence
- **Shows:** AI model used, confidence level, diagnosis
- **Options:**
  - RuleBasedModel (confidence 0.95)
  - PatternMatchingModel (confidence 0.87)
  - HeuristicModel (confidence 0.72)
- **Example:** "RuleBasedModel recommends: Add null guard (0.94 confidence)"

#### EXECUTION Phase
- **What:** Actual fix applied
- **Shows:** PR link, code changes, infrastructure actions, configuration updates
- **Example:** "PR-5421 merged: Added null check + fallback to guest account"

#### VALIDATION Phase
- **What:** Results verified with metrics
- **Shows:** Test results (PASS/FAIL), metrics after fix, success rate
- **Example:** "Sandbox: 50k refunds processed, 0 NPE, 99.8% success"

### 🚨 Alarm Management
**Click any alarm to see:**

- Alarm type (ANOMALY_DETECTED, CIRCUIT_BREAKER_OPEN, etc.)
- Severity level (CRITICAL, HIGH, MEDIUM, LOW, INFO)
- Full message with context
- Related healing flow (if available)
- Link to related flow for investigation

**Alarm Types:**
- ANOMALY_DETECTED - Exception or error found
- RECOVERY_STARTED - Fixing underway
- RECOVERY_SUCCESS - Fix completed
- RECOVERY_FAILED - Fix failed
- CIRCUIT_BREAKER_OPEN - Service unresponsive
- RATE_LIMIT_EXCEEDED - Too many requests
- MEMORY_PRESSURE - Out of memory risk
- etc.

### 📈 Dashboard Tabs

#### Dashboard Tab (Default)
- Summary stat cards (6 metrics)
- Recent fixes (top 3, respects filter)
- Active alarms (top 3)

#### Fixes Tab
- All healing flows
- Respects selected filter (All/Code/Ops)
- Shows flow type, anomaly, step count, status

#### Alarms Tab
- All alarms for system
- Shows type, severity, message
- Sortable by severity/time

## Demo Data Scenarios

### 🏦 payment-service (68% health)

**7 Healing Flows:**

1. **CODE_FIX: NullPointerException in Refund Processor**
   - Severity: CRITICAL
   - File: TransactionProcessor.java:287
   - Issue: Customer object not loaded before accessing account
   - Fix: Add null guard + fallback to guest
   - Metrics: 143 errors/hr → 0 errors
   - PR: PR-5421

2. **OPERATIONAL: High Memory Usage (Session Leak)**
   - Severity: HIGH
   - Memory: 2.1/2.5 GB (84% capacity)
   - Issue: 1.2 GB in old sessions, pool not cleaning
   - Fix: Rolling restart + clear old sessions
   - Result: Memory 2.1GB → 640MB

3. **CODE_FIX: Division by Zero in Fee Calculation**
   - Severity: HIGH
   - File: FeeCalculator.java:156
   - Issue: Merchant with 0% markup causes division error
   - Fix: Add minimum markup bound check (0.01%)
   - Impact: 12k merchant batch import now succeeds

4. **OPERATIONAL: Circuit Breaker Open (Stripe API)**
   - Severity: CRITICAL
   - Timeout Rate: 52% (Stripe v1 sunset)
   - Issue: API gateway timeout too strict for v2
   - Fix: Increase timeout 500ms → 2000ms, switch to v2
   - Result: Success rate 99.6%

5. **CODE_FIX: Currency Conversion Rounding**
   - Severity: CRITICAL
   - Issue: Float precision loss (GBP→JPY conversion)
   - Affected: 847 transactions with <1 JPY errors
   - Fix: Float → BigDecimal with arbitrary precision
   - Validation: Audit pass, all historical TXs correct

6. **OPERATIONAL: Database Connection Pool Exhaustion**
   - Severity: CRITICAL
   - Pool: 200/200 connections held
   - Queued: 1240 waiting requests
   - Issue: Unindexed query (O(n) scan, 28s each)
   - Fix: Added index + increased pool to 250
   - Result: Throughput +340%, avg query 140ms

7. **CODE_FIX: Race Condition in Transaction Lock**
   - Severity: CRITICAL
   - Issue: 34 duplicate charges (same TX ID processed twice)
   - Root: Lock released before idempotency check completes
   - Fix: Synchronized idempotency check with TX lock
   - Validation: 100k chaos test, 0 duplicates

**10 Alarms:** NullPointerException, memory pressure, batch failures, gateway outages, audit findings, connection pool, double-charging, reconciliation disputes, recovery status

---

### 🔐 auth-service (55% health)

**6 Healing Flows:**

1. **CODE_FIX: Expired JWT Tokens Accepted**
   - Severity: CRITICAL (Security)
   - Issue: 18% of active tokens are expired
   - Root: Expiry check commented out (TODO never removed)
   - Fix: Uncomment validation, add 24 edge-case tests
   - Result: All expired tokens rejected within 50ms

2. **OPERATIONAL: CPU Spike During Token Refresh**
   - Severity: HIGH
   - CPU: 91% for 14 minutes (12k refresh/sec)
   - Issue: Refresh endpoint unoptimized, cache misses
   - Fix: Redis-backed cache (TTL 30s), 10 replicas
   - Result: Cache hit rate 87%, latency 10ms → 2ms

3. **CODE_FIX: LDAP Injection Vulnerability**
   - Severity: CRITICAL (Security)
   - Issue: Username directly interpolated into LDAP filter
   - Attack: Auth bypass via LDAP injection
   - Fix: Use LDAPUtils.escapeFilter() for all inputs
   - Validation: Penetration test fails (injection blocked)

4. **OPERATIONAL: Password Reset Rate Limiting**
   - Severity: MEDIUM (Abuse)
   - Issue: 47 reset emails to same user in 5 min
   - Root: No rate limit on /auth/reset endpoint
   - Fix: 3 requests/5min per IP, 5/hour per email
   - Validation: Spam attack blocked, legit users unaffected

5. **CODE_FIX: Weak Password Hashing**
   - Severity: HIGH (Compliance)
   - Issue: 5.2M users stored with MD5 (no salt, deprecated 1996)
   - Fix: Migrate MD5 → bcrypt with cost factor 12
   - Status: 2M/5.2M re-hashed on next login
   - Compliance: Audit pass

6. **OPERATIONAL: Service Cascading Outage**
   - Severity: CRITICAL
   - Issue: 5/5 auth pods crashed after DB restart
   - Root: DB connection pool not recovering
   - Fix: Force restart pods, increased timeout 30s → 60s
   - Result: All pods healthy, recovery in 45s

**8 Alarms:** Expired tokens, CPU spikes, LDAP injection, rate limit abuse, weak cryptography, service outage, security update, recovery success

---

### 🤖 recommendation-engine (82% health)

**4 Healing Flows:**

1. **CODE_FIX: NaN Propagation in ML Scoring**
   - Severity: HIGH
   - Issue: 3.2% of scores are NaN
   - Root: Divide by zero in feature normalization (variance=0)
   - Fix: Add epsilon=1e-10 to variance, handle NaN → default
   - Validation: ML test suite, 100% valid scores

2. **OPERATIONAL: Model Inference Latency SLA Miss**
   - Severity: HIGH
   - Latency: 2.8s vs 500ms SLA (89% miss rate)
   - Issue: Model v3 is 8x larger, no GPU acceleration
   - Fix: Deploy GPU inference (4x A100), batch size 32
   - Result: 19.3x perf gain (2800ms → 145ms)

3. **CODE_FIX: Off-by-One in Ranking Algorithm**
   - Severity: MEDIUM
   - Issue: Top-50 ranking contaminated (8% affected)
   - Root: Loop boundary i <= n instead of i < n
   - Fix: Correct boundary condition, add 48 edge tests
   - Validation: Accuracy restored to 99.7%

4. **OPERATIONAL: Cache Memory Leak**
   - Severity: HIGH
   - Memory: 3.6/4GB, trending up 15MB/hr
   - Issue: LRU eviction callback broken
   - Fix: Refactor callback, manual clear of 60% stale
   - Result: Memory 3.6GB → 680MB, stable

**6 Alarms:** ML anomalies, latency SLA breach, ranking contamination, memory leak, recovery in progress, recovery success

---

### 🛠️ admin-portal (91% health)

**4 Healing Flows:**

1. **CODE_FIX: XSS Vulnerability in User Display**
   - Severity: HIGH (Security)
   - Issue: HTML not escaped in user profile display
   - Attack: Admin can inject JavaScript
   - Fix: OWASP HTML encoding + CSP header
   - Validation: Pentest confirms XSS blocked

2. **OPERATIONAL: Report Generation Timeout**
   - Severity: MEDIUM
   - Issue: 8 hours needed but only 6-hour window
   - Root: Sequential dump of all tables
   - Fix: Parallel backup (8 tables, 8 threads)
   - Result: 8hr → 1.2hr (fits in window)

3. **CODE_FIX: CSRF Token Validation Disabled**
   - Severity: HIGH (Compliance)
   - Issue: CSRF protection disabled in production (TODO flag)
   - Fix: Enable validation, add token rotation
   - Validation: Admin actions now require valid CSRF token

4. **OPERATIONAL: Backup Exceeds Maintenance Window**
   - Severity: MEDIUM
   - Backup: 8 hours needed, only 6-hour available
   - Issue: Backup parallelization inefficient
   - Fix: Parallel backup, incremental snapshots
   - Result: Completes in maintenance window

**5 Alarms:** XSS vulnerability, report timeouts, CSRF disabled, backup failures, recovery success

---

## Feature Statistics

### Overall
- **4 Systems** with distinct health profiles (68%, 55%, 82%, 91%)
- **25 Total Healing Flows** (17 CODE_FIX, 8 OPERATIONAL_FIX)
- **35 Total Alarms** with multiple severity levels
- **5 MCP Servers Tracked** (TelemetryMCP, CodeAnalysisMCP, DynamicAI, DevelopmentMCP, HealingMCP)
- **3+ AI Models** (RuleBasedModel, PatternMatchingModel, HeuristicModel)

### Anomaly Types
- NullPointerException / Null Safety (4)
- High CPU/Memory (5)
- Race Conditions (2)
- Division by Zero (1)
- Type Casting (1)
- Circuit Breaker (1)
- LDAP/XSS/CSRF (3)
- ML Issues (2)
- Configuration/Timeout (5)
- Others (4)

### Action Types
- Scale (3)
- Restart (4)
- Rollback (2)
- Configuration Change (3)
- Code Patch (9)
- Cache Clear (1)
- Parallel Optimization (2)
- Database Index (1)

---

## Interactive Capabilities

✅ Select system from sidebar
✅ Filter flows by type (All/Code/Ops)
✅ Click card to highlight filter
✅ Real-time list update on filter change
✅ View complete 5-phase workflow per flow
✅ See anomaly details (file, line, stack trace)
✅ Click alarm for details
✅ Navigate from alarm to related flow
✅ View MCP server per step
✅ See AI model and confidence per fix

---

## Adding New Systems to Monitor (v2.0+)

### System Integration Requirements

To add any running application to the self-healing system, provide:

1. **Metrics Endpoint**
   - Prometheus format: `http://app:9090/metrics`
   - OR structured JSON logs with timestamps, levels, exceptions
   - Contains: CPU%, memory, error rates, response times

2. **Source Code Access**
   - Git repository (GitHub, GitLab)
   - Personal access token for cloning/PRs
   - Recent commit history

3. **Deployment Capability**
   - Kubernetes, Docker, or VM SSH access
   - Ability to scale, restart, rollback

4. **Structured Logging**
   - JSON format with: timestamp, level, message, exception
   - Exception stack traces for code analysis

### Configuration Template

```yaml
system:
  name: my-service
  environment: production

monitoring:
  enabled: true
  health_check_interval: 60s

metrics:
  source: prometheus
  endpoint: http://localhost:9090/metrics

logs:
  location: /var/log/my-service.log
  format: json

git:
  repository: https://github.com/myorg/my-service.git
  branch: main
  token_env: GITHUB_TOKEN

deployment:
  type: kubernetes  # kubernetes, docker, vm
  namespace: production
  service: my-service
  replicas:
    min: 2
    max: 10
    target_cpu_percent: 70

thresholds:
  cpu_percent: 80
  memory_percent: 85
  error_rate: 0.05
  response_time_ms: 5000

recovery:
  enabled: true
  operational_fixes: true   # scale, restart
  code_fixes: true          # auto-patch
  rollback_on_failure: true
```

### How System Recognition Works

```
Every 60 seconds:
  1. Collect metrics (CPU, memory, errors)
  2. Scan logs for exceptions
  3. Calculate health score (0.0-1.0)
  
If anomaly detected:
  1. Fetch recent git commits
  2. Identify which change caused issue
  3. Classify: Code bug OR infrastructure
  4. Execute fix (automatic or manual review)
  5. Validate recovery
  6. Report in dashboard
```

### Real Example: Payment Service

```yaml
system:
  name: payment-service

thresholds:
  error_rate: 0.02         # Strict: max 2% errors
  response_time_ms: 3000   # Must be fast
  
deployment:
  replicas:
    min: 3                 # Always 3+ instances
    max: 20                # Auto-scale as needed
    target_cpu_percent: 70 # Keep headroom
    
recovery:
  code_fixes: true         # Auto-patch bugs
  operational_fixes: true  # Auto-scale/restart
```

### Quick Integration (5 minutes)

1. Create config file: `config/systems/my-app.yaml`
2. Add Prometheus metrics endpoint or structured logs
3. Grant git access via token: `export GITHUB_TOKEN="..."`
4. Deploy: `./run_demo.sh`
5. View: http://localhost:8888

### Supported Anomalies & Auto-Fixes

| Anomaly | Detection | Auto-Fix |
|---------|-----------|----------|
| High CPU | Metrics > 80% | Scale up replicas |
| High Memory | Metrics > 85% | Restart service |
| High Error Rate | Logs > threshold | Analyze code + PR |
| Exception | Stack trace in logs | Identify source + patch |
| Slow Response | p99 latency > SLA | Scale or optimize |
| Service Crash | Unresponsive | Auto-restart |

---

See README.md for quick start and DEVELOPER_GUIDE.md for setup instructions.

