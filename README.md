# UAC - Universal Autonomous Core

UAC is a self-healing monitoring platform that detects anomalies, diagnoses likely root causes, and executes recovery workflows across infrastructure and code paths.

This file is the canonical entry point for the project documentation set.

## What this repository contains

- Runtime and demo implementation in Java (`src/main/java`)
- Test suite in JUnit (`src/test/java`)
- Interactive dashboard served from `SimpleDashboard`
- Operator, developer, architecture, and feature documentation

## Recent change

- 2026-03-13 (`UAC_LOG_SPAM`): verbose `/api/alarms` dashboard logging is disabled by default.
- Enable detailed dashboard API traces only when debugging:

```bash
java -Duac.verbose.dashboard=true -cp build/classes com.blacklight.uac.demo.WorkingDemo
```

## Documentation map

- `README.md`: project overview and navigation index (this file)
- `USER_GUIDE.md`: how to run and use the demo dashboard
- `DEVELOPER_GUIDE.md`: local setup, troubleshooting, and development commands
- `ARCHITECTURE.md`: component model, data flow, and design principles
- `FEATURES.md`: dashboard capabilities and demo scenarios
- `AGENT.md`: machine-readable contributor context for AI agents
- `docs/agent_blueprint.md`: original prompt blueprint that inspired the architecture

## Quick start

```bash
cd /Users/afsinbuyuksarac/development/UAC
./run_demo.sh
```

Open `http://localhost:8888`.

## Core concepts

- 5-phase loop: `SIGNAL -> CONTEXT -> HYPOTHESIS -> EXECUTION -> VALIDATION`
- Components: `Observer`, `Brain`, `Resolver`, `Evolver`
- Health score is normalized to `0.0..1.0`
- Recovery actions must be idempotent
- Code changes should follow sandbox verify-first workflow

## Development and validation

```bash
mvn test
mvn package
./run_demo.sh --verify
```

## Project history (high level)

- Initial platform and documentation baseline in `initial-phase` tag.
- 2026-03-13 maintenance fix reduces default dashboard API log noise (`UAC_LOG_SPAM`).

## Recommended reading order

1. `README.md`
2. `USER_GUIDE.md`
3. `ARCHITECTURE.md`
4. `DEVELOPER_GUIDE.md`
5. `AGENT.md`

