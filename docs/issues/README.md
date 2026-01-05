# Issues

This directory tracks significant technical issues and implementation tasks for the Slack Clone project.

## What Goes Here

Create issues for:

- Architecture problems requiring investigation
- Non-trivial bugs with distributed systems implications
- Performance optimization opportunities
- Technical debt needing planning
- Complex feature implementations

Skip issues for:

- Simple bug fixes (just fix them)
- Obvious improvements (just do them)
- GitHub Issues duplicates (reference GitHub instead)

## Format

Each issue includes:

- **Problem Statement**: What's wrong
- **Root Cause**: Why it happens
- **Proposed Solutions**: Available options
- **Implementation Plan**: Fix approach
- **Success Criteria**: Success measurement

## Index

| Issue                                                    | Title                                       | Status | Priority | Created    |
|----------------------------------------------------------|---------------------------------------------|--------|----------|------------|
| [001](./0001-fix-message-ordering-distributed-system.md) | Fix Message Ordering in Distributed Systems | Open   | High     | 2025-12-31 |

## Issue Lifecycle

```
Open → In Progress → Review → Resolved → Closed
```

- **Open**: Problem identified, needs investigation
- **In Progress**: Actively being worked on
- **Review**: Implementation complete, under review
- **Resolved**: Fix merged, monitoring for issues
- **Closed**: Confirmed working, no further action

## Relationship with ADRs

- **ADRs**: Document architectural *decisions* (why we chose X over Y)
- **Issues**: Track implementation *tasks* (how to implement X)

Example:

- ADR-0008: Decides to use Snowflake IDs (decision)
- Issue-001: Implements Snowflake IDs (task)

## When to Create an Issue vs ADR

| Question                                           | Answer Points To |
|----------------------------------------------------|------------------|
| "Should we use Redis or Kafka?"                    | ADR (decision)   |
| "How do we implement Redis pub/sub?"               | Issue (task)     |
| "Why did we choose eventual consistency?"          | ADR (rationale)  |
| "MessageTimestampGenerator is broken, how to fix?" | Issue (problem)  |

---

**Template:** See [001-fix-message-ordering-distributed-system.md](./0001-fix-message-ordering-distributed-system.md)
for reference structure.
