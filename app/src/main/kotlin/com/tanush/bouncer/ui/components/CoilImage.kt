package com.tanush.bouncer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.R

/**
 * Small wrapper around Coil's AsyncImage to provide a consistent placeholder/error/fallback
 * and accept either an ImageRequest or any model (String/URL) like AsyncImage does.
 */
@Composable
fun CoilImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current

    val finalRequest = when (model) {
        is ImageRequest -> model
        else -> ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = finalRequest,
        placeholder = painterResource(R.drawable.ic_cricket_ball),
        error = painterResource(R.drawable.ic_cricket_ball),
        fallback = painterResource(R.drawable.ic_cricket_ball),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
