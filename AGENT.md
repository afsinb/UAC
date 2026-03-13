# AI Contributor Guide

This file is intended for any AI agent or automation system that needs fast, reliable context about the UAC repository.

Use this guide to understand project expectations, architecture vocabulary, and safe change workflow before editing code or docs.

## Project snapshot

- Project: UAC (Universal Autonomous Core)
- Language: Java (Maven project)
- Goal: non-invasive self-healing monitoring loop for software systems
- Entry points commonly used in demos:
  - `com.blacklight.uac.demo.WorkingDemo`
  - `com.blacklight.uac.ui.SimpleDashboard`

## System model

UAC runs a five-phase control loop:

1. `SIGNAL`: telemetry and anomaly detection
2. `CONTEXT`: gather system/log/git context
3. `HYPOTHESIS`: classify probable issue type
4. `EXECUTION`: operational recovery or code-level remediation
5. `VALIDATION`: confirm stabilization

Core runtime roles:

- `Observer`: telemetry ingestion and health scoring
- `Brain`: orchestration and routing
- `Resolver`: operational recovery actions (restart/scale/rollback style)
- `Evolver`: code-oriented remediation workflow (reproduce/patch/verify/PR)

## Key features implemented

- Health score normalization in `0.0..1.0`
- Dashboard-driven visibility into systems, alarms, and healing flows
- Filterable views for code fixes vs operational fixes
- AI-assisted diagnosis model abstractions under `ai/`
- Event-driven coordination under `core/events` and coordinator packages
- Demo scenarios with realistic anomalies and remediations

See `FEATURES.md` for full scenario-level coverage.

## Related docs

- `README.md` - repository index and quick start
- `USER_GUIDE.md` - operator-facing usage guide
- `DEVELOPER_GUIDE.md` - setup, debugging, troubleshooting
- `ARCHITECTURE.md` - design, package structure, principles
- `FEATURES.md` - detailed capability and demo matrix
- `AGENT.md` - AI contributor context and workflow guardrails
- `docs/agent_blueprint.md` - original conceptual prompt

## Project history (known milestones)

- `initial-phase` tag: initial code and core docs baseline.
- 2026-03-13 (`UAC_LOG_SPAM`): dashboard `/api/alarms` request logging made quiet by default.
  - Verbose traces can be enabled with JVM property:
    - `-Duac.verbose.dashboard=true`

## Working norms for AI agents

- Preserve non-invasive design assumptions; do not imply direct mutation of external target apps unless explicitly modeled.
- Keep health score semantics consistent (`0.0..1.0`).
- Preserve idempotency assumptions for recovery logic.
- Prefer minimal, focused edits over broad rewrites unless requested.
- Do not remove user changes that are unrelated to the requested task.
- Update docs whenever behavior, CLI flags, or workflows change.

## Safe workflow for code changes

1. Inspect current status and recent commits.
2. Read affected code paths and relevant docs.
3. Apply smallest possible change.
4. Run targeted tests first, then broader tests if needed.
5. Update docs and cross-links.
6. Report residual risks and follow-up tasks.

## Useful commands

```bash
# Build and test
mvn test
mvn package

# Run demo
./run_demo.sh
./run_demo.sh --verify
./run_demo.sh --logs

# Run demo with verbose dashboard API tracing
java -Duac.verbose.dashboard=true -cp build/classes com.blacklight.uac.demo.WorkingDemo
```

