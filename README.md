# System Design Learning Log

Building a real-time messaging system. Learning distributed systems by breaking things and measuring the damage.

## Structure

```
├── docs/
│   ├── deepdives/
│   └── adr/
├── experiments/
└── app/
```

- **docs/deepdives/**: Frame problems, identify failure modes, list trade-offs before writing code
- **docs/adr/**: Final architectural decisions. The verdict after investigation
- **experiments/**: Measure alternatives. Numbers, not opinions
- **app/**: Working implementation. See [app/README.md](app/README.md) for details

## Current Work

- Deep dive: Fan-out consistency at 10k+ users
- Next: Measure fan-out latency

## Method

1. Problem first (not solution first)
2. Document how it breaks
3. Measure alternatives
4. Pick least-bad option
5. Record decision

No best practices. Only trade-offs.