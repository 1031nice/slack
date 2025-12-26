# System Architecture Diagrams

This directory contains PlantUML diagrams visualizing the main flows of the Slack Clone system.

## Diagram List

### 1. Authentication Flow
**File**: `01-authentication-flow.puml`

Explains the OAuth2-based user authentication and JWT validation process.

**Key Topics**:
- OAuth2 Authorization Code Flow
- JWT issuance and validation
- JWT authentication on WebSocket connection
- User entity loading and Authentication object creation

**Core Components**:
- Auth endpoints
- OAuth2 integration
- Security configuration
- JWT validation

---

### 2. Message Send Flow
**File**: `02-message-send-flow.puml`

Explains how clients send messages and broadcast them to other users.

**Key Topics**:
- Receiving messages via WebSocket
- Timestamp ID generation (distributed environment)
- Saving messages to PostgreSQL
- Broadcasting to all servers via Redis Pub/Sub
- Each server sending to local WebSocket clients
- Incrementing unread count
- Detecting and notifying @mentions

**Core Concepts**:
- Event-based architecture (v0.5)
- Timestamp ID: `{unix_timestamp_μs}.{sequence}`
- Client-side ordering with 2-second buffer
- Idempotency via `Set<timestampId>`

**Main Responsibilities**:
- Message validation and persistence
- Distributed ID generation
- Real-time broadcasting
- Unread tracking
- Mention detection

---

### 3. Reconnection & Missed Message Recovery
**File**: `03-reconnection-flow.puml`

Explains how to recover missed messages after network disconnection and reconnection.

**Key Topics**:
- Client tracking last received `timestampId`
- WebSocket reconnection
- Recovering missed messages via `/app/message.resend` request
- Timestamp-based DB query (no sequence number needed)
- Sending via private queue (`/queue/resend.{username}`)
- Deduplication (idempotency via Set)

**Core Concepts**:
- Timestamp-based recovery (no coordination needed)
- At-least-once delivery
- Client-side deduplication

**Main Process**:
- Detect disconnection
- Reconnect WebSocket
- Request messages after last timestamp
- Deduplicate and display

---

### 4. Read Receipt & Unread Count
**File**: `04-read-receipt-flow.puml`

Explains how to update read receipts and manage unread counts when users read messages.

**Key Topics**:
- Receiving WebSocket READ message
- **3-step update**:
  1. Update Redis immediately (real-time cache)
  2. WebSocket broadcast (notify other users)
  3. Asynchronously save to PostgreSQL (durability)
- Clear unread count (Redis Sorted Set)
- Recovery from DB on cache miss (cache warming)

**Core Concepts**:
- **Hybrid Redis + DB architecture**
- **Eventual consistency** (expected lag <100ms)
- Redis = speed, PostgreSQL = durability
- Non-blocking async persistence

**Main Components**:
- Read receipt management
- Unread count tracking
- Cache-first strategy with DB fallback

---

### 5. Mention Notification
**File**: `05-mention-notification-flow.puml`

Explains how to detect @username in messages and send notifications to mentioned users.

**Key Topics**:
- Parsing `@username` via regex
- Looking up User entities by username (batch query)
- Creating Mention records (duplicate check)
- Sending WebSocket notifications via private queue
- Unread mention query API

**Core Concepts**:
- Batch optimization (1 query for N mentions)
- Idempotency via UNIQUE constraint
- Browser notification integration

**Main Components**:
- Mention parsing
- Mention persistence
- Real-time notifications

---

### 6. System Architecture
**File**: `06-system-architecture.puml`

Architecture diagram showing the overall system component structure and interactions.

**Main Components**:
- **Frontend**: Next.js 14 (TypeScript, WebSocket client)
- **Load Balancer**: Nginx (sticky sessions, ip_hash)
- **Backend Servers**: 3 servers (Spring Boot, WebSocket, REST API)
- **OAuth2 Server**: External auth server (JWT issuance)
- **PostgreSQL**: Source of truth (ACID)
- **Redis**: Pub/Sub + Cache (Unread, Read receipts)
- **Prometheus**: Metrics collection

**Main Data Flows**:
- Client → Nginx → Backend servers (sticky session)
- Backend → PostgreSQL (persist)
- Backend → Redis Pub/Sub → All servers (broadcast)
- Servers → Redis Cache (unread, read receipts)

---

## How to Render PlantUML

### 1. VS Code Extension
```bash
# Install PlantUML extension
code --install-extension jebbs.plantuml
```

- Open `.puml` file
- Press `Alt + D` (preview)
- Or `Ctrl+Shift+P` → "PlantUML: Preview Current Diagram"

### 2. IntelliJ IDEA Plugin
```
Settings → Plugins → Install "PlantUML integration"
```

- Open `.puml` file
- Preview automatically shows on the right

### 3. Online Viewer
Paste code at [PlantUML Web Server](http://www.plantuml.com/plantuml/uml/)

### 4. CLI (Generate Images)
```bash
# Install PlantUML
brew install plantuml

# Generate PNG
plantuml docs/diagrams/*.puml

# Generate SVG
plantuml -tsvg docs/diagrams/*.puml
```

---

## Architecture History

### v0.5 (Current)
- ✅ Event-based architecture
- ✅ Timestamp ID (distributed ID generation)
- ✅ Client-side ordering
- ✅ Redis Pub/Sub (single topic)
- ✅ Hybrid Redis+DB (eventual consistency)

### v0.6 (Planned)
- Thread support
- Timestamp ID-based parent-child relationships

### v0.9 (Planned)
- Channel-specific Redis topics
- Optimize unnecessary broadcasts

---

## References

- [Slack Engineering Blog](https://slack.engineering/)
- [ADR-0001: Redis Pub/Sub vs Kafka](../adr/0001-redis-vs-kafka-for-multi-server-broadcast.md)
- [ADR-0006: Event-Based Architecture](../adr/0006-event-based-architecture-for-distributed-messaging.md)
- [README.md](../../README.md) - Overall project overview
