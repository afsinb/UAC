# Architecture Guide

## System Overview

UAC (Universal Autonomous Core) is a 5-phase self-healing system that detects, diagnoses, and mitigates application issues.

The current branch supports two runtime modes:

- `WorkingDemo` for seeded showcase data
- `LocalSystemsMonitorDemo` for live monitoring of configured local systems

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

## Local systems extension (live monitoring)

`LocalSystemsMonitorDemo` extends the same 5-phase loop to real local targets:

- Loads system definitions from `config/systems/*.yaml`
- Polls each `health_endpoint.url`
- Tails each `logs.location` for anomaly lines (`ERROR`, `EXCEPTION`, `WARN`)
- Creates operational/code-fix healing flows dynamically in dashboard data model

Execution behavior in this mode:

- Operational flow can restart local process or Docker container
- Code-fix flow can apply patch + push + PR (real mode) or simulate PR ids
- Docker deployments can rebuild/redeploy after code fix (`DockerManager`)

Real mode is toggled by environment variable `UAC_REAL_PR=true` (set via `run_demo.sh --local-systems --real-pr`).

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
├── docker/
│   └── DockerManager.java
├── demo/
│   ├── WorkingDemo.java
│   └── LocalSystemsMonitorDemo.java
├── evolver/
│   ├── Evolver.java (code fixes)
│   └── DevelopmentTask.java
└── ui/
    ├── SimpleDashboard.java (100% dynamic)
    └── SelfHealingDashboard.java (data model)

config/
└── systems/
    ├── payment-api.yaml
    ├── cache-service.yaml
    ├── worker-service.yaml
    └── uac-core.yaml

docker/
└── docker-compose.yml
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
./run_demo.sh                              # Seeded mode (WorkingDemo)
./run_demo.sh --local-systems              # Live monitor mode
./run_demo.sh --local-systems --real-pr    # Live mode + real PR path
./run_demo.sh --logs                       # View selected mode logs
./run_demo.sh --verify                     # Check status and active class
```

## Technologies

- **Language:** Java 19
- **Framework:** Core Java (no external dependencies in main code)
- **Testing:** JUnit 5, Mockito
- **HTTP Server:** java.net.HttpServer
- **UI:** Pure HTML/CSS/JavaScript (no frameworks)

---

## Related docs

- `README.md` - project overview and run modes
- `DEVELOPER_GUIDE.md` - setup, commands, troubleshooting
- `FEATURES.md` - dashboard and scenario behavior
- `docs/agent_blueprint.md` - original architecture prompt

