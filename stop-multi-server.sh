#!/bin/bash

# Multi-Server Stop Script for v0.3
# Stops all 3 backend servers and related services

# Don't exit on error - we want to clean up as much as possible
set +e

SLACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_PLATFORM_DIR="$SLACK_DIR/../auth-platform"

echo "🛑 Stopping Multi-Server Slack App..."

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
            
            # Skip if it's a Docker-related process (Docker daemon, containerd, etc.)
            if echo "$cmd $full_cmd" | grep -qiE "(docker|containerd|dockerd|com\.docker\.|rancher|vpnkit)" >/dev/null 2>&1; then
                echo "   ⚠️  Skipping Docker-related process (PID: $pid)"
                continue
            fi
            
            # On Linux, check cgroup to detect Docker containers
            if [ -f "/proc/$pid/cgroup" ] 2>/dev/null; then
                if grep -qE "(docker|containerd)" "/proc/$pid/cgroup" 2>/dev/null; then
                    echo "   ⚠️  Skipping Docker container process (PID: $pid)"
                    continue
                fi
            fi
            
            # Safe to kill - it's not a Docker process
            echo "   Killing PID: $pid"
            kill -9 "$pid" 2>/dev/null || true
        done
        echo "✅ Port $port cleared"
    else
        echo "ℹ️  Port $port ($service_name) is not in use"
    fi
}

# Stop backend servers by PID files
for port in 8080 8081 8082; do
    PID_FILE="slack-backend-${port}.pid"
    if [ -f "$PID_FILE" ]; then
        BACKEND_PID=$(cat "$PID_FILE")
        if ps -p $BACKEND_PID > /dev/null 2>&1; then
            echo "⚙️  Stopping Backend Server (port ${port}, PID: $BACKEND_PID)..."
            kill $BACKEND_PID 2>/dev/null || true
            sleep 2
            # Force kill if still running
            if ps -p $BACKEND_PID > /dev/null 2>&1; then
                kill -9 $BACKEND_PID 2>/dev/null || true
            fi
            echo "✅ Backend Server (port ${port}) stopped"
        fi
        rm -f "$PID_FILE"
    fi
done

# Stop frontend
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

# Stop Slack infrastructure (Docker containers) FIRST
echo ""
echo "📦 Stopping Slack infrastructure (Docker containers)..."
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
    fi
fi

# Kill processes by port (force cleanup) AFTER containers are stopped
echo ""
echo "🔪 Force killing processes on application ports..."
kill_port 3000 "Frontend"
kill_port 8080 "Backend Server 1"
kill_port 8081 "Backend Server 2"
kill_port 8082 "Backend Server 3"
kill_port 80 "Nginx Load Balancer"
# Note: PostgreSQL (5432) and Redis (6379) are managed by Docker,
# so we don't kill them here to avoid affecting Docker engine

# Stop Auth Platform
if [ -d "$AUTH_PLATFORM_DIR" ] && [ -f "$AUTH_PLATFORM_DIR/stop.sh" ]; then
    echo ""
    echo "🔐 Stopping Auth Platform services..."
    cd "$AUTH_PLATFORM_DIR"
    ./stop.sh 2>/dev/null || true
    cd "$SLACK_DIR"
else
    echo "ℹ️  Auth Platform stop script not found, skipping"
fi

echo ""
echo "🎉 All services stopped successfully!"
echo ""
echo "🧹 Clean up:"
echo "   - Log files (slack-backend-*.log, slack-frontend.log) are preserved"
echo "   - PID files have been removed"
echo "   - All required ports have been cleared"
echo ""
echo "💡 To remove logs: rm -f slack-backend-*.log slack-frontend.log"

