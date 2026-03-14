# ARCHITECTURE.md - System Design & Components

## System Overview

UAC (Universal Autonomous Core) is a 5-phase self-healing system that automatically detects, diagnoses, and fixes application issues.

## March 2026 Lifecycle Notes

- **Dependency-aware execution state:** code-fix flows can remain in `WAITING_DEPENDENCIES` until all PR dependencies are merged.
- **GitHub truth mapping:** dependency status (`OPEN` / `APPROVED` / `MERGED`) is resolved from live GitHub PR metadata in real mode.
- **Consolidated deployment queue:** multiple code-fix anomalies for the same system can be consolidated into one deployment instance while preserving separate execution lineage.
- **Per-anomaly ticketing:** even when deployment is consolidated, each anomaly execution step can track its own ticket lifecycle and URL.
- **OpenProject alignment:** ticket key display follows native OpenProject work package numbering (`#<id>`).

## 5-Phase Healing Loop

```
┌─────────────────────────────────────────────────────────────┐
│ PHASE 1: SIGNAL                                             │
│ ─────────────────────────────────────────────────────────── │
│ Observer detects anomaly                                    │
│ • Collects metrics from systems                             │
│ • Parses application logs                                   │
│ • Tracks code changes (git commits)                         │
│ • Calculates normalized health score (0.0-1.0)             │
│ Output: Signal with anomaly details                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 2: CONTEXT                                            │
│ ─────────────────────────────────────────────────────────── │
│ Brain gathers system context                                │
│ • Requests last 5 minutes of metrics                        │
│ • Gathers application logs                                  │
│ • Retrieves recent code changes                             │
│ • Creates context snapshot                                  │
│ Output: ContextSnapshot with system state                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 3: HYPOTHESIS                                         │
│ ─────────────────────────────────────────────────────────── │
│ Brain determines issue type                                 │
│ • Health score threshold: < 0.5 = Operational              │
│ • Health score threshold: ≥ 0.5 = Logical                  │
│ Output: IssueHypothesis with type & confidence              │
└─────────────────────────────────────────────────────────────┘
                     ↓                    ↓
        ┌────────────────┐    ┌──────────────────┐
        │ OPERATIONAL    │    │     LOGICAL      │
        │ ISSUE          │    │     ISSUE        │
        │ (Infrastructure)│   │ (Code defect)    │
        └────────────────┘    └──────────────────┘
             ↓                          ↓
┌────────────────────────┐  ┌─────────────────────────┐
│ PHASE 4A: EXECUTION    │  │ PHASE 4B: EXECUTION     │
│ (Resolver)             │  │ (Evolver)               │
│ ──────────────────────  │  │ ────────────────────── │
│ • Scale service        │  │ • Clone repository     │
│ • Restart containers   │  │ • Reproduce issue      │
│ • Rollback deployment  │  │ • Apply patch          │
│ • Adjust configuration │  │ • Verify in sandbox    │
│ │ • Create PR            │
│ Output: Action taken    │  │ Output: PR created     │
└────────────────────────┘  └─────────────────────────┘
             │                          │
             └──────────────┬───────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 5: VALIDATION                                         │
│ ─────────────────────────────────────────────────────────── │
│ System waits for health to stabilize                        │
│ • Monitor metrics post-fix                                  │
│ • Wait for health score to be stable                        │
│ • Record success/failure                                    │
│ Output: VALIDATION_COMPLETE status                          │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Observer (Telemetry Ingestion)
**Purpose:** Collect all system signals

**Key Interface:**
```java
double healthScore = observer.mapToHealthScore(metrics, logs, dnaChanges);
// Returns: 0.0-1.0 (0=unhealthy, 1=healthy)
// Formula: Weighted average
//   - Metrics: 40% (CPU, memory, response time)
//   - Logs: 35% (exceptions, errors)
//   - Code changes (DNA): 25% (recent commits)
```

**Signals Generated:**
- METRIC_UPDATE - Health score changed
- ERROR_DETECTED - Exception or anomaly found
- EVOLUTION_REQUEST - Code fix needed

### 2. Brain (Coordinator)
**Purpose:** Route signals and determine issue type

**Key Interface:**
```java
void processSignal(Signal signal);
// Cases:
// 1. Health score drops → Request CONTEXT
// 2. Exception detected → Determine HYPOTHESIS
// 3. Hypothesis ready → Route to RESOLVER or EVOLVER
```

**Issue Determination:**
```
Health Score < 0.5 → OPERATIONAL ISSUE
├─ High error rates
├─ Resource exhaustion (CPU, memory)
├─ Service crashes
└─ Route to: RESOLVER (Scale/Restart/Rollback)

Health Score ≥ 0.5 → LOGICAL ISSUE
├─ Logic errors in code
├─ Null pointer exceptions
├─ Type mismatches
└─ Route to: EVOLVER (Code Fix/PR)
```

### 3. Resolver (Infrastructure Recovery)
**Purpose:** Execute operational fixes

**Recovery Actions:**
- **SCALE** - Increase replicas
- **RESTART** - Restart service
- **ROLLBACK** - Revert deployment
- **CONFIGURATION** - Change settings

**Idempotency Requirement:**
- All actions must be safe to execute twice
- Running twice = same result (no side effects)
- Prevents cascading failures

**Example:**
```
Action: Scale payment-service from 1→3 replicas
Idempotent: Running twice results in 3 replicas
Non-Idempotent: ✗ Scale by 2 (would be 1→3→5)
```

### 4. Evolver (Development Cycle)
**Purpose:** Create and deploy code fixes

**Workflow:**
1. **CLONE** - Clone repository
2. **REPRODUCE** - Verify issue in sandbox
3. **PATCH** - Apply code fix
4. **VERIFY** - Test in sandbox (no real code changes yet!)
5. **PR** - Create pull request only after verification

**Critical:** Never skip VERIFY step

**Example Fix:**
```java
// BEFORE: NullPointerException
String name = user.getName();  // user might be null

