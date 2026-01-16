# 📄 Topic 14: Safety, Security & Compliance

## 1. Problem Statement
Chat apps are prime targets for abuse, and subject to strict regulations.
*   **E2EE (End-to-End Encryption)**: If only users have keys, how do we report abuse or search messages?
*   **Retention**: "Delete message after 7 days" - how to guarantee this in backups?

## 2. Key Questions to Solve
*   **Search vs Privacy**: Homomorphic encryption? Or client-side search indexing?
*   **Abuse Detection**: ML models on client-side vs server-side reporting.

## 3. Direction
*   **Signal Protocol** for E2EE (if required).
*   **TTL Indexes** for automated deletion.
