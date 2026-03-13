# UAC - Universal Autonomous Core

UAC is a non-invasive self-healing monitoring layer that detects anomalies, builds hypotheses, executes recovery actions, and validates outcomes.

## Current branch behavior

- Supports two runnable demo modes through `run_demo.sh`.
- Includes a live local-systems monitor (`LocalSystemsMonitorDemo`) driven by `config/systems/*.yaml`.
- Includes Docker integration for sample systems via `docker/docker-compose.yml`.
- Includes real/simulated PR execution flow in local-systems mode.

## Quick start

```bash
cd /Users/afsinbuyuksarac/development/UAC
./run_demo.sh
```

Open `http://localhost:8888`.

## Demo modes

### 1) Seeded demo mode (default)

```bash
./run_demo.sh
```

- Runs `com.blacklight.uac.demo.WorkingDemo`
- Uses seeded systems, flows, and alarms for showcase/testing
- Logs to `/tmp/uac-demo.log`

### 2) Local systems monitor mode (live)

```bash
./run_demo.sh --local-systems
```

- Runs `com.blacklight.uac.demo.LocalSystemsMonitorDemo`
- Loads system definitions from `config/systems/*.yaml`
- Polls health endpoints and scans log files for anomalies
- Logs to `/tmp/uac-local-monitor.log`

### 3) Local systems with real PR flow

```bash
./run_demo.sh --local-systems --real-pr
```

- Sets `UAC_REAL_PR=true` for the local monitor runtime
- Enables real git/PR operations where available
- Falls back to simulated PR identifiers when real PR creation is not possible

## Useful script commands

```bash
./run_demo.sh --help
./run_demo.sh --verify
./run_demo.sh --logs
./run_demo.sh --stop
./run_demo.sh --restart
./run_demo.sh --clean
```

## Local systems configuration

System files live under `config/systems/`:

- `payment-api.yaml`
- `cache-service.yaml`
- `worker-service.yaml`
- `uac-core.yaml`

Supported YAML sections used by the monitor:

- `system` (`name`)
- `logs` (`location`)
- `health_endpoint` (`url`)
- `deployment` (`restart_command`, optional Docker fields)
- `git` (`repository`, `branch`, `token_env`)

## Docker sample stack

```bash
docker compose -f docker/docker-compose.yml up -d --build
docker compose -f docker/docker-compose.yml logs -f cache-service
docker compose -f docker/docker-compose.yml down
```

`LocalSystemsMonitorDemo` can use Docker restart and rebuild/redeploy actions when Docker deployment fields are configured.

## Recent fix spotlight

- `UAC_LOG_SPAM`: dashboard alarm debug output is gated behind JVM property `uac.verbose.dashboard`.
- Verbose dashboard request logs are disabled by default.

## Core docs

- `README.md` - overview and run modes
- `DEVELOPER_GUIDE.md` - setup, commands, troubleshooting
- `ARCHITECTURE.md` - control loop and component design
- `FEATURES.md` - dashboard behavior and scenario catalog
- `docs/agent_blueprint.md` - original system prompt blueprint

