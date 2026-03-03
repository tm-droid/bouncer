package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tanush.bouncer.ui.theme.*

/**
 * Premium modal dialog with animations
 */
@Composable
fun PremiumDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    icon: ImageVector? = null,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit = {},
    showActions: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!visible) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "dialog")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks
                    )
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = GoldPrimary.copy(alpha = 0.3f)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = GlassSurface,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.5f + shimmerOffset * 0.2f),
                            GlassBorder.copy(alpha = 0.3f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    GoldPrimary.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    
                    // Title
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Content
                    content()
                    
                    // Actions
                    if (showActions) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Dismiss button
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextSecondary
                                )
                            ) {
                                Text(
                                    text = dismissText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            
                            // Confirm button
                            Button(
                                onClick = {
                                    onConfirm()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldPrimary,
                                    contentColor = BackgroundBase
                                )
                            ) {
                                Text(
                                    text = confirmText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full screen modal with premium styling
 */
@Composable
fun PremiumFullScreenModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundBase
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                GlassHeader(
                    title = title,
                    onBackClick = onDismiss
                )
                
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Bottom sheet with premium styling
 */
@Composable
fun PremiumBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bottomSheet")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(200)
        ) + fadeOut()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            
            // Sheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = GlassSurface,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    // Handle
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                GlassBorder.copy(alpha = 0.5f),
                                RoundedCornerShape(2.dp)
                            )
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    // Title
                    if (title != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Content
                    content()
                    
                    // Bottom safe area
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

/**
 * Confirmation dialog with animation
 */
@Composable
fun PremiumConfirmationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    icon: ImageVector = Icons.Default.Warning,
    isDestructive: Boolean = false
) {
    PremiumDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        icon = icon,
        confirmText = confirmText,
        dismissText = dismissText,
        onConfirm = onConfirm
    ) {
        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

/**
 * Input dialog with text field
 */
@Composable
fun PremiumInputDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String,
    placeholder: String = "",
    initialValue: String = "",
    confirmText: String = "Save",
    dismissText: String = "Cancel"
) {
    var inputValue by remember { mutableStateOf(initialValue) }

    PremiumDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        confirmText = confirmText,
        dismissText = dismissText,
        onConfirm = { onConfirm(inputValue) }
    ) {
        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            placeholder = {
                Text(placeholder, color = TextTertiary)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = GlassBorder.copy(alpha = 0.5f),
                cursorColor = GoldPrimary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
    }
}

/**
 * Selection dialog with list
 */
@Composable
fun <T> PremiumSelectionDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    itemContent: @Composable (T, Boolean) -> Unit
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = GoldPrimary.copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(28.dp),
            color = GlassSurface
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Items
                items.forEach { item ->
                    val isSelected = item == selectedItem
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onItemSelected(item)
                                onDismiss()
                            }
                            .background(
                                if (isSelected) GoldPrimary.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        itemContent(item, isSelected)
                    }
                }
            }
        }
    }
}

/**
 * Action sheet with list of actions
 */
data class PremiumAction(
    val title: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun PremiumActionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    actions: List<PremiumAction>
) {
    PremiumBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = title
    ) {
        Column {
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            action.onClick()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = if (action.isDestructive) StatusLive else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Text(
                        text = action.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (action.isDestructive) StatusLive else TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Success/Error feedback dialog with animation
 */
@Composable
fun PremiumFeedbackDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    isSuccess: Boolean,
    title: String,
    message: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "feedback")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    PremiumDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        showActions = false
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        if (isSuccess) ChartGreen.copy(alpha = 0.15f)
                        else StatusLive.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle
                    else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isSuccess) ChartGreen else StatusLive,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = message,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSuccess) ChartGreen else StatusLive,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "OK",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Loading dialog
 */
@Composable
fun PremiumLoadingDialog(
    visible: Boolean,
    message: String = "Loading..."
) {
    if (!visible) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = GlassSurface,
            border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { rotationZ = rotation },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = GoldPrimary,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Text(
                    text = message,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
        }
    }
}
