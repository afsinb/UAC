# User Guide

This guide is for operators and evaluators who want to run the demo and understand the dashboard.

Use this document for day-to-day dashboard operation and demo walkthroughs.

## Prerequisites

- Java 19+ installed
- Browser access to `http://localhost:8888`
- Repository checked out locally

## Quick start

```bash
cd /Users/afsinbuyuksarac/development/UAC
./run_demo.sh
```

Open `http://localhost:8888` after startup.

## Script commands

```bash
./run_demo.sh
./run_demo.sh --verify
./run_demo.sh --logs
./run_demo.sh --stop
./run_demo.sh --restart
./run_demo.sh --clean
```

## Dashboard navigation

- Left sidebar: choose a monitored system
- Top cards: filter between all fixes, code fixes, and operational fixes
- Main tabs:
  - Dashboard: summary and recent items
  - Fixes: full healing flow list
  - Alarms: active/history alarm list

## Understanding a healing flow

Each flow follows five stages:

1. `SIGNAL`: anomaly detected
2. `CONTEXT`: logs/metrics/code context assembled
3. `HYPOTHESIS`: likely root cause selected
4. `EXECUTION`: remediation applied
5. `VALIDATION`: outcome checked and stabilized

Use this sequence to understand where a fix succeeded or failed.

## Understanding alarms

Alarm entries typically include:

- Alarm type and severity
- Human-readable message
- Related system and timestamps
- Optional link/reference to related healing flow

## Log behavior and verbosity

By default, dashboard API request logging is reduced to avoid log spam.

To enable verbose dashboard API traces for troubleshooting, run:

```bash
java -Duac.verbose.dashboard=true -cp build/classes com.blacklight.uac.demo.WorkingDemo
```

## Common problems

### Dashboard not reachable

```bash
./run_demo.sh --verify
lsof -i :8888
```

If port `8888` is in use, stop the old process or restart the demo.

### No data visible

```bash
./run_demo.sh --logs
```

Wait a few seconds for initialization, then refresh the page.

### Stale browser state

Do a hard refresh in your browser and retry system selection/filtering.

## Related docs

- `README.md` - repository overview and documentation hub
- `FEATURES.md` - complete scenario catalog
- `DEVELOPER_GUIDE.md` - debugging and development workflows
- `ARCHITECTURE.md` - component design details
- `AGENT.md` - AI-agent project context

