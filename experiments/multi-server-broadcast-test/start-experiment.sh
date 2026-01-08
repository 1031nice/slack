#!/bin/bash

# Multi-Server Broadcast Test - Orchestration Script
# Tests Redis-based message broadcasting across multiple backend servers

set -e

# --- Configuration ---
EXPERIMENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$EXPERIMENT_DIR/../../app"
BACKEND_DIR="$APP_DIR/backend"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# --- Helper Functions ---
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

show_help() {
    echo "Usage: ./start-experiment.sh [OPTIONS]"
    echo ""
    echo "Orchestrates the multi-server broadcast test experiment."
    echo ""
    echo "Options:"
    echo "  --all            Run full experiment: Infra -> Backend -> Test (Default)"
    echo "  --infra-only     Start only infrastructure (Postgres, Redis, Kafka)"
    echo "  --backend-only   Start only backend servers (assumes infra is ready)"
    echo "  --test-only      Run only the Node.js test script (assumes servers are ready)"
    echo "  --skip-infra     Skip infrastructure startup step"
    echo "  --skip-build     Skip Gradle build step (use existing jar)"
    echo "  --help, -h       Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./start-experiment.sh --test-only    # Run test again with different JS config"
    echo "  ./start-experiment.sh --skip-build   # Restart backends without recompiling"
}

check_prerequisites() {
    if [ ! -d "$APP_DIR" ]; then
        log_error "App directory not found at $APP_DIR"
        exit 1
    fi

    # Check for docker-compose
    if command -v docker-compose >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker-compose"
    elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker compose"
    else
        log_error "Docker Compose not found"
        exit 1
    fi
}

start_infra() {
    log_info "📦 Step 1: Starting infrastructure (PostgreSQL, Redis)..."
    cd "$APP_DIR"
    $DOCKER_COMPOSE_CMD up -d postgres redis

    log_info "⏳ Waiting for PostgreSQL..."
    COUNTER=0
    until $DOCKER_COMPOSE_CMD exec -T postgres pg_isready -U slack_user -d slack_db 2>/dev/null; do
        sleep 1
        COUNTER=$((COUNTER + 1))
        if [ $COUNTER -gt 30 ]; then
            log_error "PostgreSQL failed to start"
            exit 1
        fi
    done
    log_info "✅ PostgreSQL ready"

    log_info "⏳ Waiting for Redis..."
    COUNTER=0
    until $DOCKER_COMPOSE_CMD exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; do
        sleep 1
        COUNTER=$((COUNTER + 1))
        if [ $COUNTER -gt 30 ]; then
            log_error "Redis failed to start"
            exit 1
        fi
    done
    log_info "✅ Redis ready"

    log_info "⏳ Starting Kafka (optional)..."
    $DOCKER_COMPOSE_CMD up -d kafka || log_warn "Kafka failed to start (not critical for test)"

    log_info "⏳ Waiting for Kafka port 9092..."
    COUNTER=0
    until nc -z localhost 9092 > /dev/null 2>&1; do
        sleep 2
        COUNTER=$((COUNTER + 1))
        if [ $COUNTER -gt 30 ]; then
            log_warn "Kafka port 9092 not ready after 60s, continuing anyway..."
            break
        fi
    done
    log_info "✅ Kafka port reachable"
    
    # Give Kafka extra time to stabilize metadata
    log_info "⏳ Giving Kafka an extra 10s to fully initialize..."
    sleep 10
}

start_backend() {
    SKIP_BUILD=$1
    log_info "⚙️  Step 2: Starting 4 backend servers (ports 9000-9003)..."
    cd "$BACKEND_DIR"

    # Stop existing backends first
    log_info "  Stopping any existing backend processes..."
    pkill -f "slack-backend" || true

    if [ "$SKIP_BUILD" != "true" ]; then
        log_info "  Compiling backend..."
        ./gradlew clean classes -x test
    else
        log_info "  Skipping build step..."
    fi

    # Clean up old PIDs
    rm -f ../slack-backend-*.pid

    for port in 9000 9001 9002 9003; do
        log_info "  Starting Backend (port $port)..."
        export SPRING_PROFILES_ACTIVE=dev,$port
        # Note: Using nohup/backgrounding carefully
        ./gradlew bootRun > ../slack-backend-$port.log 2>&1 &
        PID=$!
        echo $PID > ../slack-backend-$port.pid
        unset SPRING_PROFILES_ACTIVE
    done

    cd "$EXPERIMENT_DIR"
    wait_for_backends
}

wait_for_backends() {
    log_info "⏳ Step 3: Waiting for all backends to be ready..."
    for port in 9000 9001 9002 9003; do
        echo -n "  Checking port $port... "
        COUNTER=0
        until curl -s http://localhost:$port/actuator/health > /dev/null 2>&1; do
            sleep 2
            COUNTER=$((COUNTER + 1))
            if [ $COUNTER -gt 60 ]; then
                echo ""
                log_error "Backend on port $port failed to start (timeout after 120s)"
                log_error "Check logs: tail -n 50 $APP_DIR/slack-backend-$port.log"
                exit 1
            fi
        done
        echo -e "${GREEN}OK${NC}"
    done
    log_info "✅ All backends ready"
}

run_test() {
    log_info "🚀 Step 4: Running broadcast test..."
    cd "$EXPERIMENT_DIR"

    if [ ! -d "node_modules" ]; then
        log_info "📦 Installing Node.js dependencies..."
        npm install
    fi

    echo ""
    node run-test.js
    echo ""
    log_info "✅ Experiment completed!"
    echo "   View results: cat logs/results.json"
}

# --- Main Execution Flow ---

# Default flags
RUN_INFRA=true
RUN_BACKEND=true
RUN_TEST=true
SKIP_BUILD=false

# Parse arguments
if [ $# -eq 0 ]; then
    # No args -> Run All
    :
else
    # Parse flags
    while [[ $# -gt 0 ]]; do
        case $1 in
            --all)
                RUN_INFRA=true; RUN_BACKEND=true; RUN_TEST=true
                shift ;;
            --infra-only)
                RUN_INFRA=true; RUN_BACKEND=false; RUN_TEST=false
                shift ;;
            --backend-only)
                RUN_INFRA=false; RUN_BACKEND=true; RUN_TEST=false
                shift ;;
            --test-only)
                RUN_INFRA=false; RUN_BACKEND=false; RUN_TEST=true
                shift ;;
            --skip-infra)
                RUN_INFRA=false
                shift ;;
            --skip-build)
                SKIP_BUILD=true
                shift ;;
            --help|-h)
                show_help
                exit 0 ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1 ;;
        esac
    done
fi

check_prerequisites

if [ "$RUN_INFRA" = true ]; then
    start_infra
fi

if [ "$RUN_BACKEND" = true ]; then
    start_backend "$SKIP_BUILD"
fi

if [ "$RUN_TEST" = true ]; then
    run_test
fi

