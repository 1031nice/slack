# Deep Dives: Engineering Explorations

Technical investigations into systemic limits, edge cases, and architectural trade-offs. This is the **investigation
phase** before reaching a final verdict (ADR).

### Intent

* To break the system on paper before writing a single line of production code.
* To identify the "hidden demons" (race conditions, bottlenecks, partial failures) that standard tutorials ignore.
* To move beyond "best practices" and understand the specific **trade-offs** for this project.

### The Rules of Engagement

1. **No Magic**: Don't just say "it scales." Prove it with numbers or logic.
2. **Embrace Failure**: Explicitly document how the system dies (Partial failures, network partitions, OOM).
3. **Trade-offs over Truth**: There is no "best" tool, only the least painful one for the current constraints.
4. **Link to Reality**: Every deep dive should ideally lead to a **PoC (Proof of Concept)** in `experiments/` and a
   final **ADR**.

### Investigation Index

* **[01-fanout-consistency.md](https://www.google.com/search?q=./01-fanout-consistency.md)**: Solving the delivery
  problem and ensuring message ordering in a distributed world.

---

### Deep Dive vs. ADR

* **Deep Dive**: The "Why" and "How" (The Messy Investigation).
* **ADR**: The "What" and "When" (The Final Verdict).