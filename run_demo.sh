#!/bin/bash

# Self-Healing Dashboard Demo Runner
# Compiles and runs WorkingDemo with comprehensive self-healing data

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build/classes"
SRC_DIR="$SCRIPT_DIR/src/main/java"
WORKING_DEMO_CLASS="com.blacklight.uac.demo.WorkingDemo"
LOCAL_MONITOR_CLASS="com.blacklight.uac.demo.LocalSystemsMonitorDemo"
DEMO_CLASS="$WORKING_DEMO_CLASS"
LOG_FILE="/tmp/uac-demo.log"

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║           SELF-HEALING DASHBOARD - DEMO RUNNER                 ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
}

print_step() {
    echo -e "${GREEN}✓${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

cleanup_old_process() {
    print_info "Checking for old demo processes..."
    if pgrep -f "$DEMO_CLASS" > /dev/null; then
        print_warning "Found existing process for $DEMO_CLASS, terminating..."
        pkill -f "$DEMO_CLASS" 2>/dev/null || true
        sleep 1
        print_step "Old process terminated"
    fi
}

# Stop both modes so switching is predictable.
stop_all_modes() {
    for cls in "$WORKING_DEMO_CLASS" "$LOCAL_MONITOR_CLASS"; do
        if pgrep -f "$cls" > /dev/null; then
            pkill -f "$cls" 2>/dev/null || true
        fi
    done
}

compile_sources() {
    print_info "Compiling Java sources..."

    if [ ! -d "$BUILD_DIR" ]; then
        mkdir -p "$BUILD_DIR"
        print_step "Created build directory"
    fi

    java_files=$(find "$SRC_DIR" -name "*.java" -type f | tr '\n' ' ')

    if [ -z "$java_files" ]; then
        print_error "No Java source files found in $SRC_DIR"
        exit 1
    fi

    if javac -d "$BUILD_DIR" $java_files 2>&1 | grep -q "error:"; then
        print_error "Compilation failed"
        javac -d "$BUILD_DIR" $java_files 2>&1 | grep "error:"
        exit 1
    fi

    print_step "Compilation successful ($(find "$SRC_DIR" -name "*.java" -type f | wc -l) files)"
}

verify_demo_class() {
    print_info "Verifying demo class..."

    class_rel=$(echo "$DEMO_CLASS" | tr '.' '/')
    class_path="${BUILD_DIR}/${class_rel}.class"
    if [ ! -f "$class_path" ]; then
        print_error "Class not found at $class_path"
        exit 1
    fi

    print_step "Demo class verified"
}

start_demo() {
    print_info "Starting demo class: $DEMO_CLASS"

    if [ "$DEMO_CLASS" = "$LOCAL_MONITOR_CLASS" ] && [ "$REAL_PR_MODE" = true ]; then
        UAC_REAL_PR=true java -cp "$BUILD_DIR" "$DEMO_CLASS" > "$LOG_FILE" 2>&1 &
    else
        java -cp "$BUILD_DIR" "$DEMO_CLASS" > "$LOG_FILE" 2>&1 &
    fi
    DEMO_PID=$!

    print_step "Demo started (PID: $DEMO_PID)"

    sleep 3

    if ! kill -0 $DEMO_PID 2>/dev/null; then
        print_error "Demo process died immediately. Check log:"
        tail -20 "$LOG_FILE"
        exit 1
    fi

    print_step "Demo process is running"
}

show_dashboard_info() {
    echo ""
    echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✓ DASHBOARD IS READY${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${BLUE}📊 Web Dashboard:${NC}"
    echo -e "   🌐 URL: ${YELLOW}http://localhost:8888${NC}"
    echo ""
    echo -e "${BLUE}📋 Features:${NC}"
    echo -e "   • 4 diverse systems with realistic data"
    echo -e "   • 25 healing flows with complete 5-phase details"
    echo -e "   • 35 related alarms with different severities"
    echo -e "   • Interactive filtering by fix type"
    echo -e "   • Detailed anomaly, fix, and MCP server tracking"
    echo ""
    echo -e "${BLUE}🎯 Quick Start:${NC}"
    echo -e "   1. Open ${YELLOW}http://localhost:8888${NC} in your browser"
    echo -e "   2. Select a system from the sidebar"
    echo -e "   3. Click 'Code Fixes' or 'Ops Fixes' to filter"
    echo -e "   4. Click any fix or alarm to see full details"
    echo ""
    echo -e "${BLUE}📋 Mode:${NC}"
    if [ "$DEMO_CLASS" = "$LOCAL_MONITOR_CLASS" ]; then
        echo -e "   • Local Systems Monitor (config/systems/*.yaml)"
        echo -e "   • Attaches already-running local apps dynamically"
        echo -e "   • Polls health endpoints + tails app logs"
        echo -e "   • Generates alarms and healing flows live"
        if [ "$REAL_PR_MODE" = true ]; then
            echo -e "   • Real PR mode: ${YELLOW}ENABLED${NC} (git push + gh pr create)"
        else
            echo -e "   • Real PR mode: DISABLED (simulated PR ids)"
        fi
    else
        echo -e "   • Working demo data mode"
        echo -e "   • Seeded systems and flows"
        echo -e "   • Interactive filtering and details"
    fi
    echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
    echo ""
}

show_help() {
    cat << EOF

Usage: $0 [OPTIONS]

OPTIONS:
    -h, --help      Show this help message
    --local-systems Run dynamic monitor using config/systems/*.yaml
    --real-pr       Enable real PR flow in local-systems mode (sets UAC_REAL_PR=true)
    -c, --clean     Clean build directory before compiling
    -l, --logs      Show demo logs in real-time (tail -f)
    -s, --stop      Stop running demo
    -r, --restart   Stop and restart demo
    -v, --verify    Only verify demo is running (no start)

EXAMPLES:
    # Run seeded working demo mode
    $0

    # Run dynamic local systems mode
    $0 --local-systems

    # Run local systems mode with real PR creation
    $0 --local-systems --real-pr

    # Clean build and restart
    $0 --clean --restart

    # Show logs in real-time
    $0 --logs

    # Stop demo
    $0 --stop

    # Verify running
    $0 --verify

EOF
}

stop_demo() {
    print_info "Stopping demo..."
    if pgrep -f "$WORKING_DEMO_CLASS" > /dev/null || pgrep -f "$LOCAL_MONITOR_CLASS" > /dev/null; then
        stop_all_modes
        sleep 1
        print_step "Demo stopped"
    else
        print_warning "No running demo found"
    fi
}

verify_running() {
    if pgrep -f "$WORKING_DEMO_CLASS" > /dev/null || pgrep -f "$LOCAL_MONITOR_CLASS" > /dev/null; then
        if pgrep -f "$LOCAL_MONITOR_CLASS" > /dev/null; then
            active_class="$LOCAL_MONITOR_CLASS"
            active_log="/tmp/uac-local-monitor.log"
        else
            active_class="$WORKING_DEMO_CLASS"
            active_log="/tmp/uac-demo.log"
        fi
        pid=$(pgrep -f "$active_class" | head -1)
        print_step "Demo is running (PID: $pid)"
        echo ""
        echo -e "${BLUE}Class:${NC} ${YELLOW}$active_class${NC}"
        echo -e "${BLUE}Dashboard:${NC} ${YELLOW}http://localhost:8888${NC}"
        echo -e "${BLUE}Logs:${NC} ${YELLOW}tail -f $active_log${NC}"
        return 0
    else
        print_error "Demo is not running"
        return 1
    fi
}

show_logs() {
    print_info "Showing demo logs (press Ctrl+C to stop)..."
    tail -f "$LOG_FILE"
}

main() {
    CLEAN_BUILD=false
    SHOW_LOGS=false
    STOP_ONLY=false
    RESTART=false
    VERIFY_ONLY=false
    LOCAL_SYSTEMS=false
    REAL_PR_MODE=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            --local-systems)
                LOCAL_SYSTEMS=true
                shift
                ;;
            --real-pr)
                REAL_PR_MODE=true
                shift
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            -l|--logs)
                SHOW_LOGS=true
                shift
                ;;
            -s|--stop)
                STOP_ONLY=true
                shift
                ;;
            -r|--restart)
                RESTART=true
                shift
                ;;
            -v|--verify)
                VERIFY_ONLY=true
                shift
                ;;
            *)
                print_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done

    if [ "$LOCAL_SYSTEMS" = true ]; then
        DEMO_CLASS="$LOCAL_MONITOR_CLASS"
        LOG_FILE="/tmp/uac-local-monitor.log"
    else
        DEMO_CLASS="$WORKING_DEMO_CLASS"
        LOG_FILE="/tmp/uac-demo.log"
        if [ "$REAL_PR_MODE" = true ]; then
            print_warning "--real-pr is only used with --local-systems; ignoring in working demo mode"
            REAL_PR_MODE=false
        fi
    fi

    print_header
    echo ""

    if [ "$VERIFY_ONLY" = true ]; then
        verify_running
        exit $?
    fi

    if [ "$SHOW_LOGS" = true ]; then
        show_logs
        exit 0
    fi

    if [ "$STOP_ONLY" = true ]; then
        stop_demo
        exit 0
    fi

    if [ "$RESTART" = true ] || [ "$CLEAN_BUILD" = true ]; then
        stop_demo
        echo ""
    fi

    if [ "$CLEAN_BUILD" = true ]; then
        print_info "Cleaning build directory..."
        rm -rf "$BUILD_DIR"
        print_step "Build directory cleaned"
        echo ""
    fi

    compile_sources
    echo ""

    cleanup_old_process
    echo ""

    verify_demo_class
    echo ""

    start_demo
    echo ""

    show_dashboard_info
}

main "$@"
