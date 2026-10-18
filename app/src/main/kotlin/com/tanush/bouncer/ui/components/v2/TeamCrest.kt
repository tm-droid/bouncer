package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.tanush.bouncer.managers.LogoInk
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.theme.TeamColorRegistry
import com.tanush.bouncer.ui.theme.rememberTeamColors
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * A team's crest on a neutral circular plate, or its code when there is no crest.
 *
 * Six screens each had their own copy of this, and only two had a fallback: a team the logo map did
 * not know was a blank hole in the row. The crest is fitted, not cropped — Cricbuzz serves 4:3 flags
 * and shield crests, and a circle crop cut the edges off both.
 *
 * [ring] draws a 2dp ring in the team's colour, only for a side the registry knows.
 */
@Composable
fun TeamCrest(
    teamName: String,
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    size: Dp = 24.dp,
    ring: Boolean = false,
    acronym: String? = null,
) {
    val colors = BouncerThemeV2.colors
    val px = with(LocalDensity.current) { size.roundToPx() }
    val url = remember(teamName, logoUrl, px) {
        TeamLogoManager.getBestLogoUrl(teamName.ifBlank { null }, logoUrl, px, px * CREST_ASPECT_H / CREST_ASPECT_W)
    }
    val ringColor = if (ring && TeamColorRegistry.isKnown(teamName)) rememberTeamColors(teamName).primary else null
    var loaded by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .then(if (ringColor != null) Modifier.border(RING_WIDTH, ringColor, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Monogram(
                text = crestMonogram(acronym ?: teamName.takeIf { it.isNotBlank() }?.let(TeamAcronymManager::getAcronym), teamName),
                size = size,
            )
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = teamName,
                contentScale = ContentScale.Fit,
                colorFilter = rememberCrestTint(url),
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * CREST_INSET)
                    .alpha(if (loaded) 1f else 0f),
            )
        }
    }
}

/**
 * The reversal for the crest at [url] when it would vanish on this theme's plate: a single-ink crest
 * redrawn in `onSurface`, keeping its own shape and cut-outs (`SrcIn`). England's navy crest is white
 * on the dark plate, New Zealand's white fern black on the light one, and Scotland's is one colour on
 * both ([LogoInk.Both]); every other crest is untouched.
 */
@Composable
fun rememberCrestTint(url: String?): ColorFilter? {
    val colors = BouncerThemeV2.colors
    val ink = TeamLogoManager.logoInk(url)
    return remember(ink, colors.isDark, colors.onSurface) {
        val vanishes = ink == LogoInk.Both || (ink != null && (ink == LogoInk.Dark) == colors.isDark)
        if (vanishes) ColorFilter.tint(colors.onSurface, BlendMode.SrcIn) else null
    }
}

/**
 * A person's photo on a circular plate, or their initials when there is none or it fails to load.
 * Replaces the generic person glyph every player row used, which made a list of players a column of
 * identical silhouettes.
 */
@Composable
fun PersonAvatar(
    name: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 40.dp,
) {
    val colors = BouncerThemeV2.colors
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Monogram(text = personInitials(name), size = size)
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (loaded) 1f else 0f),
            )
        }
    }
}

@Composable
private fun Monogram(text: String, size: Dp) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    // Scaled with the plate: a fixed label size overflows a 20dp crest and is lost in a 72dp one.
    val fontSize = (size.value * if (text.length > 2) MONOGRAM_SCALE_WIDE else MONOGRAM_SCALE).sp
    Text(
        text = text,
        style = typography.labelMedium.copy(fontSize = fontSize, lineHeight = fontSize),
        fontWeight = FontWeight.Bold,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** Up to three characters of the team's code; the name's first letter when there is no code. */
internal fun crestMonogram(acronym: String?, teamName: String): String {
    val code = acronym?.filter { it.isLetterOrDigit() }?.take(MAX_CREST_CHARS)?.uppercase().orEmpty()
    return code.ifEmpty { teamName.trim().take(1).uppercase() }
}

/** "Virat Kohli" -> "VK", "Rashid Khan Arman" -> "RA", "Shakib" -> "S". */
internal fun personInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { w -> w.firstOrNull()?.isLetter() == true }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(1).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

private val RING_WIDTH = 2.dp
private const val CREST_INSET = 0.12f
private const val CREST_ASPECT_W = 4
private const val CREST_ASPECT_H = 3
private const val MAX_CREST_CHARS = 3
private const val MONOGRAM_SCALE = 0.38f
private const val MONOGRAM_SCALE_WIDE = 0.3f
