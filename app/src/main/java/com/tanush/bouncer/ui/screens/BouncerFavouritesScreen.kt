package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.etchedText
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Team data model for the picker
// ─────────────────────────────────────────────────────────────────────────────

private data class TeamCategory(
    val title: String,
    val teams: List<String>
)

private val popularTeams = listOf(
    TeamCategory(
        title = "International",
        teams = listOf(
            "India", "Australia", "England", "New Zealand", "South Africa",
            "Pakistan", "Sri Lanka", "West Indies", "Bangladesh", "Afghanistan",
            "Ireland", "Zimbabwe", "Netherlands", "Nepal", "Scotland", "USA"
        )
    ),
    TeamCategory(
        title = "IPL",
        teams = listOf(
            "Mumbai Indians", "Chennai Super Kings", "Royal Challengers Bengaluru",
            "Delhi Capitals", "Kolkata Knight Riders", "Sunrisers Hyderabad",
            "Rajasthan Royals", "Punjab Kings", "Gujarat Titans", "Lucknow Super Giants"
        )
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerFavouritesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val favourites by FavouriteTeamsManager.favourites.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Favourite Teams",
                        modifier = Modifier.etchedText()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BouncerDesignSystem.colors.goldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BouncerDesignSystem.colors.backgroundBase,
                    scrolledContainerColor = BouncerDesignSystem.colors.backgroundElevated
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = BouncerDesignSystem.colors.backgroundBase
    ) { innerPadding ->

        if (favourites.isEmpty()) {
            EmptyFavouritesView(
                onAddTeamsClick = { showBottomSheet = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = BouncerDimens.screenHorizontal,
                    end = BouncerDimens.screenHorizontal,
                    top = BouncerDimens.sm,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(BouncerDimens.sm),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = BouncerDimens.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${favourites.size}/10 teams",
                            style = BouncerDesignSystem.typography.bodyMedium,
                            color = BouncerDesignSystem.colors.textSecondary
                        )
                        OutlinedButton(
                            onClick = { showBottomSheet = true },
                            enabled = favourites.size < 10,
                            contentPadding = PaddingValues(
                                horizontal = BouncerDimens.lg,
                                vertical = BouncerDimens.xs
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(BouncerDimens.xs))
                            Text("Add Team")
                        }
                    }
                }

                itemsIndexed(
                    items = favourites,
                    key = { _, team -> team }
                ) { _, team ->
                    SwipeToDismissFavouriteCard(
                        teamName = team,
                        onRemove = { FavouriteTeamsManager.remove(team) }
                    )
                }
            }
        }

        if (showBottomSheet) {
            TeamPickerBottomSheet(
                currentFavourites = favourites,
                onDismiss = { showBottomSheet = false },
                onAddTeam = { team ->
                    if (favourites.size >= 10) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Maximum 10 teams",
                                duration = SnackbarDuration.Short
                            )
                        }
                    } else {
                        FavouriteTeamsManager.add(team)
                    }
                },
                onRemoveTeam = { team -> FavouriteTeamsManager.remove(team) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyFavouritesView(
    onAddTeamsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = BouncerDesignSystem.colors.goldPrimary.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(BouncerDimens.lg))

        Text(
            text = "No favourite teams",
            style = BouncerDesignSystem.typography.headingSmall,
            color = BouncerDesignSystem.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(BouncerDimens.sm))

        Text(
            text = "Add teams you follow to see their matches first",
            style = BouncerDesignSystem.typography.bodyMedium,
            color = BouncerDesignSystem.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = BouncerDimens.xxxl)
        )

        Spacer(modifier = Modifier.height(BouncerDimens.xxl))

        Button(
            onClick = onAddTeamsClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = BouncerDesignSystem.colors.goldPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(BouncerDimens.sm))
            Text("Add Teams")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Swipe-to-dismiss favourite card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissFavouriteCard(
    teamName: String,
    onRemove: () -> Unit
) {
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = BouncerDimens.lg)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        FavouriteTeamCard(
            teamName = teamName,
            onRemove = onRemove
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Favourite team card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FavouriteTeamCard(
    teamName: String,
    onRemove: () -> Unit
) {
    val logoUrl = TeamLogoManager.getLogoUrl(teamName)
    val acronym = TeamAcronymManager.getAcronym(teamName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerDimens.cardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = BouncerDimens.cardPadding,
                    vertical = BouncerDimens.cardInnerPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle indicator
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(BouncerDimens.md))

            // Team logo
            TeamLogo(
                teamName = teamName,
                logoUrl = logoUrl,
                size = 40.dp
            )

            Spacer(modifier = Modifier.width(BouncerDimens.md))

            // Team name
            Text(
                text = teamName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(BouncerDimens.sm))

            // Acronym badge
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = BouncerDimens.sm, vertical = BouncerDimens.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = acronym,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(BouncerDimens.xs))

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(BouncerDimens.minTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $teamName",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Team logo composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TeamLogo(
    teamName: String,
    logoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (logoUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = "$teamName logo",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    CircleShape
                )
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = TeamAcronymManager.getAcronym(teamName).take(2),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Team picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamPickerBottomSheet(
    currentFavourites: List<String>,
    onDismiss: () -> Unit,
    onAddTeam: (String) -> Unit,
    onRemoveTeam: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            popularTeams
        } else {
            val query = searchQuery.trim().lowercase()
            popularTeams.mapNotNull { category ->
                val filtered = category.teams.filter { team ->
                    team.lowercase().contains(query)
                }
                if (filtered.isNotEmpty()) category.copy(teams = filtered) else null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(BouncerDimens.md))
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(BouncerDimens.lg))
                Text(
                    text = "Add Team",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(BouncerDimens.md))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search teams\u2026",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BouncerDimens.screenHorizontal)
            )

            Spacer(modifier = Modifier.height(BouncerDimens.md))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 48.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                filteredCategories.forEach { category ->
                    item(key = "header_${category.title}") {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = BouncerDimens.screenHorizontal,
                                end = BouncerDimens.screenHorizontal,
                                top = BouncerDimens.lg,
                                bottom = BouncerDimens.sm
                            )
                        )
                    }

                    items(
                        items = category.teams,
                        key = { "${category.title}_$it" }
                    ) { team ->
                        val isFavourite = currentFavourites.contains(team)
                        TeamPickerRow(
                            teamName = team,
                            isFavourite = isFavourite,
                            onToggle = {
                                if (isFavourite) {
                                    onRemoveTeam(team)
                                } else {
                                    onAddTeam(team)
                                }
                            }
                        )
                    }
                }

                if (filteredCategories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No teams found for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Team picker row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TeamPickerRow(
    teamName: String,
    isFavourite: Boolean,
    onToggle: () -> Unit
) {
    val logoUrl = TeamLogoManager.getLogoUrl(teamName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(
                horizontal = BouncerDimens.screenHorizontal,
                vertical = BouncerDimens.md
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamLogo(
            teamName = teamName,
            logoUrl = logoUrl,
            size = 36.dp
        )

        Spacer(modifier = Modifier.width(BouncerDimens.md))

        Text(
            text = teamName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(BouncerDimens.sm))

        if (isFavourite) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Already added",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(BouncerDimens.minTapTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add $teamName",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
