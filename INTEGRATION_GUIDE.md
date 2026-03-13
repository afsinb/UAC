# Integration Guide - Adding a New System to Monitor

This guide explains how to connect your running application to the Self-Healing System for automated monitoring and recovery.

## Overview

The Self-Healing System monitors applications through a standardized integration protocol. When you add a new system, it will:

1. **Detect** anomalies (exceptions, performance degradation, resource issues)
2. **Diagnose** root causes through code analysis
3. **Heal** automatically (scale, restart, fix code, or rollback)
4. **Report** results in the dashboard

## What the System Needs

### 1. Metrics & Telemetry
**What:** Real-time performance data
- CPU usage, memory, disk I/O
- Response times, request rates
- Error rates, exception counts

**Where it comes from:**
- Application logs (stdout/stderr)
- Metrics endpoint (Prometheus format)
- System monitoring tools (collectd, telegraf, etc.)

**Example:**
```
cpu_usage_percent: 85
memory_mb: 1250
error_rate: 0.05
response_time_ms: 234
```

### 2. Source Code Access
**What:** Current and historical code
- Git repository access
- Commit history for recent changes
- Current branch/version deployed

**Why:** To analyze root causes and generate fixes

**Configuration:**
```yaml
git:
  repository: https://github.com/myorg/myapp.git
  branch: main
  directory: /path/to/source
```

### 3. Logging
**What:** Structured application logs
- Exceptions and stack traces
- Business events
- Performance metrics

**Format:** JSON or structured text
```json
{
  "timestamp": "2026-03-12T10:30:45Z",
  "level": "ERROR",
  "message": "NullPointerException in UserService",
  "exception": "java.lang.NullPointerException...",
  "service": "payment-service"
}
```

### 4. Deployment System
**What:** Ability to deploy fixes and roll back
- Container orchestration (Kubernetes, Docker)
- Or traditional deployment (VM, server)
- CI/CD pipeline integration

**Why:** To automatically deploy code fixes or perform operational fixes

**Configuration:**
```yaml
deployment:
  type: kubernetes  # or docker, vm, etc.
  namespace: production
  service_name: myapp
  image_registry: gcr.io/myorg/myapp
```

### 5. Ticketing/Version Control Integration
**What:** Where to create issues/PRs for fixes
- GitHub, GitLab, Jira
- Pull request creation
- Issue tracking

**Configuration:**
```yaml
vcs:
  provider: github  # github, gitlab, etc.
  token: ${VCS_TOKEN}
  org: myorg
  repo: myapp
```

## Step-by-Step Integration

### Step 1: Create System Configuration

Create a file: `config/systems/myapp.yaml`

```yaml
system:
  name: my-payment-service
  id: payment-svc-prod
  description: Payment processing service
  
monitoring:
  enabled: true
  health_check_interval: 60s
  metrics_port: 9090
  
logs:
  location: /var/log/myapp
  format: json
  patterns:
    error: "ERROR|Exception|FATAL"
    warning: "WARN"
    
git:
  repository: https://github.com/myorg/payment-service.git
  branch: main
  
deployment:
  type: kubernetes
  namespace: production
  service: payment-service
  replicas_min: 2
  replicas_max: 10
  
vcs:
  provider: github
  org: myorg
  repo: payment-service
  token_env: GITHUB_TOKEN
  
thresholds:
  cpu_percent: 80
  memory_percent: 85
  error_rate: 0.05
  response_time_ms: 5000
```

### Step 2: Connect Metrics Collection

The system needs to ingest metrics. Choose one:

**Option A: Prometheus Metrics (Recommended)**
Your app exposes metrics on: `http://localhost:9090/metrics`

```java
// In your application
import io.micrometer.prometheus.PrometheusMeterRegistry;

@RestController
public class MetricsEndpoint {
    private PrometheusMeterRegistry registry;
    
    @GetMapping("/metrics")
    public String metrics() {
        return registry.scrape();
    }
}
```

**Option B: Log-based Metrics**
System parses logs and extracts metrics

**Option C: Agent-based Monitoring**
Deploy monitoring agent (Datadog, New Relic, etc.)

### Step 3: Configure Logging

Ensure your application outputs structured logs:

