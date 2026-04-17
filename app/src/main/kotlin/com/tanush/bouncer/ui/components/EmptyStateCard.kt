package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsBaseball
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// EmptyStateCard — Calm, non-alarming empty state
// ==========================================

private val CardBackground = Color(0xFF1A1A1A)
private val Gold = Color(0xFFD4AF37)

@Composable
fun EmptyStateCard(
    hint: String,
    icon: ImageVector = Icons.Outlined.SportsBaseball,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Gentle floating bob animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStateBob")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyBobOffset"
    )

    // Fade-in on first appearance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "emptyFadeIn"
    )

    Surface(
        modifier = modifier.fillMaxWidth().alpha(contentAlpha),
        shape = RoundedCornerShape(12.dp),
        color = CardBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gold.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(48.dp)
                    .offset { IntOffset(0, bobOffset.dp.roundToPx()) }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gold)
                ) {
                    Text(
                        text = actionLabel,
                        color = Gold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// Previews
// ==========================================

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EmptyStateCardPreview() {
    EmptyStateCard(
        hint = "No live matches right now.\nCheck back during match hours."
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EmptyStateCardWithActionPreview() {
    EmptyStateCard(
        hint = "No matches found for this filter.",
        actionLabel = "Clear Filters",
        onAction = {}
    )
}
