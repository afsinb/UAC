# DEVELOPER_GUIDE.md - Setup, Running & Troubleshooting

## Quick Start

```bash
# 1. Navigate to project
cd /Users/afsinbuyuksarac/development/UAC

# 2. Run demo (compiles + starts)
./run_demo.sh

# 3. Open browser
# http://localhost:8888
```

## Running the Demo

### run_demo.sh Script

The `run_demo.sh` script handles compilation, process management, and dashboard startup.

**Basic Usage:**
```bash
./run_demo.sh              # Compile & start
./run_demo.sh --help       # Show options
./run_demo.sh --verify     # Check if running
./run_demo.sh --stop       # Stop demo
./run_demo.sh --restart    # Stop & restart
./run_demo.sh --clean      # Clean build & restart
./run_demo.sh --logs       # View real-time logs
```

**What It Does:**
1. Finds all 112 Java source files
2. Compiles with `javac` to `build/classes/`
3. Kills any old WorkingDemo processes
4. Starts new demo in background
5. Waits 3 seconds for initialization
6. Displays dashboard URL

**Output Example:**
```
✓ Compilation successful (112 files)
✓ Old process terminated
✓ Demo started (PID: 43412)
✓ Demo process is running

════════════════════════════════════
✓ DASHBOARD IS READY
════════════════════════════════════

📊 Web Dashboard:
   🌐 URL: http://localhost:8888
```

### Manual Compilation & Running

**Compile:**
```bash
javac -d build/classes $(find src/main/java -name "*.java" | tr '\n' ' ')
```

**Run:**
```bash
java -cp build/classes com.blacklight.uac.demo.WorkingDemo
```

**With Output to File:**
```bash
java -cp build/classes com.blacklight.uac.demo.WorkingDemo > /tmp/dashboard.log 2>&1 &
```

## Dashboard Access

**URL:** http://localhost:8888

**Default Port:** 8888 (SimpleDashboard runs here)

**Note:** If you see "Connection refused", the demo hasn't started yet. Wait 5 seconds and try again.

## Logs

**Log File Location:** `/tmp/uac-demo.log`

**View Real-Time Logs:**
```bash
tail -f /tmp/uac-demo.log
```

**View Last 50 Lines:**
```bash
tail -50 /tmp/uac-demo.log
```

**Clear Logs:**
```bash
rm /tmp/uac-demo.log
```

**Log Contains:**
- System startup messages
- System registration
- Data loading for each system
- Flow creation
- Alarm creation
- API request logging

## Troubleshooting

### Demo won't start

**Check if Java is installed:**
```bash
which java
java -version
```

**Check compilation errors:**
```bash
javac -d build/classes $(find src/main/java -name "*.java" | tr '\n' ' ') 2>&1 | grep error
```

**Check if port 8888 is in use:**
```bash
lsof -i :8888
```

**Workaround:** Change port in SimpleDashboard or kill existing process:
```bash
kill -9 <PID>
```

### Port 8888 already in use

**Find process using port:**
```bash
lsof -i :8888
```

**Kill process:**
```bash
kill -9 <PID>
```

**Or use the script:**
```bash
./run_demo.sh --stop
./run_demo.sh --restart
```

### Dashboard not loading

**Verify demo is running:**
```bash
./run_demo.sh --verify
```

**Wait 3-5 seconds** - Demo needs time to initialize

**Check logs:**
```bash
tail -20 /tmp/uac-demo.log
```

**Try refreshing browser** - Sometimes JS cache issue

### No data showing in dashboard

**Check demo started successfully:**
```bash
ps aux | grep WorkingDemo | grep -v grep
```

**If not running, restart:**
```bash
./run_demo.sh --restart
```

**Wait 5 seconds** for data to load

**Check logs for errors:**
```bash
grep -i "error\|exception" /tmp/uac-demo.log | head -20
```

### Filtering not working

**Browser console errors?**
- Press F12 to open developer console
- Check for JavaScript errors
- Look for network errors in Network tab

**Try:**
- Hard refresh (Cmd+Shift+R on Mac)
- Clear browser cache
- Try different browser

### Alarms/Flows not showing details

**Check JSON escaping:**
- This was fixed in the code
- Stack traces should display correctly
- Try clicking different flows

**If still broken:**
```bash
./run_demo.sh --restart
```

