package com.tanush.bouncer.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.request.ImageRequest
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.launch

private fun teamLogoRequest(context: Context, teamName: String, size: Int): ImageRequest? {
    val url = TeamLogoManager.getBestLogoUrl(teamName, null, size, size) ?: return null
    return ImageRequest.Builder(context).data(url).crossfade(true).size(size).build()
}

@Composable
private fun PositionBadge(position: Int) {
    val colors = BouncerThemeV2.colors
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(colors.primary.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = position.toString(),
            style = BouncerThemeV2.typography.labelSmall,
            color = colors.primaryDark,
        )
    }
}

@Composable
private fun TeamLogoCircle(teamName: String, size: Int, requestSize: Int) {
    val colors = BouncerThemeV2.colors
    CoilImage(
        model = teamLogoRequest(LocalContext.current, teamName, requestSize),
        contentDescription = teamName,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant, CircleShape),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun RemoveTeamDialog(
    teamName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Team") },
        text = { Text("Remove $teamName from favorites?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun FavoritesScreen(
    onBackClick: () -> Unit,
    onTeamClick: (String) -> Unit
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current

    val favorites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()
    var showAddTeamSheet by rememberSaveable { mutableStateOf(false) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var lastRemovedTeam by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var reorderableFavorites by remember(favorites) { mutableStateOf(favorites.toList()) }
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = spacing.sm, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onBackground,
                    )
                }
                Text(
                    text = "Favorites",
                    style = typography.titleLarge,
                    color = colors.onBackground,
                )
                Row {
                    if (favorites.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (isEditMode) FavouriteTeamsManager.reorder(reorderableFavorites)
                                isEditMode = !isEditMode
                            },
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "Done" else "Edit",
                                tint = if (isEditMode) colors.primary else colors.textSecondary,
                            )
                        }
                    }
                    IconButton(onClick = { showAddTeamSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Team",
                            tint = colors.textSecondary,
                        )
                    }
                }
            }

            if (favorites.isEmpty()) {
                EmptyFavoritesState(onAddTeam = { showAddTeamSheet = true })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = spacing.sm,
                        start = spacing.lg,
                        end = spacing.lg,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    item { FavoritesHeader(count = favorites.size) }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.DragHandle else Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (isEditMode) colors.primary else colors.live,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (isEditMode) "Drag to reorder" else "Saved teams",
                                style = typography.labelMedium,
                                color = if (isEditMode) colors.primary else colors.textSecondary,
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = colors.divider,
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
                                position = index + 1,
                            )
                        }
                    } else {
                        itemsIndexed(reorderableFavorites, key = { _, teamName -> teamName }) { index, teamName ->
                            val swipeState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        lastRemovedTeam = teamName
                                        FavouriteTeamsManager.remove(teamName)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "$teamName removed",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                lastRemovedTeam?.let { FavouriteTeamsManager.add(it) }
                                                lastRemovedTeam = null
                                            }
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            )
                            SwipeToDismissBox(
                                state = swipeState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(BouncerThemeV2.shapes.medium)
                                            .background(colors.error)
                                            .padding(end = spacing.xl),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = colors.cricket.onEvent,
                                        )
                                    }
                                },
                            ) {
                                FavoriteTeamListRow(
                                    teamName = teamName,
                                    position = index + 1,
                                    onClick = { onTeamClick(teamName) },
                                    onRemove = { FavouriteTeamsManager.remove(teamName) },
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(spacing.lg))
                        Text(
                            text = if (isEditMode) "Tap ✓ to save your new order" else "Tap the edit button to reorder teams",
                            style = typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (favorites.isNotEmpty() && !isEditMode) {
            FloatingActionButton(
                onClick = { showAddTeamSheet = true },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = spacing.lg, bottom = 116.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Team")
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showAddTeamSheet) {
        AddTeamBottomSheet(
            currentFavorites = favorites,
            onDismiss = { showAddTeamSheet = false },
            onTeamSelected = { teamName -> FavouriteTeamsManager.add(teamName) },
        )
    }
}

@Composable
private fun FavoritesHeader(count: Int) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    BouncerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Favorite Teams", style = typography.headlineMedium, color = colors.onSurface)
                Text("Quick access to your teams", style = typography.bodySmall, color = colors.textSecondary)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.primary.copy(alpha = 0.12f), CircleShape),
            ) {
                Text(text = count.toString(), style = typography.scoreLarge, color = colors.primaryDark)
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
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current

    BouncerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PositionBadge(position = position)
            CoilImage(
                model = teamLogoRequest(context, teamName, 80),
                contentDescription = teamName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant, CircleShape),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = teamName,
                style = typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                ReorderControlButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                )
                ReorderControlButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderControlButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    IconButton(onClick = { if (enabled) onClick() }, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.onSurface else colors.textSecondary.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun FavoriteTeamListRow(
    teamName: String,
    position: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = remember { PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    BouncerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PositionBadge(position = position)
            TeamLogoCircle(teamName = teamName, size = 40, requestSize = 96)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = teamName,
                    style = typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Tap to open team details",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = colors.textSecondary,
                )
            }
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
            },
        )
    }
}

@Composable
private fun EmptyFavoritesState(onAddTeam: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateV2(
            icon = Icons.Outlined.SportsCricket,
            title = "No favorite teams yet",
            subtitle = "Add teams to quickly access fixtures and updates.",
            onRetry = onAddTeam,
            actionLabel = "Add team",
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
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val popularTeams = listOf(
        "India", "Australia", "England", "South Africa", "Pakistan",
        "New Zealand", "West Indies", "Sri Lanka", "Bangladesh", "Afghanistan",
        "Mumbai Indians", "Chennai Super Kings", "Royal Challengers Bengaluru",
        "Kolkata Knight Riders", "Delhi Capitals", "Rajasthan Royals",
        "Punjab Kings", "Sunrisers Hyderabad", "Gujarat Titans", "Lucknow Super Giants",
    )

    val filteredTeams = if (searchQuery.isBlank()) {
        popularTeams.filterNot { currentFavorites.contains(it) }
    } else {
        popularTeams.filter { it.contains(searchQuery, ignoreCase = true) && !currentFavorites.contains(it) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl)
                .padding(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(text = "Add Team", style = typography.headlineMedium, color = colors.onSurface)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search teams...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = BouncerThemeV2.shapes.small,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(filteredTeams, key = { it }) { teamName ->
                    TeamSelectionRow(
                        teamName = teamName,
                        onClick = {
                            onTeamSelected(teamName)
                            onDismiss()
                        },
                    )
                }
                if (filteredTeams.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.xxxl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "All teams are already in favorites" else "No teams found",
                                color = colors.textSecondary,
                                style = typography.bodyMedium,
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
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    BouncerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamLogoCircle(teamName = teamName, size = 40, requestSize = 64)
            Text(
                text = teamName,
                style = typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = colors.primaryText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
