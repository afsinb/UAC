# Developer Guide

This guide covers local setup, run modes, and troubleshooting for the current UAC branch.

## Prerequisites

- Java 19+
- `bash`
- Optional for live Docker workflow: Docker Engine + Docker Compose plugin
- Optional for real PR flow: git + authenticated GitHub CLI (`gh`)

## Quick start

```bash
cd /Users/afsinbuyuksarac/development/UAC
./run_demo.sh
```

Open `http://localhost:8888`.

## `run_demo.sh` options

```bash
./run_demo.sh --help
./run_demo.sh --clean
./run_demo.sh --logs
./run_demo.sh --stop
./run_demo.sh --restart
./run_demo.sh --verify
./run_demo.sh --local-systems
./run_demo.sh --local-systems --real-pr
```

## Demo modes

### Seeded mode (`WorkingDemo`)

```bash
./run_demo.sh
```

- Class: `com.blacklight.uac.demo.WorkingDemo`
- Log file: `/tmp/uac-demo.log`
- Best for UI walkthrough and deterministic demo data

### Live local-systems mode (`LocalSystemsMonitorDemo`)

```bash
./run_demo.sh --local-systems
```

- Class: `com.blacklight.uac.demo.LocalSystemsMonitorDemo`
- Log file: `/tmp/uac-local-monitor.log`
- Reads `config/systems/*.yaml`
- Polls health endpoints and scans log lines for anomalies

### Real PR mode (local-systems only)

```bash
./run_demo.sh --local-systems --real-pr
```

- Sets `UAC_REAL_PR=true` for runtime
- Enables real git push + `gh pr create` path when environment supports it
- If real PR creation is unavailable, flow may fall back to simulated PR IDs

## Logs

### Seeded mode logs

```bash
tail -f /tmp/uac-demo.log
```

### Local-systems mode logs

```bash
tail -f /tmp/uac-local-monitor.log
```

### Verbose dashboard API tracing (debug only)

```bash
java -Duac.verbose.dashboard=true -cp build/classes com.blacklight.uac.demo.WorkingDemo
```

`UAC_LOG_SPAM` fix gates noisy `/api/alarms` debug prints behind `uac.verbose.dashboard`.

## Local system config workflow

Configured systems:

- `config/systems/payment-api.yaml`
- `config/systems/cache-service.yaml`
- `config/systems/worker-service.yaml`
- `config/systems/uac-core.yaml`

Runtime-required keys parsed by `LocalSystemsMonitorDemo`:

- `system.name`
- `logs.location`
- `health_endpoint.url`

Optional operational/code-fix keys:

- `deployment.restart_command`
- `deployment.container_name`
- `deployment.service_name`
- `deployment.compose_file`
- `git.repository`
- `git.branch`
- `git.token_env`

## Docker stack (optional)

```bash
docker compose -f docker/docker-compose.yml up -d --build
docker compose -f docker/docker-compose.yml logs -f payment-api
docker compose -f docker/docker-compose.yml down
```

When Docker fields are present in YAML and real-PR mode is enabled:

- Operational flows can restart containers
- Code-fix flows can rebuild/redeploy the configured service

## Build and test

```bash
mvn test
mvn package
```

## Troubleshooting

### Port 8888 already in use

```bash
lsof -i :8888
./run_demo.sh --stop
```

### Verify active mode and process

```bash
./run_demo.sh --verify
```

Output includes active class and suggested log file.

### Dashboard opens but no live data in local-systems mode

```bash
tail -n 80 /tmp/uac-local-monitor.log
```

Check for:

- bad `health_endpoint.url`
- missing `logs.location`
- unavailable sample apps / containers

### Docker actions not happening during recovery

Confirm each YAML has valid `deployment.container_name`, `deployment.service_name`, and `deployment.compose_file` values.

## Related docs

- `README.md` - overview and run modes
- `ARCHITECTURE.md` - component model and flow design
- `FEATURES.md` - dashboard behavior and scenario coverage
- `docs/agent_blueprint.md` - original architecture prompt

