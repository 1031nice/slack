#!/bin/bash

# Multi-Server Broadcast Test - Stop Script

set -e

EXPERIMENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$EXPERIMENT_DIR/../../app"

echo "🛑 Stopping Multi-Server Broadcast Test..."

# Stop backend servers (Graceful attempt)
echo "Stopping backend servers (PID check)..."
for port in 9000 9001 9002 9003; do
    PID_FILE="$APP_DIR/slack-backend-$port.pid"
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "  Stopping backend on port $port (PID: $PID)..."
            kill $PID || true
        fi
        rm -f "$PID_FILE"
    fi
done

# Force cleanup using pkill
echo "Cleaning up lingering processes..."
pkill -f "slack-backend" || true
pkill -f "java" || true

# Stop Gradle Daemons
echo "Stopping Gradle daemons..."
cd "$APP_DIR/backend"
./gradlew --stop || true

# Stop Docker services
echo "Stopping Docker services..."
cd "$APP_DIR"
if command -v docker-compose >/dev/null 2>&1; then
    docker-compose down
elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose down
fi

echo "✅ All services stopped and cleaned up"
