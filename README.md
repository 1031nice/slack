# Slack Clone - Real-time Messaging System

## Project Overview
Learning-focused Slack clone project emphasizing distributed system design, real-time communication, and performance optimization.

## Architecture Highlights
- **Distributed messaging**: Redis Pub/Sub enables horizontal scaling across multiple servers
- **Trade-offs**: Eventual consistency for read status (performance over strict consistency)
- **Scalability**: Designed for multi-server deployment from the start
- **Performance approach**: 
  - Baseline measurement in v0.1 (single server, no optimization)
  - Relative improvements tracked across versions
  - Key metrics: message throughput, latency, query response time
  - Bottleneck identification and resolution
- **Observability**: Metrics collection (Micrometer + Prometheus) from v0.1

## Tech Stack
- **Backend**: Java 21 + Spring Boot 3.2 + Gradle
- **Frontend**: Next.js 14 + TypeScript
- **Database**: PostgreSQL
- **Cache/Message Broker**: Redis
- **Search Engine**: Elasticsearch (v0.6)
- **Monitoring**: Micrometer + Prometheus
- **Infrastructure**: Docker Compose

## Version Roadmap

**Current Version**: v0.1 (In Progress) 🚧

### v0.1 - MVP (Minimum Viable Product) 🚧
**Goal**: Single workspace, basic real-time messaging
- Basic CRUD APIs (User, Workspace, Channel, Message)
- Single-server WebSocket messaging
- Basic authentication (JWT)
- Default workspace and channel (auto-created on first user registration)
- Simple message sending/receiving UI
- Basic metrics collection setup
- **Baseline performance measurement** (record for future comparison)
  - Message throughput (messages/sec)
  - P50/P95/P99 latency
  - Concurrent WebSocket connections
  - Database query performance
- **Deliverable**: "Real-time messaging in a chat room"

### v0.2 - Multi-workspace & Access Control
**Goal**: Multiple workspaces with permission system
- Workspace/channel CRUD
- RBAC implementation (Owner, Admin, Member)
- Public/Private channel separation
- Workspace invitation flow
- Permission-based API access control
- **Deliverable**: "Create multiple workspaces and control channel access by permissions"

### v0.3 - Distributed Messaging (Advanced)
**Goal**: Scalable multi-server architecture
**Prerequisites**: v0.1, v0.2 completed + single-server load testing to understand limitations
- Redis Pub/Sub for server-to-server communication
- Local setup with 3 servers (ports 8080, 8081, 8082)
- Nginx load balancer configuration
- Message delivery guarantee (ACK mechanism)
- Reconnection handling and missed message recovery
- Message ordering strategy (sequence numbers)
- **Performance test: Verify scaling with 3 servers**
  - Measure actual throughput vs single server
  - Document overhead (network, Redis Pub/Sub)
  - Identify if linear scaling is achieved
- **Deliverable**: "Message delivery to all users across multiple servers"
- **Expected challenges**: Message ordering, handling server failures

### v0.4 - Read Status & Notifications
**Goal**: User engagement features
- Redis Sorted Set-based read status tracking
- Per-channel unread count optimization
- Mention detection and notifications
- Real-time read receipts via WebSocket
- Periodic DB synchronization (eventual consistency)
- **Deliverable**: "Unread message indicators and mention notifications"

### v0.5 - Thread Support
**Goal**: Organized conversations
- Thread creation and replies
- Self-referencing entity design (Message → parentMessage)
- Thread query optimization (@EntityGraph, Batch Fetch)
- Thread depth limitation
- Hot thread caching with Redis
- Thread UI
- **Performance test: Measure query time improvement with caching**
- **Deliverable**: "Reply to messages with threads"

### v0.6 - Search (Optional)
**Goal**: Message discoverability
- Phase 1: PostgreSQL full-text search
- Search UI with highlighting
- Phase 2 (optional): Elasticsearch migration scenario
- **Deliverable**: "Search past messages"

### v1.0 - Production Ready
**Goal**: Performance optimization & stability
- Establish baseline performance metrics (v0.1 single server)
- Load testing to find system breaking point
  - Gradually increase load until performance degradation
  - Identify bottleneck components (DB? WebSocket? Network?)
- Multi-server scaling verification (v0.3)
  - Measure if 3 servers ≈ 3x single server capacity
- Query optimization impact measurement (v0.5)
  - Document before/after performance with caching
