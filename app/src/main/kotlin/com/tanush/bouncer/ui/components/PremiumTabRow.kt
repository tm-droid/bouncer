package com.tanush.bouncer.ui.components

import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.accessibility.rememberReducedMotionEnabled
import kotlin.math.abs
import kotlin.math.roundToInt

private val Gold = Color(0xFFD4AF37)

/**
 * Premium tab row with a smart gold underline indicator that supports
 * swipe-linked animation via an optional [PagerState].
 */
@Composable
fun PremiumTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null
) {
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotionEnabled()

    // Tab geometry: left offset (dp) and width (dp) per index
    val tabOffsets = remember { mutableStateMapOf<Int, Float>() }
    val tabWidths = remember { mutableStateMapOf<Int, Float>() }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            with(density) {
                                tabOffsets[index] =
                                    coordinates.parentLayoutCoordinates
                                        ?.localPositionOf(coordinates, Offset.Zero)
                                        ?.x
                                        ?.toDp()
                                        ?.value ?: 0f
                                tabWidths[index] = coordinates.size.width.toDp().value
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) }
                        )
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Gold else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // --- Smart underline indicator ---
        if (tabWidths.size == tabs.size && tabOffsets.size == tabs.size) {
            val indicatorHeight = 2.dp

            if (pagerState != null && !reducedMotion) {
                // Swipe-linked indicator
                SwipeLinkedIndicator(
                    pagerState = pagerState,
                    tabOffsets = tabOffsets,
                    tabWidths = tabWidths,
                    tabCount = tabs.size,
                    indicatorHeight = indicatorHeight,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            } else {
                // Spring / instant indicator
                val targetLeft = tabOffsets[selectedIndex] ?: 0f
                val targetWidth = tabWidths[selectedIndex] ?: 0f

                val animLeft by animateDpAsState(
                    targetValue = targetLeft.dp,
                    animationSpec = if (reducedMotion) tween(0)
                    else spring(stiffness = Spring.StiffnessMediumLow),
                    label = "indicator_left"
                )
                val animWidth by animateDpAsState(
                    targetValue = targetWidth.dp,
                    animationSpec = if (reducedMotion) tween(0)
                    else spring(stiffness = Spring.StiffnessMediumLow),
                    label = "indicator_width"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(animLeft.roundToPx(), 0) }
                        .width(animWidth)
                        .height(indicatorHeight)
                        .clip(RoundedCornerShape(1.dp))
                        .drawBehind {
                            drawRect(color = Gold)
                        }
                )
            }
        }
    }
}

/**
 * Indicator that follows [PagerState] drag offset, stretching during swipe
 * and snapping back with [EaseOutExpo] on settle.
 */
@Composable
private fun SwipeLinkedIndicator(
    pagerState: PagerState,
    tabOffsets: Map<Int, Float>,
    tabWidths: Map<Int, Float>,
    tabCount: Int,
    indicatorHeight: Dp,
    modifier: Modifier = Modifier
) {
    val currentPage = pagerState.currentPage
    val fraction = pagerState.currentPageOffsetFraction
    val isDragging = abs(fraction) > 0.01f

    // Stretch multiplier: 1.3× during drag, 1.0× on settle
    val stretchMultiplier by animateFloatAsState(
        targetValue = if (isDragging) 1.3f else 1.0f,
        animationSpec = if (isDragging) tween(100)
        else tween(200, easing = EaseOutExpo),
        label = "stretch"
    )

    // Interpolate position between current page and target page
    val targetPage = when {
        fraction > 0 -> (currentPage + 1).coerceAtMost(tabCount - 1)
        fraction < 0 -> (currentPage - 1).coerceAtLeast(0)
        else -> currentPage
    }
    val absFraction = abs(fraction)

    val currentLeft = tabOffsets[currentPage] ?: 0f
    val targetLeft = tabOffsets[targetPage] ?: 0f
    val currentWidth = tabWidths[currentPage] ?: 0f
    val targetWidth = tabWidths[targetPage] ?: 0f

    // Lerp offset and base width
    val interpLeft = currentLeft + (targetLeft - currentLeft) * absFraction
    val interpWidth = currentWidth + (targetWidth - currentWidth) * absFraction

    // Apply stretch: expand equally from center
    val stretchedWidth = interpWidth * stretchMultiplier
    val stretchOffset = (stretchedWidth - interpWidth) / 2f
    val finalLeft = interpLeft - stretchOffset

    Box(
        modifier = modifier
            .offset { IntOffset(finalLeft.dp.roundToPx(), 0) }
            .width(stretchedWidth.dp)
            .height(indicatorHeight)
            .clip(RoundedCornerShape(1.dp))
            .drawBehind {
                drawRoundRect(
                    color = Gold,
                    cornerRadius = CornerRadius(1.dp.toPx()),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
            }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
private fun PremiumTabRowPreview() {
    val tabs = listOf("Summary", "Scorecard", "Commentary")
    PremiumTabRow(
        tabs = tabs,
        selectedIndex = 1,
        onTabSelected = {}
    )
}
