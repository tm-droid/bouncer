package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.glass.*
import coil.request.ImageRequest
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import kotlin.math.roundToInt

@Composable
fun FavoritesScreen(
    onBackClick: () -> Unit,
    onTeamClick: (String) -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val favorites by FavouriteTeamsManager.favourites.collectAsState()
    var showAddTeamSheet by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }

    // Reorder state
    var reorderableFavorites by remember(favorites) { mutableStateOf(favorites.toList()) }
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "favShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    GlassBloomBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Glass Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = dimens.screenHorizontal, vertical = dimens.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBackClick,
                        contentDescription = "Back",
                        useGoldTint = false
                    )
                    Text(
                        "FAVORITES",
                        style = typography.headingSmall,
                        color = colors.goldPrimary,
                        letterSpacing = 2.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.xs)) {
                        if (favorites.isNotEmpty()) {
                            GlassIconButton(
                                icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                onClick = {
                                    if (isEditMode) {
                                        FavouriteTeamsManager.reorder(reorderableFavorites)
                                    }
                                    isEditMode = !isEditMode
                                },
                                contentDescription = if (isEditMode) "Done" else "Edit",
                                useGoldTint = isEditMode
                            )
                        }
                        GlassIconButton(
                            icon = Icons.Default.Add,
                            onClick = { showAddTeamSheet = true },
                            contentDescription = "Add Team"
                        )
                    }
                }

                if (favorites.isEmpty()) {
                    EmptyFavoritesState(
                        onAddTeam = { showAddTeamSheet = true }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = dimens.xl,
                            start = dimens.xl,
                            end = dimens.xl,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimens.lg)
                    ) {
                        // Header Stats
                        item {
                            FavoritesHeader(
                                count = favorites.size,
                                shimmerOffset = shimmerOffset
                            )
                        }

                        // Section Divider
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(dimens.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isEditMode) Icons.Default.DragHandle else Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (isEditMode) colors.goldPrimary else colors.statusLive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isEditMode) "DRAG TO REORDER" else "YOUR TEAMS",
                                    style = typography.labelSmall,
                                    color = if (isEditMode) colors.goldPrimary else colors.textSecondary,
                                    letterSpacing = 1.5.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    colors.glassBorder.copy(alpha = 0.3f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        // Favorite Teams
                        if (isEditMode) {
                            items(reorderableFavorites.size) { index ->
                                val teamName = reorderableFavorites[index]
                                ReorderableTeamCard(
                                    teamName = teamName,
                                    onMoveUp = {
                                        if (index > 0) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            reorderableFavorites = reorderableFavorites.toMutableList().apply {
                                                add(index - 1, removeAt(index))
                                            }
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < reorderableFavorites.lastIndex) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            reorderableFavorites = reorderableFavorites.toMutableList().apply {
                                                add(index + 1, removeAt(index))
                                            }
                                        }
                                    },
                                    onRemove = {
                                        reorderableFavorites = reorderableFavorites.toMutableList().apply {
                                            removeAt(index)
                                        }
                                        FavouriteTeamsManager.remove(teamName)
                                    },
                                    canMoveUp = index > 0,
                                    canMoveDown = index < reorderableFavorites.lastIndex,
                                    position = index + 1
                                )
                            }
                        } else {
                            items(reorderableFavorites.chunked(2)) { rowTeams ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.md)
                                ) {
                                    rowTeams.forEach { teamName ->
                                        FavoriteTeamCard(
                                            teamName = teamName,
                                            onClick = { onTeamClick(teamName) },
                                            onRemove = { FavouriteTeamsManager.remove(teamName) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowTeams.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Footer tip
                        item {
                            Spacer(modifier = Modifier.height(dimens.lg))
                            Text(
                                text = if (isEditMode)
                                    "Tap ✓ to save your new order"
                                else
                                    "Tap the edit button to reorder teams",
                                style = typography.labelSmall,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Glass FAB
            if (favorites.isNotEmpty() && !isEditMode) {
                GlassFab(
                    icon = Icons.Default.Add,
                    onClick = { showAddTeamSheet = true },
                    contentDescription = "Add Team",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = dimens.lg, bottom = 116.dp)
                )
            }
        }
    }

    // Add Team Bottom Sheet
    if (showAddTeamSheet) {
        AddTeamBottomSheet(
            currentFavorites = favorites,
            onDismiss = { showAddTeamSheet = false },
            onTeamSelected = { teamName ->
                FavouriteTeamsManager.add(teamName)
            }
        )
    }
}

@Composable
private fun FavoritesHeader(
    count: Int,
    shimmerOffset: Float
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = true,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(dimens.xxl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimens.xs)
            ) {
                Text(
                    text = "Favorite Teams",
                    style = typography.headingMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Quick access to your teams",
                    style = typography.bodySmall,
                    color = colors.textMuted
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.statusLive.copy(alpha = 0.15f + shimmerOffset * 0.1f),
                                colors.statusLive.copy(alpha = 0.05f)
                            )
                        ),
                        CircleShape
                    )
                    .border(1.dp, colors.statusLive.copy(alpha = 0.3f), CircleShape)
            ) {
                Text(
                    text = count.toString(),
                    style = typography.statValue,
                    color = colors.statusLive
                )
            }
        }
    }
}

