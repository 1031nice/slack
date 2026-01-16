# 📄 Topic 10: Multi-Device Synchronization (Session Management)

## 1. Problem Statement
A user is logged in on Phone, Tablet, and Desktop simultaneously.
*   **Read State**: Reading a message on Phone must clear the badge on Desktop instantly.
*   **Draft Sync**: Typing on Phone should show the draft on Desktop (advanced).

## 2. Key Questions to Solve
*   **Routing**: Does `User A` mean one connection or N connections?
*   **Fan-out**: Do we broadcast to `topic/user/{id}` or `topic/session/{sessionId}`?
*   **Conflict Resolution**: If I modify a message on Phone and Tablet simultaneously, who wins?

## 3. Direction
*   **Session-aware Routing**.
*   **User-Topic Fan-out**: Broadcast events to all active sessions of a user.