// AFTER: Null-safe
String name = user != null ? user.getName() : "Guest";
```

### 5. AI Engine (Decision Making)
**Purpose:** Recommend fixes based on anomaly type

**AI Models (Priority Order):**
1. **RuleBasedModel** - Fast rule-based decisions
2. **PatternMatchingModel** - Pattern recognition
3. **HeuristicModel** - Weighted scoring

**Example Decisions:**
```
Anomaly: NullPointerException
├─ Rule: Add null check → Confidence: 0.95
└─ Fix: if (object != null) { ... }

Anomaly: High CPU
├─ Pattern: Matches spike after deployment
├─ Hypothesis: Bad code in new version
└─ Fix: Rollback to previous version

Anomaly: OOMError
├─ Heuristic: Memory grows 50MB/hour
├─ Time to OOM: ~2 hours
└─ Fix: Restart service + investigate heap dump
```

## Data Models

### Signal
```java
public class Signal {
    String source;              // TelemetryMCP, etc.
    String type;               // METRIC_UPDATE, ERROR_DETECTED
    Map<String, Object> data;  // Key-value details
}
```

### IssueHypothesis
```java
public class IssueHypothesis {
    String type;               // OPERATIONAL or LOGICAL
    double healthScore;        // 0.0-1.0
    String diagnosis;          // Root cause description
    double confidence;         // 0.0-1.0 confidence
}
```

### RecoveryAction (Operational)
```java
public class RecoveryAction {
    ActionType type;           // SCALE, RESTART, ROLLBACK
    String target;             // Service name
    Map<String, Object> params;// Scale factor, version, etc.
}
```

### DevelopmentTask (Code Fix)
```java
public class DevelopmentTask {
    String taskType;           // FIX, ENHANCEMENT
    String anomalyType;        // NullPointerException, etc.
    String code;               // Source code to fix
    String stackTrace;         // Full stack trace
    boolean verified;          // Sandbox verification passed
}
```

## Design Principles

### 1. Non-Invasive Observation
- UAC reads system state, doesn't modify target application directly
- Changes only happen through Resolver (infrastructure) or Evolver (PRs)
- If UAC crashes, target app is unaffected

### 2. Stateless Components
- Each component independently testable with mocked dependencies
- Brain doesn't store state; routes each signal freshly
- Recovery actions are stateless (re-entrant)

### 3. Normalized Health Score
- Single metric for system assessment
- Always 0.0 (unhealthy) to 1.0 (healthy)
- Never exceeds bounds
- Used for all routing decisions

### 4. Idempotent Operations
- All recovery actions safe to execute twice
- Same result on repeat execution
- Prevents cascading failures
- Critical for reliability

### 5. Verification Before Production
- Evolver tests all code fixes in sandbox
- Never deploys untested changes
- PR review before merge
- Prevents breaking production

## Package Structure

```
src/main/java/com/blacklight/uac/
├── ai/
│   ├── AIDecisionEngine.java
│   ├── models/
│   │   ├── AIModel.java (interface)
│   │   ├── RuleBasedModel.java
│   │   ├── PatternMatchingModel.java
│   │   └── HeuristicModel.java
│   └── learning/
│       └── ReinforcementLearner.java
├── core/coordinator/
│   ├── Brain.java (signal router)
│   ├── Signal.java (data carrier)
│   ├── HypothesisEngine.java
│   ├── ContextSnapshot.java
│   └── IssueHypothesis.java
├── observer/
│   ├── Observer.java (health calculation)
│   ├── Metric.java
│   ├── LogEntry.java
│   └── DNAChange.java
├── resolver/
│   ├── Resolver.java (operational fixes)
│   └── RecoveryAction.java
├── evolver/
│   ├── Evolver.java (code fixes)
│   └── DevelopmentTask.java
└── ui/
    ├── SimpleDashboard.java (100% dynamic)
    ├── SelfHealingDashboard.java (data model)
    └── WorkingDemo.java (demo data)
```

## Workflow Integration

### Running Tests
```bash
mvn test                          # All tests
mvn test -Dtest=BrainTest        # Specific test
```

### Building
```bash
mvn compile                       # Compile
mvn package                       # Package JAR
mvn clean install                 # Full rebuild
```

### Running Demo
```bash
./run_demo.sh                     # Compile + start
./run_demo.sh --logs              # View logs
./run_demo.sh --verify            # Check status
```

## Technologies

- **Language:** Java 19
- **Framework:** Core Java (no external dependencies in main code)
- **Testing:** JUnit 5, Mockito
- **HTTP Server:** java.net.HttpServer
- **UI:** Pure HTML/CSS/JavaScript (no frameworks)

---

**See also:**
- docs/agent_blueprint.md - Original architecture
- FEATURES.md - Detailed feature descriptions
- DEVELOPER_GUIDE.md - Setup and troubleshooting