- Comprehensive error handling and logging
- Monitoring dashboard setup
- Performance benchmark documentation with test environment specifications
  - Hardware specs (CPU, RAM, network)
  - Test data volume
  - Load testing scenarios
  - Before/after comparison tables
- **Deliverable**: "Performance characteristics documented with reproducible tests"

### Version Completion Checklist
Each version completion should include:
- [ ] Git tag created (e.g., `git tag v0.1`)
- [ ] Version Roadmap updated (🚧 → ✅)
- [ ] Demo GIF/Screenshot added
- [ ] Architecture diagram updated
- [ ] Lessons Learned section updated with challenges and solutions

**Git Branch Strategy:**
```
main (stable releases only)
  ├── v0.1 (tag)
  ├── v0.2 (tag)
  └── v0.3 (tag)

develop (integration branch)
  ├── feature/websocket-messaging
  ├── feature/rbac
  ├── feature/redis-pubsub
  └── feature/read-status
```

## Getting Started

### Prerequisites
- Java 21+
- Node.js 18+
- Docker & Docker Compose

### Quick Start
```bash
# Start infrastructure (database, redis)
docker-compose up -d

# Start backend (from backend directory)
cd backend
./gradlew bootRun

# Start frontend (from frontend directory, in a new terminal)
cd frontend
npm install
npm run dev
```

The backend will be available at `http://localhost:8080`  
The frontend will be available at `http://localhost:3000`

## Project Structure
```
slack/
├── backend/              # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/slack/
│   │   │   │   ├── controller/    # REST controllers
│   │   │   │   ├── service/       # Business logic
│   │   │   │   ├── repository/    # Data access layer
│   │   │   │   ├── domain/        # Entity models
│   │   │   │   └── config/        # Configuration
│   │   │   └── resources/
│   │   │       ├── api/           # OpenAPI specification
│   │   │       └── db/migration/  # Flyway migrations
│   └── build.gradle
├── frontend/             # Next.js frontend
│   ├── app/              # Next.js app directory
│   └── package.json
├── local/                # Local documentation (not in git)
│   └── ROADMAP.md       # Detailed development roadmap
├── docker-compose.yml
└── README.md
```

## API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api-docs
- **API Specification File**: `/backend/src/main/resources/api/openapi.yaml`

## Core Features & Learning Goals

### 1. Real-time Messaging & Distributed Architecture
- WebSocket (STOMP) bidirectional communication
- Multi-server message broadcasting with Redis Pub/Sub
- Load balancing with sticky sessions
- Message delivery guarantee (ACK mechanism)
- Message ordering strategy (sequence numbers)
- Reconnection handling and missed message recovery
- Performance testing with gradual load increase
  - Measure baseline in v0.1
  - Identify breaking points
  - Track improvements across versions
- Metrics collection (Micrometer + Prometheus) from v0.1

### 2. Workspace/Channel Access Control
- RBAC implementation (Owner, Admin, Member, Guest)
- Spring Security Method Security (@PreAuthorize)
- Multi-tenancy with workspace isolation
- Public/Private channel access control
- Invitation and permission change event handling

### 3. Read Status Management & Notifications
- Real-time read status tracking with Redis Sorted Set
- Per-channel unread count optimization
- Mention/DM notification system
- Eventual consistency with periodic DB synchronization
- WebSocket-based read receipt broadcasting

### 4. Thread Structure & Query Optimization
- Self-referencing entity design (Message → parentMessage)
- N+1 problem resolution (@EntityGraph, Batch Fetch)
- Thread depth limitation and pagination
- Hot thread caching with Redis
- Recursive query optimization

### 5. Full-text Search (Optional Advanced Feature)
- Phase 1: PostgreSQL Full-Text Search
- Phase 2: Elasticsearch migration scenario
- Real-time indexing pipeline
- Search result highlighting
- Trade-off: search performance vs accuracy

## Lessons Learned

> Problems encountered during development and their solutions are documented here.

### v0.1
(To be written after completion)
- Performance baseline: [to be documented]

### v0.2
(To be written after completion)

### v0.3
(To be written after completion)
- Scaling verification: [actual vs expected results]

### v0.4
(To be written after completion)

### v0.5
(To be written after completion)
- Query optimization impact: [before/after performance with caching]

### v0.6
(To be written after completion)

### v1.0
(To be written after completion)

## Future Enhancements
- File upload with S3 integration
- Emoji reactions
- Voice/video calls
- Thread search within channels
- Mobile app