#!/bin/bash

# Slack Dev Mode Start Script
# Auth Platform 없이 Slack만 시작합니다 (개발용 간단 로그인 사용)

set -e

SLACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🚀 Starting Slack App in DEV mode (without Auth Platform)..."
echo "⚠️  Dev mode uses simple JWT authentication (username only)"

cd "$SLACK_DIR"

# Clean up any existing containers
echo "🧹 Cleaning up any existing Docker containers..."
DOCKER_COMPOSE_CMD=""
if docker info >/dev/null 2>&1; then
    if command -v docker-compose >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker-compose"
    elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker compose"
    fi

    if [ -n "$DOCKER_COMPOSE_CMD" ]; then
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
until $DOCKER_COMPOSE_CMD exec -T postgres pg_isready -U slack_user -d slack_db 2>/dev/null; do
    sleep 1
    COUNTER=$((COUNTER + 1))
    if [ $COUNTER -gt 30 ]; then
        echo "❌ PostgreSQL failed to start within 30 seconds"
        exit 1
    fi
done
echo "✅ PostgreSQL is ready"

# Start Backend with dev profile
echo "⚙️  Starting Slack Backend in DEV mode (port 9000)..."
cd backend
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun > ../slack-backend.log 2>&1 &
BACKEND_PID=$!
echo $BACKEND_PID > ../slack-backend.pid
unset SPRING_PROFILES_ACTIVE
cd ..

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

# Wait and check if services are starting
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
echo "🎉 Slack App started successfully in DEV mode!"
echo "📍 Services:"
echo "   - PostgreSQL:   localhost:5432"
echo "   - Redis:        localhost:6379"
echo "   - Backend API:  http://localhost:9000"
echo "   - Frontend App: http://localhost:3000"
echo ""
echo "🔐 Dev Mode Login:"
echo "   - No Auth Platform required!"
echo "   - Use dev login page: http://localhost:3000/dev-login"
echo "   - Just enter any username to get a JWT token"
echo ""
echo "🔍 To check logs:"
echo "   tail -f slack-backend.log"
echo "   tail -f slack-frontend.log"
echo ""
echo "🛑 To stop all services, run: ./stop-all.sh"
