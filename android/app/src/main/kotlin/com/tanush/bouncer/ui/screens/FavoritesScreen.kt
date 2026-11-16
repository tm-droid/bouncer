package com.tanush.bouncer.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.width
import com.tanush.bouncer.ui.theme.rememberTeamInk
import com.tanush.bouncer.ui.components.v2.TeamCrest
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.launch

/** The side's place in the saved order, as a quiet tabular figure — it was a gold badge. */
@Composable
private fun PositionBadge(position: Int) {
    Text(
        text = position.toString(),
        style = BouncerThemeV2.typography.numeric,
        color = BouncerThemeV2.colors.textSecondary,
        modifier = Modifier.width(20.dp),
    )
}

/** The crest, or the team's code when there is none — unknown sides were a bare grey circle. */
@Composable
private fun TeamLogoCircle(teamName: String, size: Int) {
    TeamCrest(teamName = teamName, size = size.dp, ring = true)
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
    val resources = LocalResources.current

    val favorites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()
    var showAddTeamSheet by rememberSaveable { mutableStateOf(false) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var lastRemovedTeam by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val removeWithUndo: (String) -> Unit = { teamName ->
        lastRemovedTeam = teamName
        FavouriteTeamsManager.remove(teamName)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.favorites_team_removed, teamName),
                actionLabel = resources.getString(R.string.common_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                lastRemovedTeam?.let { FavouriteTeamsManager.add(it) }
                lastRemovedTeam = null
            }
        }
    }

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
                        contentDescription = stringResource(R.string.common_back),
                        tint = colors.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.favorites_favorites),
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
                            contentDescription = stringResource(R.string.favorites_add_team),
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
                                tint = if (isEditMode) colors.primaryText else colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (isEditMode) "Drag to reorder" else "Saved teams",
                                style = typography.labelMedium,
                                color = if (isEditMode) colors.primaryText else colors.textSecondary,
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
                                        removeWithUndo(teamName)
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
                                            contentDescription = stringResource(R.string.common_remove),
                                            tint = colors.cricket.onEvent,
                                        )
                                    }
                                },
                            ) {
                                FavoriteTeamListRow(
                                    teamName = teamName,
                                    position = index + 1,
                                    onClick = { onTeamClick(teamName) },
                                    onRemove = { removeWithUndo(teamName) },
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.favorites_add_team))
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
                Text(stringResource(R.string.common_favorite_teams), style = typography.headlineMedium, color = colors.onSurface)
                Text(
                    stringResource(R.string.favorites_quick_access_to_your_teams),
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Text(text = count.toString(), style = typography.scoreLarge, color = colors.onSurface)
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

    BouncerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PositionBadge(position = position)
            TeamLogoCircle(teamName = teamName, size = 40)
            Text(
                text = teamName,
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = rememberTeamInk(teamName),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                ReorderControlButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.favorites_move_up),
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                )
                ReorderControlButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.favorites_move_down),
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_remove),
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
            TeamLogoCircle(teamName = teamName, size = 40)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = teamName,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = rememberTeamInk(teamName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemove()
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_remove),
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyFavoritesState(onAddTeam: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateV2(
            icon = Icons.Outlined.SportsCricket,
            title = stringResource(R.string.favorites_no_favorite_teams_yet),
            subtitle = stringResource(R.string.favorites_add_teams_to_quickly_access_fixtures),
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
    val focusManager = LocalFocusManager.current

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
            Text(text = stringResource(R.string.favorites_add_team), style = typography.headlineMedium, color = colors.onSurface)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.favorites_search_teams)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
            TeamLogoCircle(teamName = teamName, size = 40)
            Text(
                text = teamName,
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = rememberTeamInk(teamName),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.favorites_add),
                tint = colors.primaryText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
