# 📄 Topic 12: Global Architecture (Multi-Region)

## 1. Problem Statement
Users in Asia communicating with users in US-East suffer from high latency (200ms+ RTT).
*   **Region Failover**: If AWS `ap-northeast-2` goes down, can Asia users still chat?
*   **Data Sovereignty**: GDPR/local laws requiring data to stay within borders.

## 2. Key Questions to Solve
*   **Replication**: Active-Active DB (DynamoDB Global Tables? CockroachDB?) vs Active-Passive (Read Replicas).
*   **Edge Routing**: Can we terminate WebSockets at the Edge (AWS CloudFront / Global Accelerator)?

## 3. Direction
*   **Edge Acceleration** for connectivity.
*   **Active-Passive** for DB (simpler) or **Sharded by Region** for compliance.
