# ADR-0005: Explicit Service-Level Authorization over @PreAuthorize

- **Status**: Accepted ✅
- **Date**: 2024-12-17
- **Context**: Authorization strategy for REST APIs
- **Incident**: PermissionService deletion caused runtime failures, not caught at compile time

---

## Problem Statement

@PreAuthorize with complex SpEL expressions caused a production incident: deleting PermissionService resulted in runtime
failures across 9 controller endpoints. The error only surfaced at runtime, not compile time.

**Actual incident:**

```java
@PreAuthorize("@permissionService.isWorkspaceMemberByAuthUserId(...)")
public ResponseEntity<...>getChannel(...){}

// PermissionService deleted → No compile error
// Runtime: IllegalArgumentException: Failed to evaluate expression '@permissionService...'
```

## Context

**Current implementation used @PreAuthorize with complex SpEL:**

- 9 controller endpoints using `@permissionService.*` expressions
- Bean references, method parameter interpolation, Java class references
-
Example: `@PreAuthorize("@permissionService.hasWorkspaceRoleByAuthUserId(authentication.principal.subject, #workspaceId, T(com.slack.domain.workspace.WorkspaceRole).ADMIN)")`

**Problems encountered:**

1. **No compile-time safety**: Bean deletion not caught by compiler
2. **Poor debuggability**: Error messages unclear, no stack trace context
3. **Testing difficulty**: Requires SecurityContext mocking
4. **Refactoring risk**: IDE doesn't understand SpEL strings (no "find usages", no refactoring support)
5. **No logging**: Authorization failures don't log user/resource context

## Proposed Solutions

### Option 1: Restore @PreAuthorize ⚠️

Keep using @PreAuthorize with PermissionService for SpEL expressions.

**Pros:**

- ✅ Declarative, authorization visible at controller level
- ✅ AOP-based separation of concerns
- ✅ Spring Security's recommended approach for method security

**Cons:**

- ❌ No type safety (magic strings)
- ❌ Runtime-only validation
- ❌ Poor debugging experience
- ❌ Difficult to test in isolation
- ❌ IDE refactoring doesn't work
- ❌ No contextual logging

**When acceptable:**

- Simple role checks: `@PreAuthorize("hasRole('ADMIN')")`
- Global authorization (not resource-specific)

**Not acceptable:**