@Composable
private fun FavoriteTeamCard(
    teamName: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(teamName, null, 256, 256)
    var showRemoveDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(dimens.lg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Team Logo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                ) {
                    CoilImage(
                        model = if (logoUrl == null) null else ImageRequest.Builder(context)
                            .data(logoUrl)
                            .crossfade(true)
                            .size(128)
                            .build(),
                        contentDescription = teamName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(dimens.md))

                // Team Name
                Text(
                    text = teamName,
                    style = typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite Heart
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .background(colors.statusLive.copy(alpha = 0.15f), CircleShape)
                    .clickable { showRemoveDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Remove",
                    tint = colors.statusLive,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    // Remove confirmation dialog
    if (showRemoveDialog) {
        Dialog(onDismissRequest = { showRemoveDialog = false }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(dimens.xxl)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.lg)
                ) {
                    Text(
                        "Remove Team",
                        style = typography.headingSmall,
                        color = colors.textPrimary
                    )
                    Text(
                        "Remove $teamName from favorites?",
                        style = typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        GlassTextButton(
                            text = "Cancel",
                            onClick = { showRemoveDialog = false }
                        )
                        Spacer(modifier = Modifier.width(dimens.sm))
                        GlassPrimaryButton(
                            text = "Remove",
                            onClick = {
                                onRemove()
                                showRemoveDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableTeamCard(
    teamName: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    position: Int
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(teamName, null, 128, 128)

    // Subtle shake animation for edit mode
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeOffset"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.roundToInt(), 0) },
        useGoldBorder = true,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(dimens.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(colors.goldPrimary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = position.toString(),
                    color = colors.goldPrimary,
                    style = typography.labelSmall
                )
            }

            // Team Logo
            CoilImage(
                model = if (logoUrl == null) null else ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .size(80)
                    .build(),
                contentDescription = teamName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.glassBackground, CircleShape),
                contentScale = ContentScale.Fit
            )

            // Team name
            Text(
                text = teamName,
                style = typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Reorder buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.xs)
            ) {
                GlassIconButtonSmall(
                    icon = Icons.Default.KeyboardArrowUp,
                    onClick = { if (canMoveUp) onMoveUp() },
                    contentDescription = "Move up",
                    enabled = canMoveUp
                )

                GlassIconButtonSmall(
                    icon = Icons.Default.KeyboardArrowDown,
                    onClick = { if (canMoveDown) onMoveDown() },
                    contentDescription = "Move down",
                    enabled = canMoveDown
                )

                GlassIconButtonSmall(
                    icon = Icons.Default.Close,
                    onClick = onRemove,
                    contentDescription = "Remove",
                    useGoldTint = false
                )
            }
        }
    }
}

@Composable
private fun EmptyFavoritesState(
    onAddTeam: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.xl)
        ) {
            // Animated Heart Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .size(100.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.statusLive.copy(alpha = 0.15f),
                                colors.statusLive.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(colors.glassBackground, CircleShape)
                        .border(1.dp, colors.statusLive.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = colors.statusLive.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = "No Favorite Teams",
                style = typography.headingMedium,
                color = colors.textPrimary
            )

            Text(
                text = "Add teams to quickly access their\nmatches and updates",
                style = typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(dimens.sm))

            GlassPrimaryButton(
                text = "Add Your First Team",
                onClick = onAddTeam,
                icon = Icons.Default.Add
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTeamBottomSheet(
    currentFavorites: List<String>,
    onDismiss: () -> Unit,
    onTeamSelected: (String) -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val popularTeams = listOf(
        "India", "Australia", "England", "South Africa", "Pakistan",
        "New Zealand", "West Indies", "Sri Lanka", "Bangladesh", "Afghanistan",
        "Mumbai Indians", "Chennai Super Kings", "Royal Challengers Bengaluru",
        "Kolkata Knight Riders", "Delhi Capitals", "Rajasthan Royals",
        "Punjab Kings", "Sunrisers Hyderabad", "Gujarat Titans", "Lucknow Super Giants"
    )

    val filteredTeams = if (searchQuery.isBlank()) {
        popularTeams.filterNot { currentFavorites.contains(it) }
    } else {
        popularTeams.filter {
            it.contains(searchQuery, ignoreCase = true) && !currentFavorites.contains(it)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.backgroundBase,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(dimens.md))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(colors.textMuted, RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.height(dimens.lg))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.xl)
                .padding(bottom = dimens.xxxl),
            verticalArrangement = Arrangement.spacedBy(dimens.lg)
        ) {
            Text(
                text = "Add Team",
                style = typography.headingMedium,
                color = colors.textPrimary
            )

            // Search Field
            GlassTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search teams...",
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Teams List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(dimens.sm)
            ) {
                items(filteredTeams) { teamName ->
                    TeamSelectionRow(
                        teamName = teamName,
                        onClick = {
                            onTeamSelected(teamName)
                            onDismiss()
                        }
                    )
                }

                if (filteredTeams.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimens.xxxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank())
                                    "All teams are already in favorites"
                                else
                                    "No teams found",
                                color = colors.textMuted,
                                style = typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamSelectionRow(
    teamName: String,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val context = LocalContext.current
    val logoUrl = TeamLogoManager.getBestLogoUrl(teamName, null, 128, 128)

    GlassCardSmall(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoilImage(
                model = if (logoUrl == null) null else ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .size(64)
                    .build(),
                contentDescription = teamName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.glassBackground, CircleShape),
                contentScale = ContentScale.Fit
            )

            Text(
                text = teamName,
                style = typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = colors.goldPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
