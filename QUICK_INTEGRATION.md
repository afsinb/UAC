# Quick Integration Reference

## 5-Minute Setup for Adding a New System

### 1. Create Config File
```bash
mkdir -p config/systems
cat > config/systems/my-service.yaml << 'EOF'
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
  type: kubernetes
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
EOF
```

### 2. Enable Metrics (Choose ONE)

**Option A: Prometheus (Recommended)**
- Add dependency: `io.micrometer:micrometer-registry-prometheus`
- Expose endpoint: `GET http://your-app:9090/metrics`
- System will auto-scrape

**Option B: Structured Logs**
- Output JSON logs with timestamp, level, message, exception
- System will parse and extract metrics

**Option C: Pre-existing Monitoring**
- If you use Datadog/New Relic/CloudWatch
- System can integrate with their APIs

### 3. Configure Git Access
```bash
# Create personal access token on GitHub
# Settings → Developer settings → Personal access tokens

export GITHUB_TOKEN="ghp_xxxxxxxxxxxxx"
export GIT_REPO="https://github.com/myorg/my-service.git"
```

### 4. Deploy Monitoring Agent
```bash
# For Kubernetes
kubectl apply -f uac-monitor-deployment.yaml

# For Docker
docker-compose up uac-monitor

# For VM
./run_demo.sh
```

### 5. Verify Integration
```bash
# Check metrics collection
curl http://localhost:9090/metrics

# Check git access
git clone https://github.com/myorg/my-service.git

# Check logs parsing
tail -f /var/log/my-service.log

# Open dashboard
open http://localhost:8888
```

---

## What System Recognizes

| Signal | How Detected | Action |
|--------|-------------|--------|
| **High CPU** | Metrics > 80% | Scale up or restart |
| **High Memory** | Metrics > 85% | Restart (clear memory) |
| **High Error Rate** | Logs: ERROR count > 5% | Analyze code, create PR |
| **Exception** | Logs: Exception stack trace | Identify source file/line |
| **Slow Response** | Metrics: p99 > threshold | Scale or optimize |
| **Crash** | Service unresponsive | Automatic restart |

---

## Configuration Options Explained

```yaml
# WHAT TO MONITOR
monitoring:
  health_check_interval: 60s    # Check every 60 seconds (1-300s)
  log_scan_interval: 30s        # Scan logs every 30 seconds
  
# WHERE TO GET DATA
metrics:
  source: prometheus            # prometheus | statsd | custom
  endpoint: http://app:9090     # Where metrics live
  
logs:
  location: /var/log/app.log    # File path or URL
  format: json                  # json | plain
  
# WHEN TO ALERT (Health Score < 0.5 triggers)
thresholds:
  cpu_percent: 80               # Alert if CPU > 80%
  memory_percent: 85            # Alert if Memory > 85%
  error_rate: 0.05              # Alert if Errors > 5%
  response_time_ms: 5000        # Alert if p99 > 5 seconds
  
# HOW TO FIX
recovery:
  operational_fixes: true       # Allow scale/restart
  code_fixes: true              # Allow auto-patching
  rollback_on_failure: true     # Rollback if fix fails
```

---

## System Detection Flow (How It Works)

```
Every 60 seconds:
  1. Collect metrics (CPU, memory, request rate, errors)
  2. Scan logs for exceptions
  3. Calculate health score (0.0-1.0)
  
If health score drops:
  1. Fetch recent git commits
  2. Identify which change caused issue
  3. Classify: Code bug OR infrastructure issue
  4. Create/execute fix
  5. Monitor recovery
  6. Report in dashboard
```

---

## Example: Real Payment Service

```yaml
system:
  name: payment-service
  
thresholds:
  cpu_percent: 85          # Payment services can tolerate higher CPU
  memory_percent: 90
  error_rate: 0.02         # Must be strict: 2% max errors
  response_time_ms: 3000   # Transactions should be fast
  
deployment:
  replicas:
    min: 3                 # Always have 3 instances
    max: 20                # Scale up to 20 if needed
    target_cpu_percent: 70 # Stay at 70% CPU for headroom
    
recovery:
  code_fixes: true         # Auto-patch bugs
  operational_fixes: true  # Auto-scale/restart
```

---

## Minimum Requirements

Your app needs:
- ✅ Metrics endpoint (or structured logs)
- ✅ Git repository with history
- ✅ Deployment capability (K8s, Docker, or SSH)
- ✅ Read/write permissions via token or SSH

---

## For More Details

See: `INTEGRATION_GUIDE.md` (complete guide with examples)

