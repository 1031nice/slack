#!/usr/bin/env node

/**
 * Generate dev token compatible with simplified DevJwtUtil.java
 * In dev mode, DevJwtUtil accepts plain text as token (no JWT required)
 */

// Simply return the username as token
// DevJwtUtil.extractUsername() will accept this directly
const username = process.argv[2] || 'test-user';

console.log(username);