## File Structure

```
/Users/afsinbuyuksarac/development/UAC/
├── run_demo.sh                    # Demo runner script
├── src/main/java/
│   └── com/blacklight/uac/
│       ├── demo/
│       │   └── WorkingDemo.java   # Demo data generator
│       ├── ui/
│       │   ├── SimpleDashboard.java  # Main dashboard
│       │   └── SelfHealingDashboard.java  # Data model
│       └── ... (other components)
├── build/classes/                 # Compiled classes
├── docs/
│   └── agent_blueprint.md        # Original architecture
└── README.md, ARCHITECTURE.md, FEATURES.md, etc.
```

## Performance

**Initial Startup:**
- Compilation: 5-10 seconds (112 files)
- Startup: 3 seconds
- First dashboard load: <1 second

**Dashboard Performance:**
- Page load: <100ms
- Data refresh: Every 2-3 seconds
- Filter update: Instant

**Memory Usage:**
- Demo process: 300-500 MB
- Dashboard: 50-100 MB
- Browser: Depends on browser

**Log File Growth:**
- ~100KB per hour of operation
- Can be safely deleted anytime

## Customization

### Add More Demo Data

Edit `src/main/java/com/blacklight/uac/demo/WorkingDemo.java`:

1. Add new system:
```java
dataModel.registerSystem("new-service");
dataModel.systems.get("new-service").healthScore = 0.75;
```

2. Add flows and alarms:
```java
SelfHealingDashboard.HealingFlow flow = 
    new SelfHealingDashboard.HealingFlow(id, "CODE_FIX");
flow.anomaly = new SelfHealingDashboard.AnomalyDetails();
// ... set anomaly details
dataModel.addHealingFlow(flow);
```

3. Recompile:
```bash
./run_demo.sh --clean
```

### Change Dashboard Port

Edit `src/main/java/com/blacklight/uac/demo/WorkingDemo.java`:

Find:
```java
SelfHealingDashboard dataModel = new SelfHealingDashboard(8889);
SimpleDashboard dashboard = new SimpleDashboard(dataModel, 8888);
```

Change `8888` to desired port, then recompile.

### Modify Dashboard HTML/CSS/JS

All UI is generated in `SimpleDashboard.java` method `generateHTML()`.

Edit CSS in the `<style>` section or JavaScript in the `<script>` section, then recompile.

## Testing

**Run unit tests:**
```bash
mvn test
```

**Run specific test:**
```bash
mvn test -Dtest=BrainTest
```

**Build JAR:**
```bash
mvn package
```

## Architecture

See ARCHITECTURE.md for system design details.

## Common Issues

| Issue | Solution |
|-------|----------|
| "Connection refused" | Demo not running, wait 5s |
| No data in dashboard | Check logs with `tail -f /tmp/uac-demo.log` |
| Port 8888 in use | `./run_demo.sh --stop` or `kill -9 <PID>` |
| Compilation error | Check Java version (need 19+) |
| Filters not working | Hard refresh browser (Cmd+Shift+R) |
| Details modals blank | Restart demo: `./run_demo.sh --restart` |

## Dependencies

**Java:**
- Java 19+ (compiler and runtime)

**Build Tools:**
- `javac` (Java compiler)
- `bash` (shell script)

**Runtime:**
- Standard Java libraries only
- No external dependencies required

**Browser:**
- Modern browser (Chrome, Firefox, Safari, Edge)
- JavaScript enabled
- HTML5 support

## Environment Setup

**Verify Java:**
```bash
java -version
# Should show Java 19 or higher
```

**Verify Script Permission:**
```bash
ls -la run_demo.sh
# Should show -rwxr-xr-x
# If not: chmod +x run_demo.sh
```

**Check Available Ports:**
```bash
netstat -an | grep 8888
# Should show nothing (port is free)
```

## Next Steps

1. Run demo: `./run_demo.sh`
2. Open dashboard: http://localhost:8888
3. Select a system from sidebar
4. Click filter cards to see different flows
5. Click any flow or alarm to see details
6. Read FEATURES.md for complete feature list

---

**Documentation:**
- README.md - Quick start & overview
- ARCHITECTURE.md - System design
- FEATURES.md - Complete feature list
- DEVELOPER_GUIDE.md (this file) - Setup & troubleshooting

