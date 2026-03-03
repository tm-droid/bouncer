package com.tanush.bouncer.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the dominant color from an image URL using the Palette API and Coil.
 * Falls back to [default] if extraction fails or the URL is null.
 */
@Composable
fun rememberDominantColor(url: String?, default: Color = GoldDark): State<Color> {
    val context = LocalContext.current
    val colorState = remember(url) { mutableStateOf(default) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false) // Palette needs software bitmaps
                    .size(128) // Small size is sufficient for palette extraction
                    .build()
                val result = ImageLoader(context).execute(request)
                if (result is SuccessResult) {
                    (result.drawable as? BitmapDrawable)?.bitmap
                } else null
            }
            if (bitmap != null) {
                val palette = withContext(Dispatchers.Default) {
                    Palette.from(bitmap).generate()
                }
                val dominant = palette.getDominantColor(android.graphics.Color.TRANSPARENT)
                if (dominant != android.graphics.Color.TRANSPARENT) {
                    colorState.value = Color(dominant)
                }
            }
        } catch (_: Exception) {
            // Silently fall back to default color
        }
    }

    return colorState
}
