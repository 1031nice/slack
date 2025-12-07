---
name: Troubleshooting / Bug Investigation
about: Document debugging process and solutions for production issues
title: '[BUG] [Short description of problem]'
labels: 'troubleshooting'
assignees: ''

---

## Problem Description
<!-- What's broken? What's the observable symptom? -->


## Error Message / Logs
<!-- Paste relevant error messages or stack traces -->
```
[Paste error here]
```

## Environment
- **Version:** v0.X
- **Components affected:** Backend / Frontend / Infrastructure
- **Setup:** Single server / Multi-server / Docker Compose
- **OS:** macOS / Linux / Windows

## Steps to Reproduce
1.
2.
3.

**Expected behavior:**


**Actual behavior:**


## Initial Hypothesis
<!-- What did you think was causing this? -->


## Investigation Process

### Step 1: [What you tried]
**Action:**


**Result:**


**Learning:**


### Step 2: [Next thing you tried]
**Action:**


**Result:**


**Learning:**

<!-- Add more steps as needed -->

## Root Cause
<!-- After investigation, what was actually causing the problem? -->


## Solution

### What Fixed It
```java
// Code change or configuration that solved it
```

### Why It Works
<!-- Explain the fix - don't just say "it works now" -->


### How to Verify
```bash
# Command or test to confirm it's fixed
```

## Prevention
<!-- How to avoid this in the future? -->
- [ ] Add test case
- [ ] Add validation/error handling
- [ ] Update documentation
- [ ] Add monitoring/alerting
- [ ] Fix in multiple places if needed

## Lessons Learned
<!-- What would you do differently next time? -->
1.
2.

## Code Changes
<!-- Link to commit or PR that fixed this -->
- Commit:
- Files changed:

## Related Issues
- Similar issue: #
- Caused by: #
- Blocks: #

## Documentation Updates
- [ ] Update README.md "Lessons Learned" section
- [ ] Update troubleshooting guide
- [ ] Create TIL issue if insight is valuable: #

---

## Debugging Tips Used
<!-- For future reference, what techniques helped? -->
- [ ] Checked logs
- [ ] Used debugger
- [ ] Added print statements
- [ ] Simplified to minimal reproduction
- [ ] Checked Docker container status
- [ ] Reviewed recent commits
- [ ] Searched GitHub issues / Stack Overflow
- [ ] Read documentation
- [ ] Asked in community / forum
