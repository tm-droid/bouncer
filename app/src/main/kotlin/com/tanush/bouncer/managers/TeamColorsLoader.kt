package com.tanush.bouncer.managers

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import io.sentry.Sentry
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Loads `assets/TeamColors.json` — crest-derived colours, `{"Team name": ["#RRGGBB", "#RRGGBB"]}` —
 * into [TeamColorRegistry].
 *
 * The asset is generated offline by `tools/team-colors/extract.mjs`. Until it exists every team the
 * curated table does not list stays neutral, which is the state the app shipped in, so a missing
 * file is logged and nothing more.
 */
object TeamColorsLoader {
    private const val TAG = "TeamColorsLoader"
    private const val ASSET = "TeamColors.json"

    fun initialize(context: Context) {
        try {
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val raw = AppJson.decodeFromString<Map<String, List<String>>>(json)
            TeamColorRegistry.loadExtracted(parseTeamColors(raw))
        } catch (e: FileNotFoundException) {
            Log.i(TAG, "No $ASSET; crest-derived team colours are off (${e.message})")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read $ASSET", e)
            Sentry.captureException(e)
        }
    }
}

/** `["#RRGGBB", "#RRGGBB"]` pairs to colours. Entries that are not two parseable hex colours are dropped. */
internal fun parseTeamColors(raw: Map<String, List<String>>): Map<String, Pair<Color, Color>> =
    raw.mapNotNull { (name, hexes) ->
        val primary = hexes.getOrNull(0)?.let(::parseHexColor)
        val secondary = hexes.getOrNull(1)?.let(::parseHexColor) ?: primary
        if (primary == null || secondary == null) null else name to (primary to secondary)
    }.toMap()

internal fun parseHexColor(hex: String): Color? {
    val digits = hex.removePrefix("#")
    if (digits.length != HEX_RGB_LENGTH) return null
    val rgb = digits.toLongOrNull(radix = 16) ?: return null
    return Color(OPAQUE_ALPHA or rgb)
}

private const val HEX_RGB_LENGTH = 6
private const val OPAQUE_ALPHA = 0xFF000000L
