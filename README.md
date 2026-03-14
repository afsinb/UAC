# 🎯 UAC - Universal Autonomous Core

**AI-Supported Self-Healing Monitoring System for Software Applications**

---

## 🚀 PROJECT AT A GLANCE

### What is UAC?
A non-invasive autonomous monitoring layer that automatically detects, diagnoses, and heals system issues without modifying your target application.

### Key Capabilities
✅ **Self-Monitor** - Real-time anomaly detection  
✅ **Self-Diagnose** - AI-based root cause analysis  
✅ **Self-Heal** - Automatic infrastructure recovery  
✅ **Self-Deploy** - Code patches via CI/CD  

### Recent Changes (March 2026)
- `developV3` Added open-source ticket lifecycle integration (local tracker + optional GitLab Issues) with anomaly->fix->PR->deploy status/comments/labels traceability in dashboard.
- `143ff2a` Persisted newly learned anomaly rules and fix recipes into `config/next/*-learned.yaml`.
- `d70af23` Enforced strict GitHub truth for PR dependency status mapping (`MERGED` and `APPROVED` only from GH state/review decision).
- `3782945` Added dependency-aware deployment flow UX (`WAITING_DEPENDENCIES`, clearer PR dependency tracking in dashboard).
- `6f73ca5` Introduced PR lifecycle/task dependency enforcement and `MergerMCPServer` integration.
- `9512eca`, `6e84e13`, `0394104` Expanded Docker-aware runtime demo controls, `gh` CLI PR workflow, and local-systems real PR execution path.

---

## 🏗️ SYSTEM ARCHITECTURE

### 5-Phase Self-Healing Loop
```
SIGNAL          → CONTEXT         → HYPOTHESIS      → EXECUTION       → VALIDATION
Detect Issue      Gather Data       Analyze Root      Apply Fix         Verify Health
↓                 ↓                 Cause             ↓                 ↓
Monitor           Metrics,          Operational       Resolver or       Health Score
anomalies         Logs,             vs Logical        Evolver           Stabilization
                  Code Changes
```

### Core Components
| Component | Purpose | Tech |
|-----------|---------|------|
| **Observer** | Telemetry ingestion | Metrics, Logs, Git changes |
| **Brain (Coordinator)** | Signal processing & routing | Event-based decisions |
| **Resolver** | Infrastructure healing | Scale, Restart, Rollback |
| **Evolver** | Code-level healing | Patch, Test, Deploy |

---

## 📦 MODERN ARCHITECTURE (Clean Architecture)

```
DOMAIN LAYER (Pure Business Logic)
├── anomaly/          - Issue detection & diagnosis
├── healing/          - Recovery action definitions
├── metrics/          - Health scoring (0.0-1.0)
└── common/           - Shared domain concepts

APPLICATION LAYER (Use Cases)
├── AnomalyDetectionUseCase
├── HealingExecutionUseCase
└── DTOs for request/response

INFRASTRUCTURE LAYER (Implementation)
├── event/            - Event-driven system
├── service/          - Component implementations
└── persistence/      - Data storage (ready)

MAIN ORCHESTRATOR
└── SelfHealingMonitor - Entry point with Builder pattern
```

**Design Patterns:** Clean Architecture, DDD, Event-Driven, Strategy, Builder, Repository  
**SOLID Principles:** All 5 enforced throughout  

---

## 📋 FILE STRUCTURE

### Documentation (Keep Only These)
```
README.md                           ← YOU ARE HERE
AGENTS.md                           ← Development guidelines
MODERN_ARCHITECTURE_GUIDE.md        ← Detailed architecture
MIGRATION_GUIDE.md                  ← Deployment procedures
TEST_SUITE.md                       ← Testing documentation
```

### Source Code (Production Ready)
```
src/main/java/com/blacklight/uac/
├── domain/                         (19 classes - business logic)
├── application/                    (6 classes - use cases)
├── infrastructure/                 (8 classes - implementations)
├── SelfHealingMonitor.java         (entry point)
├── coordinator/                    (v1 reference - Brain, Signal)
├── observer/                       (v1 reference - monitoring)
├── resolver/                       (v1 reference - recovery)
├── evolver/                        (v1 reference - code fixes)
├── core/                           (shared components)
├── strategies/                     (healing strategies)
└── demo/                           (showcase)
```