```java
// Example: Spring Boot with JSON logging
// application.yml
logging:
  level:
    root: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

// Or use Logstash JSON encoder for structured logs
{
  "timestamp": "2026-03-12T10:30:45Z",
  "level": "ERROR",
  "logger": "com.myorg.PaymentService",
  "message": "Transaction failed",
  "exception": "NullPointerException",
  "service": "payment-service",
  "request_id": "req-12345"
}
```

### Step 4: Grant Git Access

The system needs to read/write to your repository:

**Create a Deploy Key or Token:**

```bash
# Generate token on GitHub
# Settings → Developer settings → Personal access tokens → Fine-grained tokens
# Permissions needed:
# - contents: read/write
# - pull-requests: read/write
# - issues: read/write
```

Store securely:
```bash
export UAC_GITHUB_TOKEN="ghp_xxxxxxxxxxxxx"
export UAC_GIT_REPO="https://github.com/myorg/myapp.git"
```

### Step 5: Deploy Monitoring Agent

Deploy the Self-Healing System agent to your infrastructure:

**For Kubernetes:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: uac-config
data:
  system.yaml: |
    system:
      name: my-service
      monitoring:
        enabled: true
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: uac-monitor
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: uac
        image: uac:1.0
        env:
        - name: MONITOR_SYSTEM
          value: "my-service"
        - name: GITHUB_TOKEN
          valueFrom:
            secretKeyRef:
              name: uac-secrets
              key: github-token
        volumeMounts:
        - name: config
          mountPath: /config
        - name: logs
          mountPath: /logs
      volumes:
      - name: config
        configMap:
          name: uac-config
      - name: logs
        hostPath:
          path: /var/log
```

**For Docker Compose:**
```yaml
version: '3'
services:
  myapp:
    image: myorg/myapp:latest
    ports:
      - "8080:8080"
    environment:
      - LOG_LEVEL=INFO
      
  uac-monitor:
    image: uac:1.0
    environment:
      - SYSTEM_NAME=my-service
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - METRICS_URL=http://myapp:9090/metrics
      - LOG_PATH=/logs/myapp.log
    volumes:
      - /var/log:/logs
      - ./uac-config.yaml:/config/system.yaml
    depends_on:
      - myapp
```

### Step 6: Verify Integration

The system will automatically:

1. **Detect Metrics** - Pull CPU, memory, error rates
2. **Monitor Logs** - Parse exceptions and anomalies
3. **Analyze Code** - Clone your repo and examine recent changes
4. **Detect Anomalies** - When health score drops below threshold
5. **Create Issues/PRs** - For code-level fixes
6. **Execute Actions** - Scale, restart, rollback, or deploy fixes

## How System Recognition Works

### Anomaly Detection Flow

```
┌─────────────────────────────────────────────────────────────┐
│ 1. METRICS COLLECTION (Every 60 seconds)                   │
│    • CPU, Memory, Disk usage                               │
│    • Request rate, Error rate                              │
│    • Response time percentiles (p50, p99)                  │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────────┐
│ 2. HEALTH SCORE CALCULATION                                │
│    • Normalize all metrics to 0.0-1.0 scale               │
│    • Apply thresholds (your config)                        │
│    • Calculate weighted health score                       │
│    • Compare to historical baseline                        │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────────┐
│ 3. ANOMALY DETECTION                                       │
│    • If health < 0.5 → Operational issue suspected        │
│    • If health drops > 20% → Alert triggered              │
│    • Pull error logs for analysis                          │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────────┐
│ 4. ROOT CAUSE ANALYSIS                                     │
│    • Parse exception stack traces                          │
│    • Fetch recent git commits (from your repo)            │
│    • Identify which code change caused issue              │
│    • Classify: Logical bug vs Operational (resource)     │
└────────────────┬────────────────────────────────────────────┘
                 │
     ┌───────────┴──────────────┐
     │                          │
┌────▼──────────────────┐   ┌──▼────────────────────┐
│ LOGICAL ISSUE         │   │ OPERATIONAL ISSUE     │
│ (Code-level bug)      │   │ (Infrastructure)      │
│                       │   │                       │
│ → Clone repo          │   │ → Scale service       │
│ → Reproduce issue     │   │ → Restart containers  │
│ → Patch code          │   │ → Rollback version    │
│ → Test in sandbox     │   │ → Adjust config       │
│ → Create PR           │   │                       │
└───────────────────────┘   └───────────────────────┘
```

## Configuration File Reference

```yaml
system:
  name: service-name              # Unique identifier
  id: svc-prod-001                # Internal ID
  environment: production          # prod/staging/dev
  
