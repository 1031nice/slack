# Experiments: Truth via Implementation

Code is the final arbiter of truth. This directory contains **Proof of Concepts (PoC)** and **Benchmarks** to validate
the hypotheses raised in `docs/deep-dives/`.

### Core Philosophy

* **Isolated**: Each experiment is a standalone sandbox. No dependencies on the main `app/` if possible.
* **Measurable**: Every experiment must produce data (latency, throughput, memory usage).
* **Trashable**: The code here doesn't need to be pretty. It only needs to prove a point. Once the point is proven, it's
  archived.

### Experiment Workflow

1. **Hypothesis**: "I believe will perform better than under conditions."
2. **Setup**: Minimal code to simulate the scenario (e.g., using `k6`, `Testcontainers`, or a simple `main` class).
3. **Execution**: Run under stress.
4. **Conclusion**: Did the data support the deep dive? If not, why?

### Lab Index

* **`fanout-latency-lab/`**: Measuring the time delta between Redis `PUBLISH` and WebSocket receipt for 10k subscribers.
* **`id-gen-bench/`**: Comparing `UUID v4`, `v7`, and `Snowflake` for write-heavy indexing performance in PostgreSQL.
* **`transactional-boundary-test/`**: Simulating partial failures between DB commits and Redis broadcasts to observe
  data drift.

---

### How to Run

Most experiments are designed to run via Docker Compose to ensure a clean environment.

```bash
cd experiments/[lab-name]
docker-compose up -d
./run-bench.sh

```