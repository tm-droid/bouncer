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
import androidx.compose.material.icons.outlined.SportsCricket
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.glass.*
import coil.request.ImageRequest
import android.content.Context
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.components.EmptyStateView
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.utils.PreferencesManager
import kotlin.math.roundToInt

private fun teamLogoRequest(context: Context, teamName: String, size: Int): ImageRequest? {
    val url = TeamLogoManager.getBestLogoUrl(teamName, null, size, size) ?: return null
    return ImageRequest.Builder(context).data(url).crossfade(true).size(size).build()
}

@Composable
private fun PositionBadge(position: Int, alpha: Float = 0.2f) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(hierarchy.secondary.primary.copy(alpha = alpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = position.toString(),
            style = BouncerDesignSystem.typography.labelSmall,
            color = hierarchy.secondary.primary
        )
    }
}

@Composable
private fun TeamLogoCircle(teamName: String, size: Int, requestSize: Int) {
    val colors = BouncerDesignSystem.colors
    CoilImage(
        model = teamLogoRequest(LocalContext.current, teamName, requestSize),
        contentDescription = teamName,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colors.glassBackground, CircleShape),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun RemoveTeamDialog(
    teamName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(dimens.xxl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.lg)) {
                Text("Remove Team", style = typography.headingSmall, color = colors.textPrimary)
                Text("Remove $teamName from favorites?", style = typography.bodyMedium, color = colors.textSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassTextButton(text = "Cancel", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(dimens.sm))
                    GlassPrimaryButton(text = "Remove", onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    onBackClick: () -> Unit,
    onTeamClick: (String) -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val context = LocalContext.current

    val favorites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()
    var showAddTeamSheet by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var lastRemovedTeam by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var reorderableFavorites by remember(favorites) { mutableStateOf(favorites.toList()) }
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = remember { PreferencesManager.getInstance(context).hapticFeedbackEnabled }

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
                        color = hierarchy.secondary.primary,
                        letterSpacing = 2.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.xs)) {
                        if (favorites.isNotEmpty()) {
                            GlassIconButton(
                                icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                onClick = {
                                    if (isEditMode) FavouriteTeamsManager.reorder(reorderableFavorites)
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
                    EmptyFavoritesState(onAddTeam = { showAddTeamSheet = true })
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = dimens.lg,
                            start = dimens.screenHorizontalPadding,
                            end = dimens.screenHorizontalPadding,
                            bottom = dimens.bottomNavHeight + dimens.xxxl
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimens.cardGap)
                    ) {
                        item { FavoritesHeader(count = favorites.size, shimmerOffset = shimmerOffset) }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(dimens.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isEditMode) Icons.Default.DragHandle else Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (isEditMode) hierarchy.secondary.primary else colors.statusLive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isEditMode) "Drag to reorder" else "Saved teams",
                                    style = typography.labelSmall,
                                    color = if (isEditMode) hierarchy.secondary.primary else colors.textSecondary,
                                    letterSpacing = 0.8.sp
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

                        if (isEditMode) {
                            items(reorderableFavorites.size, key = { reorderableFavorites[it] }) { index ->
                                val teamName = reorderableFavorites[index]
                                ReorderableTeamCard(
                                    teamName = teamName,
                                    onMoveUp = {
                                        if (index > 0) {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            reorderableFavorites = reorderableFavorites.toMutableList().apply {
                                                add(index - 1, removeAt(index))
                                            }
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < reorderableFavorites.lastIndex) {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            reorderableFavorites = reorderableFavorites.toMutableList().apply {
                                                add(index + 1, removeAt(index))
                                            }
                                        }
                                    },
                                    onRemove = {
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        reorderableFavorites = reorderableFavorites.toMutableList().apply { removeAt(index) }
                                        FavouriteTeamsManager.remove(teamName)
                                    },
                                    canMoveUp = index > 0,
                                    canMoveDown = index < reorderableFavorites.lastIndex,
                                    position = index + 1
                                )
                            }
                        } else {
                            itemsIndexed(reorderableFavorites) { index, teamName ->
                                val swipeState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            lastRemovedTeam = teamName
                                            FavouriteTeamsManager.remove(teamName)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "$teamName removed",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    lastRemovedTeam?.let { FavouriteTeamsManager.add(it) }
                                                    lastRemovedTeam = null
                                                }
                                            }
                                            true
                                        } else false
                                    }
                                )
                                SwipeToDismissBox(
                                    state = swipeState,
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.error)
                                                .padding(end = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White) }
                                    }
                                ) {
                                    FavoriteTeamListRow(
                                        teamName = teamName,
                                        position = index + 1,
                                        onClick = { onTeamClick(teamName) },
                                        onRemove = { FavouriteTeamsManager.remove(teamName) }
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(dimens.lg))
                            Text(
                                text = if (isEditMode) "Tap ✓ to save your new order" else "Tap the edit button to reorder teams",
                                style = typography.labelSmall,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

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

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showAddTeamSheet) {
        AddTeamBottomSheet(
            currentFavorites = favorites,
            onDismiss = { showAddTeamSheet = false },
            onTeamSelected = { teamName -> FavouriteTeamsManager.add(teamName) }
        )
    }
}