monitoring:
  enabled: true
  health_check_interval: 60s      # How often to check
  metrics_timeout: 10s             # Timeout for metric collection
  log_scan_interval: 30s           # How often to scan logs
  
metrics:
  source: prometheus              # prometheus, statsd, custom
  endpoint: http://localhost:9090/metrics
  scrape_interval: 30s
  
logs:
  location: /var/log/app          # Log file path or URL
  format: json                    # json, plain
  retention_days: 7               # Keep logs for N days
  
git:
  repository: https://github.com/org/repo.git
  branch: main                    # Branch to monitor
  token_env: GITHUB_TOKEN         # Environment var for token
  
deployment:
  type: kubernetes                # kubernetes, docker, vm
  namespace: production
  service: my-service
  image: registry/org/service
  replicas:
    min: 1
    max: 10
    target_cpu_percent: 70        # Auto-scale at 70% CPU
    
vcs:
  provider: github                # github, gitlab, etc
  token_env: VCS_TOKEN
  create_prs: true                # Auto-create PRs for fixes
  
thresholds:
  cpu_percent: 80                 # Alert at 80% CPU
  memory_percent: 85              # Alert at 85% memory
  disk_percent: 90                # Alert at 90% disk
  error_rate: 0.05                # Alert at 5% error rate
  response_time_ms: 5000          # Alert if p99 > 5s
  
recovery:
  enabled: true
  operational_fixes: true         # Allow scale/restart
  code_fixes: true                # Allow auto-patching
  rollback_on_failure: true
```

## Testing Your Integration

Once configured, test with these commands:

```bash
# 1. Test metrics collection
curl http://localhost:9090/metrics

# 2. Test git access
git clone https://github.com/myorg/myapp.git

# 3. Test logs parsing
tail -f /var/log/myapp.log | grep -i error

# 4. Manually trigger health check
./run_demo.sh --verify

# 5. View dashboard
open http://localhost:8888
```

## Dashboard Integration

Once integrated, your system appears in the dashboard:

```
Dashboard (http://localhost:8888)
├─ Systems Sidebar
│  └─ my-payment-service (health: 92%)
├─ Fixes Tab
│  ├─ Anomaly 1: High CPU detected
│  ├─ Anomaly 2: NullPointerException
│  └─ Anomaly 3: Memory leak suspected
├─ Alarms Tab
│  ├─ CRITICAL: Error rate 15%
│  ├─ HIGH: P99 latency > SLA
│  └─ MEDIUM: Disk usage warning
└─ Details
   └─ Click any fix/alarm to see:
      • Anomaly detected
      • Root cause analysis
      • Code/config fix applied
      • Validation results
```

## Real Example: Adding Payment Service

Here's a complete example of integrating a real payment service:

**File: `config/systems/payment-service.yaml`**

```yaml
system:
  name: payment-service
  environment: production
  
monitoring:
  enabled: true
  health_check_interval: 30s
  
metrics:
  source: prometheus
  endpoint: http://payment-service.prod.svc.cluster.local:9090/metrics
  
logs:
  location: /var/log/payment-service/app.log
  format: json
  
git:
  repository: https://github.com/fintech/payment-service.git
  branch: main
  token_env: GITHUB_TOKEN
  
deployment:
  type: kubernetes
  namespace: production
  service: payment-service
  image: gcr.io/fintech/payment-service
  replicas:
    min: 3
    max: 20
    target_cpu_percent: 75
    
vcs:
  provider: github
  token_env: GITHUB_TOKEN
  create_prs: true
  
thresholds:
  cpu_percent: 85
  memory_percent: 90
  error_rate: 0.02           # Alert at 2% errors
  response_time_ms: 3000     # Alert if p99 > 3s
  
recovery:
  enabled: true
  operational_fixes: true
  code_fixes: true
```

## Next Steps

1. Create your system configuration file
2. Ensure metrics are exposed (Prometheus format)
3. Ensure logs are structured (JSON)
4. Grant git access via token/SSH
5. Deploy the monitoring agent
6. Wait 2-3 minutes for first metrics collection
7. Open dashboard and verify your system appears
8. Trigger a test anomaly and watch it auto-heal

Questions? Check the ARCHITECTURE.md for how the system works internally.

