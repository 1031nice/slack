#!/bin/bash

# Multi-Server Start Script for v0.3
# Starts 3 backend servers on ports 8080, 8081, 8082 with Nginx load balancer

set -e

SLACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_PLATFORM_DIR="$SLACK_DIR/../auth-platform"

echo "🚀 Starting Multi-Server Slack App (v0.3)..."
echo "   - Backend Server 1: port 9000"
echo "   - Backend Server 2: port 9001"
echo "   - Backend Server 3: port 9002"
echo "   - Nginx Load Balancer: port 80"
echo ""

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

# Start Slack infrastructure (PostgreSQL, Redis, Nginx)
echo ""
echo "📦 Starting Slack infrastructure (PostgreSQL, Redis, Nginx)..."
docker-compose up -d

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

# Wait for Redis to be ready
echo "⏳ Waiting for Redis to be ready..."
COUNTER=0
until $DOCKER_COMPOSE_CMD exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; do
    sleep 1
    COUNTER=$((COUNTER + 1))
    if [ $COUNTER -gt 30 ]; then
        echo "❌ Redis failed to start within 30 seconds"
        exit 1
    fi
done
echo "✅ Redis is ready"

# Check if backend directory exists
if [ ! -d "backend" ]; then
    echo "❌ Error: backend directory not found"
    exit 1
fi

# Start Backend Server 1 (port 9000)
echo ""
echo "⚙️  Starting Backend Server 1 (port 9000)..."
cd backend
export SPRING_PROFILES_ACTIVE=9000
./gradlew bootRun > ../slack-backend-9000.log 2>&1 &
BACKEND_9000_PID=$!
echo $BACKEND_9000_PID > ../slack-backend-9000.pid
unset SPRING_PROFILES_ACTIVE
cd ..

# Start Backend Server 2 (port 9001)
echo "⚙️  Starting Backend Server 2 (port 9001)..."
cd backend
export SPRING_PROFILES_ACTIVE=9001
./gradlew bootRun > ../slack-backend-9001.log 2>&1 &
BACKEND_9001_PID=$!
echo $BACKEND_9001_PID > ../slack-backend-9001.pid
unset SPRING_PROFILES_ACTIVE
cd ..

# Start Backend Server 3 (port 9002)
echo "⚙️  Starting Backend Server 3 (port 9002)..."
cd backend
export SPRING_PROFILES_ACTIVE=9002
./gradlew bootRun > ../slack-backend-9002.log 2>&1 &
BACKEND_9002_PID=$!
echo $BACKEND_9002_PID > ../slack-backend-9002.pid
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

echo ""
echo "📝 Process IDs saved:"
echo "   - Backend 9000: slack-backend-9000.pid (PID: $BACKEND_9000_PID)"
echo "   - Backend 9001: slack-backend-9001.pid (PID: $BACKEND_9001_PID)"
echo "   - Backend 9002: slack-backend-9002.pid (PID: $BACKEND_9002_PID)"
echo "   - Frontend: slack-frontend.pid (PID: $FRONTEND_PID)"
echo ""
echo "📊 Logs are written to:"
echo "   - slack-backend-9000.log"
echo "   - slack-backend-9001.log"
echo "   - slack-backend-9002.log"
echo "   - slack-frontend.log"

# Wait a bit and check if services are starting
echo ""
echo "⏳ Waiting for services to start..."
sleep 10

# Check if all backend servers are running
if ps -p $BACKEND_9000_PID > /dev/null; then
    echo "✅ Backend Server 1 (9000) is running (PID: $BACKEND_9000_PID)"
else
    echo "❌ Backend Server 1 (9000) failed to start"
fi

if ps -p $BACKEND_9001_PID > /dev/null; then
    echo "✅ Backend Server 2 (9001) is running (PID: $BACKEND_9001_PID)"
else
    echo "❌ Backend Server 2 (9001) failed to start"
fi

if ps -p $BACKEND_9002_PID > /dev/null; then
    echo "✅ Backend Server 3 (9002) is running (PID: $BACKEND_9002_PID)"
else
    echo "❌ Backend Server 3 (9002) failed to start"
fi

if ps -p $FRONTEND_PID > /dev/null; then
    echo "✅ Frontend is running (PID: $FRONTEND_PID)"
else
    echo "❌ Frontend failed to start"
fi

echo ""
echo "🎉 Multi-Server Slack App started successfully!"
echo ""
echo "📍 All Services:"
echo "   Auth Platform:"
echo "     - Redis:           localhost:6379"
echo "     - OAuth2 Server:   http://localhost:8081"
echo "     - Resource Server: http://localhost:8082"
echo "   Slack Infrastructure:"
echo "     - PostgreSQL:      localhost:5432"
echo "     - Redis:           localhost:6380"
echo "     - Nginx LB:        http://localhost:80"
echo "   Slack Backend Servers:"
echo "     - Server 1:        http://localhost:9000"
echo "     - Server 2:        http://localhost:9001"
echo "     - Server 3:        http://localhost:9002"
echo "   Slack Frontend:"
echo "     - Frontend App:    http://localhost:3000"
echo ""
echo "🌐 Access the application via Nginx: http://localhost"
echo "   (Nginx will load balance requests across 3 backend servers)"
echo ""
echo "🔍 To check logs:"
echo "   tail -f slack-backend-9000.log"
echo "   tail -f slack-backend-9001.log"
echo "   tail -f slack-backend-9002.log"
echo "   tail -f slack-frontend.log"
echo ""
echo "🛑 To stop all services, run: ./stop-multi-server.sh"

