#!/bin/bash

# Slack App Complete Stop Script
# This script stops all services: Slack services + Auth Platform

SLACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_PLATFORM_DIR="$SLACK_DIR/../auth-platform"

echo "🛑 Stopping Slack App and all dependencies..."

cd "$SLACK_DIR"

# Stop Slack Backend
if [ -f "slack-backend.pid" ]; then
    BACKEND_PID=$(cat slack-backend.pid)
    if ps -p $BACKEND_PID > /dev/null; then
        echo "⚙️  Stopping Slack Backend (PID: $BACKEND_PID)..."
        kill $BACKEND_PID
        echo "✅ Slack Backend stopped"
    else
        echo "⚠️  Slack Backend is not running"
    fi
    rm -f slack-backend.pid
else
    echo "⚠️  slack-backend.pid not found"
fi

# Stop Slack Frontend
if [ -f "slack-frontend.pid" ]; then
    FRONTEND_PID=$(cat slack-frontend.pid)
    if ps -p $FRONTEND_PID > /dev/null; then
        echo "🎨 Stopping Slack Frontend (PID: $FRONTEND_PID)..."
        kill $FRONTEND_PID
        echo "✅ Slack Frontend stopped"
    else
        echo "⚠️  Slack Frontend is not running"
    fi
    rm -f slack-frontend.pid
else
    echo "⚠️  slack-frontend.pid not found"
fi

# Stop Slack infrastructure
echo "📦 Stopping Slack infrastructure..."
docker-compose down

# Stop Auth Platform
if [ -d "$AUTH_PLATFORM_DIR" ] && [ -f "$AUTH_PLATFORM_DIR/stop.sh" ]; then
    echo "🔐 Stopping Auth Platform services..."
    cd "$AUTH_PLATFORM_DIR"
    ./stop.sh
else
    echo "⚠️  Auth Platform stop script not found"
fi

cd "$SLACK_DIR"

echo ""
echo "🎉 All services stopped successfully!"
echo ""
echo "🧹 Clean up:"
echo "   - Log files (slack-backend.log, slack-frontend.log) are preserved"
echo "   - To remove logs: rm -f *.log"