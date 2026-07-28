package com.tanush.bouncer.utils

import java.security.MessageDigest

/**
 * Collision-resistant fingerprint of text, for use inside cache keys.
 *
 * Replaces `String.hashCode()`, which is 32 bits. A commentary cache holds thousands of entries per
 * match, and by the birthday bound a 32-bit fingerprint starts colliding in the tens of thousands —
 * at which point one ball's cached commentary is served for a different ball's context. 128 bits
 * makes that unreachable.
 */

/** Bytes of SHA-256 kept. 16 bytes = 128 bits = 32 hex chars. */
private const val FINGERPRINT_BYTES = 16

/**
 * Hex fingerprint of [parts] taken together.
 *
 * Parts are separated by a NUL byte, so `("ab", "c")` and `("a", "bc")` do not fingerprint alike —
 * the previous `(a + b + c).hashCode()` could not tell those apart even before hash collisions.
 */
fun contentFingerprint(vararg parts: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (part in parts) {
        digest.update(part.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
}
