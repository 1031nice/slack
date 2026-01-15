# Causal Ordering Lab (Topic 05)

## Overview
This experiment validates the effectiveness of **Snowflake IDs** and **Client-side Ordered Insertion** in maintaining message order under adverse conditions (Network Jitter) and identifies the limitations caused by **Clock Skew**.

## Hypothesis
1.  **Network Jitter**: With random network delays, a Naive Append strategy will result in frequent message inversions. **Ordered Insertion** will achieve 0% inversions with 0ms artificial latency.
2.  **Clock Skew**: If a server's clock drifts significantly (e.g., -5000ms), even Ordered Insertion cannot fix the causal ordering violation (replies appearing before questions). This confirms the necessity of NTP.

## Experimental Setup
*   **Producer**: Generates messages with 64-bit Snowflake IDs.
*   **Network**: Simulates Jitter (random delay) and Clock Skew (offset timestamps).
*   **Consumer**: Implements two strategies (Append vs. Insertion).

## Scenarios
1.  **Scenario A (Baseline Jitter)**: 
    *   3 Producers (perfectly synced clocks).
    *   Network Jitter: 0-200ms.
    *   Goal: Prove Ordered Insertion works.
2.  **Scenario B (Clock Skew Disaster)**:
    *   3 Producers.
    *   Producer 3 has a -5000ms clock drift.
    *   Goal: Demonstrate ordering failure despite correct algorithm.

## How to Run
```bash
cd experiments/causal-ordering-lab
npm install
node run-lab.js
```
