# Multi-Server Broadcast Test (Scenario A)

## Hypothesis

**From Deep Dive 01, Section 1.2**: In a multi-server architecture using Redis Pub/Sub for broadcasting, we hypothesize that:

1. A message sent to Server A will reach clients connected to Server B
2. 100% delivery rate across all servers

## Experimental Setup

### Architecture

```
Client → STOMP WebSocket → Backend Server → PostgreSQL → Redis Pub/Sub
                                                  ↓
                              ┌───────────────────┴───────────────────┐
                              ↓                                       ↓
                        Backend-1 (9000)                        Backend-2 (9001)
                              ↓                                       ↓
                      50% of WebSocket                        50% of WebSocket
                         Clients                                   Clients
```

### Components

- **2+ Backend Instances**: Spring Boot servers (minimum 2 for multi-server test)
- **1 PostgreSQL**: Shared database for message persistence
- **1 Redis**: Pub/Sub broker for real-time fan-out
- **N WebSocket Clients**: Distributed across servers (default: 4 clients)

## Running the Experiment

### Prerequisites

- Docker & Docker Compose installed
- Node.js 18+ installed
- Java 21+ installed

### Quick Start

```bash
cd experiments/multi-server-broadcast-test
./start-experiment.sh --all
```

This script will:
1. Start infrastructure (PostgreSQL, Redis)
2. Build and start 2+ Spring Boot backend instances
3. Run the Node.js test script (`run-test.js`)
4. Generate results in `./logs/`

### Advanced Usage

The `start-experiment.sh` script supports modular execution:

```bash
# Run everything (Infra + Backend + Test) - Default
./start-experiment.sh --all

# Start only infrastructure
./start-experiment.sh --infra-only

# Start/Restart only backend servers (assumes infra is ready)
./start-experiment.sh --backend-only

# Run only the test script (assumes servers are ready)
./start-experiment.sh --test-only

# Restart backend without rebuilding (faster)
./start-experiment.sh --backend-only --skip-build
```

### Configuration

Edit `run-test.js` to adjust test parameters:

```javascript
const CONFIG = {
  wsServers: [...],
  channelId: 1,
  totalClients: 4,        // Total number of WebSocket clients
  messagesPerSecond: 1,   // Message send rate
  testDurationSec: 10,    // Test duration
  warmupSec: 3            // Warmup period
};
```

## Output & Results

### Console Output

The script will output a summary of the test results including message delivery rates and latency percentiles (P50, P95, P99).

### Log Files

- **`logs/messages.csv`**: Raw message delivery logs
- **`logs/results.json`**: Aggregated test results and statistics

## Success Criteria

This experiment validates **Deep Dive 01, Section 1.2** if:

| Criterion                  | Target                          | Result              |
|----------------------------|---------------------------------|---------------------|
| **Delivery Rate**          | 100%                            | ✅ 100% (40/40)     |
| **Cross-Server Broadcast** | Messages reach all servers      | ✅ Verified         |
| **Architecture**           | Multi-server via Redis Pub/Sub  | ✅ Working          |

**Test Configuration:**
- Servers: 2 backends (9000, 9001)
- Clients: 4 WebSocket clients (2 per server)
- Messages: 10 messages sent
- Expected: 40 deliveries (4 clients × 10 messages)
- Observed: 40 deliveries

**Latency (Reference Only):**
- Average: 16ms, P50: 16ms, P99: 18ms
- Note: Latency varies significantly based on environment, load, and server count. This experiment focuses on functional validation (delivery rate), not performance benchmarking.