- Complex SpEL with bean references
- Method parameter interpolation (#workspaceId)
- Resource-specific authorization

---

### Option 2: Explicit Service-Level Authorization ⭐ (Chosen)

Remove @PreAuthorize, check permissions explicitly in service methods.

**Implementation:**

```java
// Controller: pass User to service
@GetMapping("/channels/{channelId}")
public ResponseEntity<ChannelResponse> getChannel(
@PathVariable Long channelId,
@AuthenticationPrincipal User user){
        return ok(channelService.getChannel(channelId,user));
        }

// Service: explicit authorization check
public ChannelResponse getChannel(Long channelId,User user){
        Channel channel=channelRepository.findById(channelId)
        .orElseThrow(()->new ChannelNotFoundException(...));

        // Explicit, type-safe, loggable
        if(!canUserAccessChannel(channel,user)){
        log.warn("Access denied: user={}, channel={}, type={}",
        user.getId(),channel.getId(),channel.getType());
        throw new AccessDeniedException("Cannot access channel");
        }

        return toResponse(channel);
        }
```

**Pros:**

- ✅ **Type safety**: Compile-time checking
- ✅ **Debuggable**: Breakpoints, stack traces
- ✅ **Testable**: No SecurityContext mocking needed
- ✅ **IDE support**: Refactoring, find usages, go-to-definition
- ✅ **Loggable**: Full context (user, resource, reason)
- ✅ **Explicit**: Authorization logic visible in code flow

**Cons:**

- ❌ Slightly more verbose
- ❌ Authorization logic mixed with business logic

**Real-world usage:**

- Large-scale projects prefer this approach for complex authorization
- @PreAuthorize reserved for simple role checks only

---

### Option 3: Custom Authorization Service

Extract authorization logic into dedicated service.

```java

@Service
public class ChannelAuthorizationService {
    public void requireChannelAccess(Channel channel, User user) {
        if (!canAccess(channel, user)) {
            log.warn("Access denied: user={}, channel={}", user.getId(), channel.getId());
            throw new AccessDeniedException("Cannot access channel");
        }
    }
}

    // Service
    public ChannelResponse getChannel(Long channelId, User user) {
        Channel channel = findChannelById(channelId);
        channelAuthorizationService.requireChannelAccess(channel, user);
        return toResponse(channel);
    }
```

**Pros:**

- ✅ Centralized authorization logic
- ✅ Reusable across services
- ✅ All benefits of Option 2

**Cons:**

- ❌ Additional abstraction layer
- ❌ May be overkill for simple authorization

---

## Decision: Explicit Service-Level Authorization

**Why we chose Option 2:**

1. **Type safety prevents incidents**:
    - Compiler catches missing dependencies
    - Refactoring tools work correctly
    - No runtime surprises

2. **Better debugging**:
    - Clear stack traces
    - Breakpoint support
    - Contextual logging (user, resource, action)

3. **Easier testing**:
    - No SecurityContext mocking
    - Direct method invocation
    - Clear test intent

4. **Better maintainability**:
    - IDE refactoring support
    - Find usages works
    - Go-to-definition works

**Trade-offs accepted:**

| Trade-off                      | Why acceptable                                   |
|--------------------------------|--------------------------------------------------|
| More verbose                   | Explicitness > brevity for complex authorization |
| Authorization in service layer | Better than runtime failures from @PreAuthorize  |
| No AOP separation              | Type safety and debuggability more valuable      |

**Comparison:**

|                 | **@PreAuthorize**      | **Explicit Service Check**     |
|-----------------|------------------------|--------------------------------|
| **Type safety** | ❌ Runtime only         | ✅ Compile time                 |
| **Debugging**   | ❌ Poor (SpEL errors)   | ✅ Excellent (stack traces)     |
| **Testing**     | ❌ SecurityContext mock | ✅ Direct invocation            |
| **IDE support** | ❌ No refactoring       | ✅ Full refactoring             |
| **Logging**     | ❌ No context           | ✅ Full context                 |
| **Verbosity**   | ✅ Concise              | ⚠️ More code                   |
| **Best for**    | Simple role checks     | Complex resource authorization |

---

## Implementation Plan

**Phase 1: Create authorization utilities**

- Add authorization helper methods to services
- Implement logging for authorization failures

**Phase 2: Migrate controllers (9 endpoints)**

- Remove @PreAuthorize annotations
- Add @AuthenticationPrincipal User parameter
- Pass User to service methods

**Phase 3: Update services**

- Add explicit authorization checks
- Add authorization failure logging

**Phase 4: Remove PermissionService**

- Already deleted (caused this incident)
- Authorization logic now in service layer

---

## Key Lessons

**What we learned:**

1. **Type safety matters**: SpEL expressions bypass compiler, cause runtime failures
2. **@PreAuthorize is good for simple cases**: `hasRole('ADMIN')` is fine, complex SpEL is not
3. **Debuggability is critical**: Production incidents need clear logs and stack traces
4. **Explicit > implicit for complex logic**: Authorization is business logic, should be explicit

**When to use @PreAuthorize:**

- ✅ Simple role checks: `@PreAuthorize("hasRole('ADMIN')")`
- ✅ Authentication checks: `@PreAuthorize("isAuthenticated()")`
- ❌ Complex SpEL with bean references
- ❌ Resource-specific authorization

**When to use explicit service-level checks:**

- ✅ Resource-specific authorization (channel, workspace, message)
- ✅ Complex authorization logic
- ✅ When you need contextual logging
- ✅ When type safety matters

---

## References

1. [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
    - Official documentation for @PreAuthorize

2. [Introduction to Spring Method Security | Baeldung](https://www.baeldung.com/spring-security-method-security)
    - "@PreAuthorize supersedes @Secured"

3. [Spring Boot Security Best Practices 2025](https://hub.corgea.com/articles/spring-boot-security-best-practices)
    - Modern security patterns

4. **Our incident**: PermissionService deletion → 9 endpoints failing at runtime
    - No compile-time detection
    - Error: `IllegalArgumentException: Failed to evaluate expression '@permissionService...'`
    - Resolution: This ADR
