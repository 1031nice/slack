# Architecture Decision Records (ADR)

Architecture decisions for the Slack Clone project.

## ADR Definition

A document capturing an architectural decision with its context and consequences.

## Format

Each ADR includes:
- **Status**: Proposed | Accepted | Deprecated | Superseded
- **Context**: Problem being solved
- **Decision**: Chosen solution
- **Consequences**: Trade-offs
- **Alternatives Considered**: Other options and rejection reasons

## Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [0001](./0001-redis-vs-kafka-for-multi-server-broadcast.md) | Redis Pub/Sub vs Kafka for Multi-Server Broadcast | Accepted | 2024-12-10 |
| [0002](./0002-eventual-consistency-for-read-status.md) | Eventual Consistency for Read Status | Accepted | 2024-12-13 |
| [0003](./0003-redis-zset-for-unread-counts.md) | Use Redis Sorted Set (ZSET) for Unread Count Tracking | Accepted | 2024-12-14 |
| [0004](./0004-redis-pipeline-vs-lua-script.md) | Redis Pipeline vs Lua Script for Batch Operations | Accepted | 2024-12-14 |
| [0005](./0005-explicit-authorization-over-preauthorize.md) | Explicit Authorization Over @PreAuthorize | Accepted | 2024-12-15 |
| [0006](./0006-event-based-architecture-for-distributed-messaging.md) | Event-Based Architecture for Distributed Messaging | Proposed | 2025-12-20 |
| [0007](./0007-kafka-batching-for-read-receipt-persistence.md) | Kafka-Based Batching for Read Receipt Persistence | Proposed | 2025-12-26 |
| [0008](./0008-message-ordering-in-distributed-systems.md) | Message Ordering in Distributed Chat Systems | Proposed | 2025-12-31 |

## Creating a New ADR

1. Copy the template below
2. Number it sequentially (002, 003, etc.)
3. Fill in all sections
4. Update this README's index
5. Commit and reference in related PRs

## Template

```markdown
# ADR-XXX: [Title]

## Status

**Proposed** | **Accepted** | **Deprecated** | **Superseded by ADR-XXX**

## Context

What is the issue that we're seeing that is motivating this decision or change?

## Decision

What is the change that we're proposing and/or doing?

## Consequences

What becomes easier or more difficult to do because of this change?

### Positive
- ...

### Negative
- ...

## Alternatives Considered

### Alternative 1: [Name]

**Rejected because:**
- ...

## Applicability

When should this pattern be used?
When should it NOT be used?

## Related

- Related code files
- Related ADRs
- Related issues/PRs

## References

- External links
- Documentation
- Articles
```

## When to Write an ADR

Write an ADR for decisions that:
- Affect system architecture
- Have significant trade-offs
- Get reused across the codebase
- May need future justification
- Involve technology/pattern selection

## When NOT to Write an ADR

Skip ADRs for:
- Simple refactoring
- Bug fixes
- Obvious technology choices
- Temporary workarounds
- Implementation details without architectural impact

## Best Practices

1. Write ADRs when making decisions, not retroactively
2. Keep concise (1-2 pages max)
3. Include context for future readers
4. List alternatives considered
5. Update status when decisions change (preserve old ADRs)
6. Reference in code (`// See ADR-001 for why we use ZSET`)

## Resources

- [ADR GitHub Organization](https://adr.github.io/)
- [Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