### Tests (40+ Test Methods)
```
src/test/java/
├── observer/ObserverTest.java
├── coordinator/BrainTest.java
├── resolver/ResolverTest.java
├── evolver/EvolverTest.java
└── UniversalAutonomousCoreIntegrationTest.java
```

---

## 🎯 QUICK START

### 1. Understand the System
```bash
# Read these in order:
1. This file (README.md) - 5 min overview
2. MODERN_ARCHITECTURE_GUIDE.md - 30 min detailed design
3. AGENTS.md - 15 min development patterns
```

### 2. Run Tests
```bash
./run_tests_java.sh          # Quick validation
mvn test                      # Full test suite
```

### 3. Start Development
```bash
# Follow AGENTS.md for:
- Adding new signal types
- Implementing recovery actions
- Creating development tasks
- Writing tests
```

### 4. Deploy
```bash
# Read MIGRATION_GUIDE.md for:
- Staging deployment
- Production deployment
- Configuration
- Monitoring
```

---

## 🔑 KEY CONCEPTS

### Health Score (0.0 - 1.0)
**Single normalized metric** for system health
- **0.0-0.5**: Critical - Immediate action needed
- **0.5-0.8**: Degraded - Monitor closely
- **0.8-1.0**: Healthy - Normal operation

Calculated from: Metrics (40%) + Logs (35%) + Code Changes (25%)

### Signal Types
- `METRIC_UPDATE` - Performance metrics changed
- `ERROR_DETECTED` - Exception or anomaly
- `EVOLUTION_REQUEST` - Code patch request

### Recovery Actions
- **SCALE** - Add resources (Operational)
- **RESTART** - Restart service (Operational)
- **ROLLBACK** - Revert version (Operational)
- **PATCH** - Fix code (Logical)

### Issue Classification
- **Operational** (Infrastructure) - Low health score → Scale/Restart/Rollback
- **Logical** (Code defect) - Recent changes detected → Patch/Test/Deploy

### Externalized AI Rules and Recipes
UAC now keeps anomaly detection rules and AI fix recipes outside Java code.

Catalog files:
- `config/next/anomaly-rules.yaml` - seeded anomaly detection rules
- `config/next/anomaly-learned.yaml` - dynamically learned anomaly rules
- `config/next/fix-recipes.yaml` - seeded AI fix recipes
- `config/next/fix-recipes-learned.yaml` - dynamically learned recipes

Runtime behavior:
1. Log lines are matched against `anomaly-rules.yaml`
2. Unknown exceptions are auto-normalized (e.g. `MyCustomException` -> `MY_CUSTOM_EXCEPTION`)
3. A new anomaly flow is created for the new type
4. `AIDecisionEngine` chooses a recipe from external catalogs
5. If no recipe exists, a learned fallback recipe is generated and persisted

### Open-Source Ticketing (JIRA-like)
UAC now creates and updates a ticket for each healing flow.

Supported providers:
- `local` (default): lightweight built-in ticket board (no external dependency)
- `gitlab`: GitLab CE/EE Issues API
- `openproject`: OpenProject work packages via API v3

Lifecycle mapping:
- flow created -> ticket created with labels (anomaly/severity/system/pr/mitigation/logs/solution)
- waiting dependencies -> ticket status `WAITING_DEPENDENCIES` with PR dependency comment
- deployment complete -> ticket status `COMPLETED` with final deployment comment

Per-system config (`config/systems/*.yaml`):

```yaml
ticketing:
  enabled: true
  provider: openproject
  base_url: http://localhost:8084
  project_id: uac
  type_id: 1
  token_env: OPENPROJECT_API_TOKEN
```

### OpenProject Docker Setup
Start OpenProject and its database:

```bash
docker compose -f docker/docker-compose.yml up -d openproject-db openproject
```

Wait until OpenProject is up:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8084
```

Expected response after startup is usually `302` (redirect to sign-in).

Then:
1. Open `http://localhost:8084` and complete first-login admin setup.
2. Create an API token in OpenProject account settings.
3. Recommended: run the initializer script to save tokens/config in one place:

```bash
./setup_integrations.sh
```

The initializer writes `docker/.uac.env`, can create/update a local OpenProject dev user (default `UAC` / `uac123`), and is automatically loaded by `run_demo.sh --local-systems` and `./docker/openproject_bootstrap.sh`.

4. Or export the token manually for UAC runtime:

```bash
export OPENPROJECT_API_TOKEN="<your-token>"
```

