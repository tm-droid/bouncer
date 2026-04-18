package com.tanush.bouncer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.delay

@Composable
fun SplashScreenV2(onSplashComplete: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800),
        label = "splash_alpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1500)
        onSplashComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.SportsCricket, contentDescription = null,
                modifier = Modifier.size(64.dp), tint = colors.onPrimary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "BOUNCER", style = typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold, color = colors.onPrimary,
            )
        }
    }
}
