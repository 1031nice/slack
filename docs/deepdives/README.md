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
| 03 | [Massive Fan-out Architecture](./03-massive-fan-out.md) | Redis bandwidth limits, Tier 1-3 strategies | 🔄 In Progress |
| 04 | [Gateway Separation](./04-gateway-separation.md) | MSA, gRPC vs REST, Stateful vs Stateless | 🆕 New |
| 05 | [Causal Ordering Guarantees](./05-causal-ordering.md) | Snowflake IDs, Logical Clocks, Client buffering | ⏳ Pending |
| 06 | [Read Status Updates](./06-read-status-updates.md) | Redis ZSet, Async read receipts, Unread counts | ⏳ Pending |

---

### Deep Dive vs. ADR

*   **Deep Dive**: The "Why" and "How" (The Messy Investigation).
*   **ADR**: The "What" and "When" (The Final Verdict).