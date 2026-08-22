package com.tanush.bouncer.data

import kotlinx.serialization.json.Json

/**
 * The single [Json] configuration for the app.
 *
 * This replaces reflective Gson. The settings below are not defaults — each one exists because
 * Gson was lenient in that specific way and the data this app reads is not under its control
 * (scraped HTML payloads, cached blobs written by older builds, Appwrite rows written by a Node
 * function). Switching to a strict parser without them would turn "a field appeared upstream" from
 * a non-event into a crash.
 *
 * - [Json.ignoreUnknownKeys]: Gson silently drops unknown fields. Cricbuzz and the Appwrite
 *   functions both add fields without warning; a new one must not throw on the device.
 * - [Json.coerceInputValues]: an explicit `null` for a non-nullable property with a default takes
 *   the default instead of throwing, which is what Gson did.
 * - [Json.explicitNulls] = false: nulls are omitted when writing, so cached payloads stay small
 *   and round-trip through older readers.
 * - [Json.isLenient]: accepts unquoted keys and single quotes, which scraped payloads occasionally
 *   contain.
 *
 * `encodeDefaults` is deliberately left at its default of false for the same round-trip reason.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}
