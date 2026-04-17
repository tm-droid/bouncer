package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback
import kotlinx.coroutines.delay

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    
    val pulse by rememberInfiniteTransition(label = "errorPulse").animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "errorPulse"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StatusLive.copy(alpha = 0.2f * pulse),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = StatusLive,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.3.sp
        )
        
        subtitle?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                haptic.click()
                onRetry()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Retry",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun LoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GoldLoadingIndicator(size = 56.dp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun RetryingOverlay(
    isRetrying: Boolean,
    attemptNumber: Int,
    maxAttempts: Int,
    onCancel: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val haptic = rememberHapticFeedback()
    
    AnimatedVisibility(
        visible = isRetrying,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Surface1,
                                GlassSurface
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GoldLoadingIndicator(size = 64.dp)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Retrying...",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Attempt $attemptNumber of $maxAttempts",
                    color = hierarchy.secondary.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = {
                        haptic.click()
                        onCancel()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassSurface
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkStatusBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "networkPulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "networkPulse"
    )
    
    AnimatedVisibility(
        visible = isOffline,
        enter = fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            StatusWarning.copy(alpha = 0.9f * pulse),
                            StatusWarning.copy(alpha = 0.95f * pulse)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "No internet connection",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
fun AutoRetryHandler(
    shouldRetry: Boolean,
    onRetry: () -> Unit,
    maxAttempts: Int = 3,
    delayMillis: Long = 2000
) {
    var attemptCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(shouldRetry, attemptCount) {
        if (shouldRetry && attemptCount < maxAttempts) {
            delay(delayMillis * (attemptCount + 1))
            attemptCount++
            onRetry()
        }
    }
    
    // Reset attempt count when shouldRetry becomes false
    LaunchedEffect(shouldRetry) {
        if (!shouldRetry) {
            attemptCount = 0
        }
    }
}

@Composable
fun SuccessSnackbar(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    durationMillis: Long = 2000
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(durationMillis)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                StatusSuccess.copy(alpha = 0.95f),
                                StatusSuccess.copy(alpha = 0.85f)
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
