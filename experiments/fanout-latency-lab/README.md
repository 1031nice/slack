# Fan-out Latency Lab (Topic 03)

## Hypothesis
A single "Hot Channel" (e.g., #general with 1,000+ users) will introduce significant tail latency (P99) due to the serialization overhead and TCP buffer saturation on the backend server.

## Experimental Setup
- **Server**: Single Spring Boot Backend instance (running on port 9000).
- **Clients**: 100 to 1,000+ simulated WebSocket clients (managed by `mass-client.js`).
- **Load**: 1 broadcast message sent to the channel.
- **Metric**: Time delta between Server Send and the *last* client receiving the message.

## Prerequisites
- **Rancher Desktop** (or Docker) running.
- **Backend Server** running on `http://localhost:9000`.
- Node.js installed.

## How to Run

1. **Install Dependencies**:
   ```bash
   cd experiments/fanout-latency-lab
   npm install
   ```

2. **Run Test**:
   ```bash
   # Default: 100 clients baseline
   node run-fanout-test.js
   ```

3. **Adjusting Scale**:
   Edit `run-fanout-test.js` to change `totalClients` to 1,000 or more:
   ```javascript
   const CONFIG = {
       totalClients: 1000,
       clientsPerProcess: 100,
       // ...
   };
   ```

## Expected Results
- **100 clients**: P99 latency < 50ms.
- **1000 clients**: P99 latency might spike to 200ms+.
