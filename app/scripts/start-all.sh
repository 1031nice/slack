#!/bin/bash

# Slack App Complete Start Script
# This script starts all required services: Auth Platform + Slack services

set -e

SLACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_PLATFORM_DIR="$SLACK_DIR/../auth-platform"

echo "🚀 Starting Slack App with all dependencies..."

# Check if auth-platform directory exists
if [ ! -d "$AUTH_PLATFORM_DIR" ]; then
    echo "❌ Error: Auth Platform directory not found at $AUTH_PLATFORM_DIR"
    exit 1
fi

# Check if auth-platform start script exists
if [ ! -f "$AUTH_PLATFORM_DIR/start.sh" ]; then
    echo "❌ Error: Auth Platform start script not found at $AUTH_PLATFORM_DIR/start.sh"
    exit 1
fi

# Start Auth Platform first
echo "🔐 Starting Auth Platform services..."
cd "$AUTH_PLATFORM_DIR"
./start.sh

# Return to slack directory
cd "$SLACK_DIR"

# Clean up any existing containers before starting (let docker-compose handle networks)
echo "🧹 Cleaning up any existing Docker containers..."
DOCKER_COMPOSE_CMD=""
if docker info >/dev/null 2>&1; then
    if command -v docker-compose >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker-compose"
    elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker compose"
    fi
    
    if [ -n "$DOCKER_COMPOSE_CMD" ]; then
        # Just run down - docker-compose will handle networks automatically
        # DO NOT manually remove networks to avoid Docker engine issues
        $DOCKER_COMPOSE_CMD down --remove-orphans 2>/dev/null || true
    fi
fi

# Start Slack infrastructure
echo "📦 Starting Slack infrastructure (PostgreSQL, Redis)..."
if [ -z "$DOCKER_COMPOSE_CMD" ]; then
    echo "❌ Error: Docker Compose not found"
    exit 1
fi
$DOCKER_COMPOSE_CMD up -d

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
COUNTER=0
DOCKER_COMPOSE_CMD="docker-compose"
if ! command -v docker-compose >/dev/null 2>&1 && command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE_CMD="docker compose"
fi
until $DOCKER_COMPOSE_CMD exec -T postgres pg_isready -U slack_user -d slack_db 2>/dev/null; do
    sleep 1
    COUNTER=$((COUNTER + 1))
    if [ $COUNTER -gt 30 ]; then
        echo "❌ PostgreSQL failed to start within 30 seconds"
        exit 1
    fi
done
echo "✅ PostgreSQL is ready"

# Check if backend directory exists
if [ ! -d "backend" ]; then
    echo "❌ Error: backend directory not found"
    exit 1
fi

# Start Backend
echo "⚙️  Starting Slack Backend (port 9000)..."
cd backend
export SPRING_PROFILES_ACTIVE=9000
./gradlew bootRun > ../slack-backend.log 2>&1 &
BACKEND_PID=$!
echo $BACKEND_PID > ../slack-backend.pid
unset SPRING_PROFILES_ACTIVE
cd ..

# Check if frontend directory exists
if [ ! -d "frontend" ]; then
    echo "❌ Error: frontend directory not found"
    exit 1
fi

# Install frontend dependencies if needed
if [ ! -d "frontend/node_modules" ]; then
    echo "📦 Installing frontend dependencies..."
    cd frontend
    npm install
    cd ..
fi

# Start Frontend
echo "🎨 Starting Slack Frontend (port 3000)..."
cd frontend
npm run dev > ../slack-frontend.log 2>&1 &
FRONTEND_PID=$!
echo $FRONTEND_PID > ../slack-frontend.pid
cd ..

echo "📝 Process IDs saved to slack-backend.pid and slack-frontend.pid"
echo "📊 Logs are written to slack-backend.log and slack-frontend.log"

# Wait a bit and check if services are starting
echo "⏳ Waiting for Slack services to start..."
sleep 5

if ps -p $BACKEND_PID > /dev/null; then
    echo "✅ Slack Backend is running (PID: $BACKEND_PID)"
else
    echo "❌ Slack Backend failed to start"
fi

if ps -p $FRONTEND_PID > /dev/null; then
    echo "✅ Slack Frontend is running (PID: $FRONTEND_PID)"
else
    echo "❌ Slack Frontend failed to start"
fi

echo ""
echo "🎉 Slack App started successfully!"
echo "📍 All Services:"
echo "   Auth Platform:"
echo "     - Redis:           localhost:6379"
echo "     - OAuth2 Server:   http://localhost:8081"
echo "     - Resource Server: http://localhost:8082"
echo "   Slack Services:"
echo "     - PostgreSQL:      localhost:5432"
echo "     - Backend API:     http://localhost:9000"
echo "     - Frontend App:    http://localhost:3000"
echo ""
echo "🌐 Open your browser and go to: http://localhost:3000"
echo ""
echo "🔍 To check logs:"
echo "   tail -f slack-backend.log"
echo "   tail -f slack-frontend.log"
echo "   tail -f $AUTH_PLATFORM_DIR/oauth2-server.log"
echo "   tail -f $AUTH_PLATFORM_DIR/resource-server.log"
echo ""
echo "🛑 To stop all services, run: ./stop-all.sh"