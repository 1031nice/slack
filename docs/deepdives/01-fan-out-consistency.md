# Topic 01: Massive-scale Real-time Fan-out & Consistency

## 1. Problem Statement

* **The Challenge**: Addressing the exponential increase in message delivery load as the number of users () in a single
  channel grows.
* **Latency Budget**: Ensuring end-to-end message delivery (from publisher to tens of thousands of subscribers) within a
  **100ms** window.
* **Consistency vs. Availability**: Defining the trade-offs between 'Message Ordering' and 'System Availability' during
  network partitions or server failures.

## 2. Proposed Architecture

* **WebSocket Gateway Layer**: Designing edge servers to maintain millions of concurrent stateful connections.
* **Message Broker Strategy**: Evaluating high-throughput broadcast layers (e.g., Redis Pub/Sub, NATS, Kafka).
* **Storage Path**: Analyzing the trade-offs between **Write-through** (DB-first) and **Write-behind** (Broker-first)
  patterns.

## 3. The "Must-Consider" List (Deep Dive)

### 🚩 Exception 1: Thundering Herd (Reconnection Storm)

* **Scenario**: Mass reconnection attempts after a gateway server restart or regional network outage.
* **Focus**: Implementing Jittered Backoff and Connection Throttling to prevent CPU/Memory exhaustion.
* **🚀 Starter Questions for Deep Dive:**
* "When 50,000+ sockets drop simultaneously, where should the 'Reconnection Gate' be placed—at the Load Balancer (L7)
  level or within the application logic? How do we orchestrate a 'warm-up' period for the system to prevent immediate
  resource exhaustion upon recovery?"

### 🚩 Exception 2: Race Condition (DB vs. WebSocket)

* **Scenario**: Real-time message delivery via WebSocket succeeds, but the subsequent DB persistence fails (or is
  delayed).
* **Focus**: Preventing "Ghost Messages" using the Outbox Pattern or Transactional Event Listeners.
* **🚀 Starter Questions for Deep Dive:**
* "Since ADR-0001 chooses a DB-first approach, how do we handle the 'Partial Failure' scenario where a record is
  committed to PostgreSQL but the Redis broadcast fails? Is it better to roll back the DB transaction (impacting
  latency) or let the client reconcile the state later?"

### 🚩 Exception 3: Hot Channel & Fan-out Storm

* **Scenario**: A message with `@everyone` in a channel with 100k+ members (e.g., `#general`).
* **Focus**: Tenant Isolation and Rate Limiting to prevent a single workspace from degrading the entire system.
* **🚀 Starter Questions for Deep Dive:**
* "How do we protect shared infrastructure (like Redis bandwidth or CPU cycles) from a single 'Hot Channel' without
  impacting other tenants? Should we implement 'Cell-based Isolation' or dynamic 'Fair Queuing' at the messaging layer?"

### 🚩 Exception 4: Causal Consistency (Ordering)

* **Scenario**: Out-of-order message delivery due to distributed clock skew or network jitter.
* **Focus**: Using Logical Clocks (e.g., Lamport) or Centralized Sequencers to enable client-side reordering.
* **🚀 Starter Questions for Deep Dive:**
* "In a high-throughput environment where physical clocks cannot be trusted, what is the most cost-effective way to
  generate globally monotonic IDs? How do we handle the 'Message Interleaving' problem where a reply arrives at the
  client before the original question?"

## 4. Validation (PoC & Benchmarking)

* **Scenario A**: Measuring end-to-end latency with 10k concurrent users across 3+ server instances.
* **Scenario B**: Validating data integrity by simulating artificial database latency.
* **Scenario C**: Testing message loss rates during abrupt server shutdowns (Fault Tolerance).
