# Deep Dives: Engineering Explorations

Technical investigations into systemic limits, edge cases, and architectural trade-offs. This is the **investigation phase** before reaching a final verdict (ADR).

### Intent

* To break the system on paper before writing a single line of production code.
* To identify the "hidden demons" (race conditions, bottlenecks, partial failures) that standard tutorials ignore.
* To move beyond "best practices" and understand the specific **trade-offs** for this project.

### The Rules of Engagement

1. **No Magic**: Don't just say "it scales." Prove it with numbers or logic.
2. **Embrace Failure**: Explicitly document how the system dies (Partial failures, network partitions, OOM).
3. **Trade-offs over Truth**: There is no "best" tool, only the least painful one for the current constraints.
4. **Link to Reality**: Every deep dive should ideally lead to a **PoC (Proof of Concept)** in `experiments/` and a final **ADR**.

---

### Investigation Index

| # | Topic | Key Focus | Status |
| :--- | :--- | :--- | :--- |
| 01 | [Multi-Server Broadcasting](./01-multi-server-broadcasting.md) | Redis Pub/Sub, DB-first write path | ✅ Done |
| 02 | [Consistency & Race Conditions](./02-db-redis-race-condition.md) | Full Payload vs ID-only, DB-commit race | ✅ Done |
| 03 | [Massive Fan-out Architecture](./03-massive-fan-out.md) | Redis bandwidth limits, Tier 1-3 strategies | ✅ Done |
| 04 | [Gateway Separation](./04-gateway-separation.md) | MSA, gRPC vs REST, Stateful vs Stateless | ✅ Done |
| 05 | [Causal Ordering Guarantees](./05-causal-ordering.md) | Snowflake IDs, Jitter, Clock Skew | ✅ Done |
| 06 | [Read Status Updates](./06-read-status-updates.md) | Kafka Write-Behind, Batching efficiency | ✅ Done |

### Future Roadmap (Architectural Expansion)

| # | Topic | Key Focus | Status |
| :--- | :--- | :--- | :--- |
| 07 | [Distributed Presence](./07-distributed-presence.md) | Heartbeat storm, Redis Bitmap/HLL | 📋 Skeleton |
| 08 | [Real-time Search](./08-realtime-search.md) | ES Indexing lag, CDC, Dual-read | 📋 Skeleton |
| 09 | [Smart Notifications](./09-smart-notifications.md) | Suppression, Throttling, Redis Locks | 📋 Skeleton |
| 10 | [Multi-Device Sync](./10-multi-device-sync.md) | Session routing, User-topic fan-out | 📋 Skeleton |
| 11 | [Zero-Downtime Deployment](./11-zero-downtime-deployment.md) | Connection draining, Jitter reconnect | 📋 Skeleton |
| 12 | [Global Architecture](./12-global-architecture.md) | Multi-region, Data sovereignty | 📋 Skeleton |
| 13 | [Media Handling](./13-media-handling.md) | Resumable upload, CDN, Thumbnail Lambda | 📋 Skeleton |
| 14 | [Security & Compliance](./14-security-compliance.md) | E2EE, Privacy vs Search, TTL Retention | 📋 Skeleton |

---

### Deep Dive vs. ADR

*   **Deep Dive**: The "Why" and "How" (The Messy Investigation).
*   **ADR**: The "What" and "When" (The Final Verdict).