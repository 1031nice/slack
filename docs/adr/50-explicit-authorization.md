# ADR-0050: Explicit Authorization Strategy

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-05
- **Context**: Security Hardening
- **Deciders**: Engineering Team
- **Related Deep Dive**: None

---

## TL;DR

**Decision**: Enforce **Explicit Authorization Checks** at the Service Layer for every resource access.

**Key Pattern**: `permissionService.require{Resource}Access(userId, resourceId)`

**Rationale**: "Implicit" authorization (assuming a user can access a message because they are authenticated) is a major security risk (IDOR). We must explicitly verify that `User ∈ Channel` before allowing access to `Channel` or `Message`.

---

## Context

### The Problem

Initial implementation relied on implicit trust:
1.  User logs in (Authenticated).
2.  User requests `GET /messages/123`.
3.  System returns Message 123 without checking if User is actually a member of the channel that owns Message 123.

This leads to **Insecure Direct Object References (IDOR)** vulnerabilities.

---

## Decision

We implement a dedicated `PermissionService` that encapsulates all access logic.

### Rules

1.  **Service Layer Enforcement**: Controllers delegate authz to Services. Services call `PermissionService`.
2.  **Fail-Fast**: If unauthorized, throw `AccessDeniedException` (403 Forbidden) or `ResourceNotFoundException` (404 Not Found) immediately.
3.  **Hierarchy**:
    *   To access `Message`, you must have access to `Channel`.
    *   To access `Channel`, you must be a `Member` of the channel (or it's public).
    *   To access `Workspace`, you must be a `Member` of the workspace.

---

## Implementation Details

```java
// Good Pattern
public MessageResponse getMessage(Long userId, Long messageId) {
    // 1. Check permission FIRST
    permissionService.requireMessageAccess(userId, messageId);

    // 2. Then fetch data
    return messageRepository.findById(messageId);
}
```

---

## Consequences

- ✅ **Security**: Prevents IDOR attacks.
- ✅ ** consistency**: All checks are centralized in `PermissionService`.
- ❌ **Overhead**: Extra DB queries to verify membership (mitigated by caching).