You can also create `docker/.uac.env` from `docker/.uac.env.example` manually.

5. Bootstrap/create the `uac` project used by system YAML configs:

```bash
./docker/openproject_bootstrap.sh
```

Once configured, new anomaly flows will create OpenProject work packages and append status updates while dependencies/deployments evolve.

---

## 🛠️ DEVELOPMENT WORKFLOW

### Adding New Feature
1. Create domain entity/service in `domain/`
2. Create use case in `application/`
3. Implement in `infrastructure/`
4. Write tests
5. Update AGENTS.md if pattern changes

### Extending Recovery
1. Implement `HealingStrategy` interface
2. Register in `SelfHealingMonitor`
3. Test idempotency (safe to run twice)
4. Add test cases

### Adding New Signal Type
1. Add type to `Signal.java`
2. Handle in `Brain.processSignal()`
3. Route to appropriate component
4. Add test to `BrainTest.java`

---

## 📊 PROJECT METRICS

| Metric | Value | Status |
|--------|-------|--------|
| Source Files | 27 active | ✅ Clean |
| Domain Classes | 19 | ✅ Complete |
| Application Classes | 6 | ✅ Complete |
| Infrastructure Classes | 8 | ✅ Complete |
| Test Methods | 40+ | ✅ Comprehensive |
| Design Patterns | 8+ | ✅ Modern |
| SOLID Principles | 5/5 | ✅ Enforced |
| Architecture | Clean | ✅ Production-Ready |

---

## ⚡ CRITICAL CONSTRAINTS

### Health Score Normalization
```java
// MUST be 0.0 to 1.0, never outside this range
double score = Math.min(1.0, Math.max(0.0, rawScore));
```

### Idempotent Recovery
```java
// Recovery actions MUST be safe to execute twice
resolver.executeRecovery(action);
resolver.executeRecovery(action);  // Same result
```

### Verification Sandbox
```java
// NEVER apply real code changes without sandbox verification
1. Clone repo
2. Reproduce issue
3. Create patch
4. Test in sandbox ← Must pass
5. Only then create PR
```

### Non-Invasive Design
- UAC only **reads** target application state
- Changes only through explicit recovery or PR
- If UAC crashes, target app unaffected

---

## 🚀 GETTING STARTED

**First time?** Start here:
```
1. Read: This README (bird's eye view)
2. Read: MODERN_ARCHITECTURE_GUIDE.md (detailed design)
3. Read: AGENTS.md (development patterns)
4. Run: ./run_tests_java.sh (verify system)
5. Code: Follow AGENTS.md patterns
```

**Ready to deploy?**
```
1. Read: MIGRATION_GUIDE.md
2. Follow deployment steps
3. Test in staging
4. Deploy to production
```

**Running tests?**
```
1. Read: TEST_SUITE.md
2. Run: mvn test or ./run_tests_java.sh
3. Check: src/test/java/ for examples
```

---

## 📚 ESSENTIAL REFERENCES

| Document | Purpose | When to Read |
|----------|---------|--------------|
| **AGENTS.md** | Development patterns & guidelines | Every coding session |
| **MODERN_ARCHITECTURE_GUIDE.md** | System design & architecture | Understanding the system |
| **TEST_SUITE.md** | Testing documentation | Writing/running tests |
| **MIGRATION_GUIDE.md** | Deployment procedures | Before production |

---

## ✅ PROJECT STATUS

```
✅ Code Cleanup:           Complete
✅ Modern Architecture:     Implemented (35+ classes)
✅ Design Patterns:        Applied (8+)
✅ SOLID Principles:       Enforced (5/5)
✅ Tests:                  Passing (40+ methods)
✅ Documentation:          Streamlined (5 files)
✅ Production Ready:       YES
```

---

## 🎯 NEXT STEPS

1. **Understand:** Read MODERN_ARCHITECTURE_GUIDE.md
2. **Learn Patterns:** Study AGENTS.md thoroughly
3. **Validate:** Run ./run_tests_java.sh
4. **Develop:** Follow guidelines in AGENTS.md
5. **Deploy:** Use MIGRATION_GUIDE.md

---

**Your AI-Supported Self-Healing Monitoring System is production-ready!** 🚀

For detailed development patterns, see **AGENTS.md**  
For system architecture, see **MODERN_ARCHITECTURE_GUIDE.md**  
For testing, see **TEST_SUITE.md**  
For deployment, see **MIGRATION_GUIDE.md**

