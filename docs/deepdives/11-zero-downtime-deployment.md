# 📄 Topic 11: Zero-Downtime Deployment (WebSocket)

## 1. Problem Statement
Deploying new backend code requires restarting servers.
*   **Long-lived Connections**: Unlike REST (stateless), WebSockets are stateful. Restarting a server disconnects 100k users instantly.
*   **Thundering Herd**: 100k users trying to reconnect at the exact same second will DDoS the Auth/DB.

## 2. Key Questions to Solve
*   **Graceful Shutdown**: How to stop accepting *new* connections while keeping *old* ones alive for a bit?
*   **Connection Draining**: How to force clients to reconnect *gradually* (random jitter) before the server kills the process?
*   **Blue/Green**: Can we migrate connections without dropping them? (Very hard)

## 3. Direction
*   **Rolling Update** with **Connection Draining** logic + **Client-side Jitter Reconnect**.
