# 📄 Topic 09: Smart Notification Routing (Push Notifications)

## 1. Problem Statement
Sending a push notification (FCM/APNS) for *every* message creates "Notification Fatigue" and wastes money.
*   **Suppression**: If User A is currently chatting in the room (WebSocket connected), **DO NOT** send a push.
*   **Throttling**: If User B sends 10 messages in 1 second, send only 1 push summary.

## 2. Key Questions to Solve
*   **State Check**: How to check "Is User A online in Channel X?" with low latency before sending push?
*   **Distributed Lock**: How to throttle notifications across multiple servers?

## 3. Direction
*   **Redis-based Lock** for throttling.
*   **Presence Check** before dispatching to Push Queue.
