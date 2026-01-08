# Multi-Server Broadcast Test (Scenario A)

## Hypothesis

**From Deep Dive 01, Section 1.2**: In a multi-server architecture using Redis Pub/Sub for broadcasting, we hypothesize that:

1. A message sent to Server A will reach clients connected to Servers B, C, and D
2. 100% delivery rate across all servers
3. P99 latency < 100ms (end-to-end from API request to WebSocket delivery)

## Experimental Setup

### Architecture

```
Client → REST API (Random Server) → PostgreSQL → Redis Pub/Sub
                                         ↓
                    ┌────────────────────┴────────────────────┐
                    ↓                    ↓                    ↓
              Backend-1 (9000)     Backend-2 (9001)    Backend-3 (9002)    Backend-4 (9003)
                    ↓                    ↓                    ↓                    ↓
            25% of WebSocket      25% of WebSocket    25% of WebSocket    25% of WebSocket
               Clients               Clients             Clients             Clients
```

### Components

- **4 Backend Instances**: Spring Boot servers running on ports 9000-9003
- **1 PostgreSQL**: Shared database for message persistence
- **1 Redis**: Pub/Sub broker for real-time fan-out
- **1 Kafka**: Read receipt persistence (optional for broadcast test)
- **N WebSocket Clients**: Distributed evenly across 4 servers (default: 4 clients)

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
1. Start infrastructure (PostgreSQL, Redis, Kafka)
2. Build and start 4 Spring Boot backend instances
3. Run the Node.js load test script (`run-test.js`)
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

| Criterion                  | Target           | Result (Local) |
|----------------------------|------------------|----------------|
| **Delivery Rate**          | 100%             | TBD            |
| **P99 Latency**            | < 100ms          | TBD            |
| **Cross-Server Broadcast** | All servers recv | TBD            |

*Note: In a local environment running 4 servers + infra on a single machine, occasional resource contention may cause slight latency spikes.*
