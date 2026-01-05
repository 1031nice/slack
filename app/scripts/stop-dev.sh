#!/bin/bash

# Slack App Development Mode Stop Script
# This script stops Slack services WITHOUT Auth Platform
# Safe against Rancher Desktop engine shutdown

# Don't exit on error - we want to clean up as much as possible
set +e

SLACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🛑 Stopping Slack App (Development Mode)..."

cd "$SLACK_DIR"

# Function to kill process on a port (but NOT Docker containers or Docker daemon)
kill_port() {
    local port=$1
    local service_name=$2
    local pids=$(lsof -ti:$port 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "🔪 Killing processes on port $port ($service_name)..."
        # Filter out Docker-related processes to avoid killing Docker engine
        for pid in $pids; do
            # Skip if PID is invalid
            if ! ps -p "$pid" >/dev/null 2>&1; then
                continue
            fi

            # Get process command (works on both Linux and macOS)
            local cmd=""
            if [ "$(uname)" = "Darwin" ]; then
                # macOS
                cmd=$(ps -p "$pid" -o comm= 2>/dev/null || echo "")
            else
                # Linux
                cmd=$(ps -p "$pid" -o comm= 2>/dev/null || echo "")
            fi

            # Get full command line
            local full_cmd=$(ps -p "$pid" -o command= 2>/dev/null || echo "")

            # WHITELIST approach: ONLY kill Java/Node/Gradle processes (our application)
            # This is safer than trying to blacklist everything else
            if echo "$cmd $full_cmd" | grep -qE "(java|node|npm|gradle)" 2>/dev/null; then
                # Double-check it's not a Docker-managed process
                if echo "$cmd $full_cmd" | grep -qiE "(docker|containerd|rancher|lima|qemu)" 2>/dev/null; then
                    echo "   ⚠️  Skipping Docker/Rancher-related Java/Node process (PID: $pid)"
                    continue
                fi

                # On Linux, check cgroup to detect Docker containers
                if [ -f "/proc/$pid/cgroup" ] 2>/dev/null; then
                    if grep -qE "(docker|containerd)" "/proc/$pid/cgroup" 2>/dev/null; then
                        echo "   ⚠️  Skipping Docker container process (PID: $pid)"
                        continue
                    fi
                fi

                echo "   Killing application process PID: $pid ($cmd)"
                kill -9 "$pid" 2>/dev/null || true
            else
                echo "   ⚠️  Skipping non-application process (PID: $pid): $cmd"
            fi
        done
        echo "✅ Port $port cleared"
    else
        echo "ℹ️  Port $port ($service_name) is not in use"
    fi
}

# Stop processes by PID files first (graceful shutdown)
if [ -f "backend/backend.pid" ]; then
    BACKEND_PID=$(cat backend/backend.pid)
    if ps -p $BACKEND_PID > /dev/null 2>&1; then
        echo "⚙️  Stopping Slack Backend (PID: $BACKEND_PID)..."
        kill $BACKEND_PID 2>/dev/null || true
        sleep 2
        # Force kill if still running
        if ps -p $BACKEND_PID > /dev/null 2>&1; then
            kill -9 $BACKEND_PID 2>/dev/null || true
        fi
        echo "✅ Slack Backend stopped"
    fi
    rm -f backend/backend.pid
fi

if [ -f "slack-backend.pid" ]; then
    BACKEND_PID=$(cat slack-backend.pid)
    if ps -p $BACKEND_PID > /dev/null 2>&1; then
        echo "⚙️  Stopping Slack Backend (PID: $BACKEND_PID)..."
        kill $BACKEND_PID 2>/dev/null || true
        sleep 2
        # Force kill if still running
        if ps -p $BACKEND_PID > /dev/null 2>&1; then
            kill -9 $BACKEND_PID 2>/dev/null || true
        fi
        echo "✅ Slack Backend stopped"
    fi
    rm -f slack-backend.pid
fi

if [ -f "slack-frontend.pid" ]; then
    FRONTEND_PID=$(cat slack-frontend.pid)
    if ps -p $FRONTEND_PID > /dev/null 2>&1; then
        echo "🎨 Stopping Slack Frontend (PID: $FRONTEND_PID)..."
        kill $FRONTEND_PID 2>/dev/null || true
        sleep 2
        # Force kill if still running
        if ps -p $FRONTEND_PID > /dev/null 2>&1; then
            kill -9 $FRONTEND_PID 2>/dev/null || true
        fi
        echo "✅ Slack Frontend stopped"
    fi
    rm -f slack-frontend.pid
fi

# Stop Slack infrastructure (Docker containers)
echo ""
echo "📦 Stopping Slack infrastructure (Docker containers)..."
# Check if Docker daemon is running before attempting to stop containers
if docker info >/dev/null 2>&1; then
    DOCKER_COMPOSE_CMD=""
    if command -v docker-compose >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker-compose"
    elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker compose"
    fi

    if [ -n "$DOCKER_COMPOSE_CMD" ]; then
        # Stop containers gracefully - docker-compose down should handle networks automatically
        # DO NOT use --volumes to avoid data loss
        # DO NOT manually remove networks - let docker-compose handle it to avoid Docker engine issues
        $DOCKER_COMPOSE_CMD down --remove-orphans 2>/dev/null || echo "⚠️  docker-compose down failed, continuing..."

        # That's it - don't manually touch networks
        # If networks remain, they're likely in use by other containers or Docker itself
        # Manually removing them can cause Docker engine to become unstable
    else
        echo "⚠️  Docker Compose not found, skipping container shutdown"
    fi
else
    echo "⚠️  Docker daemon is not running, skipping container shutdown"
fi

# Kill processes by port (force cleanup) AFTER containers are stopped
# Only kill application ports, not Docker-managed ports
echo ""
echo "🔪 Force killing processes on application ports..."
kill_port 3000 "Frontend"
kill_port 9000 "Backend API"
# Note: PostgreSQL (5432) and Redis (6380) are managed by Docker,
# so we don't kill them here to avoid affecting Docker engine

echo ""
echo "🎉 Slack App stopped successfully (Dev Mode)!"
echo ""
echo "🧹 Clean up:"
echo "   - Log files (backend.log) are preserved"
echo "   - PID files have been removed"
echo "   - All required ports have been cleared"
echo ""
echo "💡 To remove logs: rm -f backend/backend.log"
echo "💡 Note: Auth Platform is NOT stopped (dev mode doesn't use it)"
echo ""