@Composable
private fun FavoritesHeader(count: Int, shimmerOffset: Float) {
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
            Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
                Text("Favorite Teams", style = typography.headingMedium, color = colors.textPrimary)
                Text("Quick access to your teams", style = typography.bodySmall, color = colors.textMuted)
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
                Text(text = count.toString(), style = typography.statValue, color = colors.statusLive)
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(dimens.lg)
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(hierarchy.secondary.primary.copy(alpha = 0.1f), Color.Transparent)
                            ),
                            CircleShape
                        )
                ) {
                    CoilImage(
                        model = teamLogoRequest(context, teamName, 128),
                        contentDescription = teamName,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(dimens.md))
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

    if (showRemoveDialog) {
        RemoveTeamDialog(
            teamName = teamName,
            onDismiss = { showRemoveDialog = false },
            onConfirm = {
                onRemove()
                showRemoveDialog = false
            }
        )
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
            PositionBadge(position = position, alpha = 0.2f)
            CoilImage(
                model = teamLogoRequest(context, teamName, 80),
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
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.xs)) {
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
private fun FavoriteTeamListRow(
    teamName: String,
    position: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = remember { PreferencesManager.getInstance(context).hapticFeedbackEnabled }
    var showRemoveDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(dimens.radius.lg),
        contentPadding = PaddingValues(dimens.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PositionBadge(position = position, alpha = 0.14f)
            TeamLogoCircle(teamName = teamName, size = 40, requestSize = 96)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.xxs)
            ) {
                Text(
                    text = teamName,
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = "Tap to open team details", style = typography.labelSmall, color = colors.textMuted)
            }
            GlassIconButtonSmall(
                icon = Icons.Default.Close,
                onClick = { showRemoveDialog = true },
                contentDescription = "Remove",
                useGoldTint = false
            )
        }
    }

    if (showRemoveDialog) {
        RemoveTeamDialog(
            teamName = teamName,
            onDismiss = { showRemoveDialog = false },
            onConfirm = {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove()
                showRemoveDialog = false
            }
        )
    }
}

@Composable
private fun EmptyFavoritesState(onAddTeam: () -> Unit) {
    val dimens = BouncerDesignSystem.dimens
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateView(
            icon = Icons.Outlined.SportsCricket,
            title = "No favorite teams yet",
            message = "Add teams to quickly access fixtures and updates.",
            actionLabel = "Add team",
            onAction = onAddTeam,
            modifier = Modifier.padding(horizontal = dimens.screenHorizontalPadding)
        )
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
        popularTeams.filter { it.contains(searchQuery, ignoreCase = true) && !currentFavorites.contains(it) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.backgroundBase,
        dragHandle = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Text(text = "Add Team", style = typography.headingMedium, color = colors.textPrimary)
            GlassTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search teams...",
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(dimens.sm)
            ) {
                items(filteredTeams, key = { it }) { teamName ->
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
                            modifier = Modifier.fillMaxWidth().padding(dimens.xxxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "All teams are already in favorites" else "No teams found",
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
private fun TeamSelectionRow(teamName: String, onClick: () -> Unit) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    GlassCardSmall(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamLogoCircle(teamName = teamName, size = 40, requestSize = 64)
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
                tint = hierarchy.secondary.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
