# 📄 Topic 07: Distributed Presence System (State Management)

## 1. Problem Statement
Managing "Online/Offline" status for millions of users creates a massive continuous workload.
*   **Heartbeat Storm**: If 1M users send a heartbeat every 30s, that's ~33k writes/sec.
*   **Stale Data**: If a server crashes, how do we detect that its 100k connected users are now "Offline" without waiting for TTL?

## 2. Key Questions to Solve
*   **Storage**: Is Redis `SET` enough? Or do we need `Bitmap` or `HyperLogLog` for memory efficiency?
*   **Write Optimization**: Can we batch heartbeats?
*   **Active Probing**: Should the server ping clients, or clients ping servers?

## 3. Direction
*   Likely **Redis-based Ephemeral Store** with aggressive sharding.
