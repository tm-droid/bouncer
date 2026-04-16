package com.tanush.bouncer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.services.LiveCommentaryManager
import com.tanush.bouncer.ui.ScorecardTab_Extracted
import com.tanush.bouncer.ui.StatisticsTab_Extracted
import com.tanush.bouncer.ui.theme.BgBase
import com.tanush.bouncer.ui.theme.Gold400
import com.tanush.bouncer.ui.theme.TextPrimary
import com.tanush.bouncer.ui.theme.TextSecondary
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ScorecardLoadState(
    val scorecard: Scorecard?,
    val isLoading: Boolean,
    val errorMessage: String?,
    val refresh: () -> Unit
)

@Composable
private fun rememberScorecardLoadState(matchId: String): ScorecardLoadState {
    val scraper = remember { CricBuzzScraper() }
    val scope = rememberCoroutineScope()
    var scorecard by remember(matchId) { mutableStateOf<Scorecard?>(null) }
    var isLoading by remember(matchId) { mutableStateOf(true) }
    var errorMessage by remember(matchId) { mutableStateOf<String?>(null) }

    val refresh: () -> Unit = remember(matchId, scope, scraper) {
        {
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    scorecard = scraper.fetchScorecard(matchId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Sentry.captureException(error)
                    errorMessage = error.message ?: "Could not load scorecard."
                } finally {
                    isLoading = false
                }
            }
            Unit
        }
    }

    LaunchedEffect(matchId) {
        refresh()
    }

    return ScorecardLoadState(
        scorecard = scorecard,
        isLoading = isLoading,
        errorMessage = errorMessage,
        refresh = refresh
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchCommentaryScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val commentaryManager = remember { LiveCommentaryManager() }
    val keyNames = remember(match) { match.teams.mapNotNull { it.name }.ifEmpty { listOf(match.matchTitle) } }
    var commentary by remember(match.id) { mutableStateOf(listOf("Syncing live commentary...")) }
    var isRefreshing by remember(match.id) { mutableStateOf(false) }

    LaunchedEffect(match.id) {
        while (true) {
            try {
                isRefreshing = true
                val lines = commentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/${match.id}",
                    keyNames = keyNames
                )
                commentary = if (lines.isNotEmpty()) {
                    lines
                } else {
                    listOf("Commentary stream has no new updates yet.")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Sentry.captureException(error)
                commentary = listOf("Unable to refresh commentary: ${error.message ?: "unknown error"}")
            } finally {
                isRefreshing = false
            }
            delay(8000)
        }
    }

    Scaffold(
        containerColor = BgBase,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            text = "LIVE COMMENTARY",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gold400,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = match.matchTitle,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp),
                            strokeWidth = 2.dp,
                            color = Gold400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(commentary, key = { index, line -> "$index-$line" }) { index, line ->
                var visible by remember(line) { mutableStateOf(false) }
                LaunchedEffect(line) { visible = true }
                val cardAlpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0.25f,
                    animationSpec = tween(
                        durationMillis = 320,
                        delayMillis = (index * 35).coerceAtMost(180)
                    ),
                    label = "commentaryFadeIn"
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Gold400.copy(alpha = if (index == 0) 0.35f else 0.15f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = cardAlpha }
                        .semantics {
                            contentDescription = "Commentary update ${commentary.size - index}"
                        }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = "UPDATE ${commentary.size - index}",
                            color = Gold400.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = line,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScorecardScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val scorecardState = rememberScorecardLoadState(match.id)

    Scaffold(
        containerColor = BgBase,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "FULL SCORECARD",
                        fontWeight = FontWeight.Bold,
                        color = Gold400,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = scorecardState.refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh scorecard",
                            tint = Gold400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (scorecardState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Gold400,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }

            if (scorecardState.errorMessage != null && scorecardState.scorecard == null) {
                ErrorStateCard(
                    title = "Scorecard unavailable",
                    message = scorecardState.errorMessage,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                ScorecardTab_Extracted(scorecard = scorecardState.scorecard)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchAnalysisScreen(
    match: CricketMatch,
    onBackClick: () -> Unit
) {
    val scorecardState = rememberScorecardLoadState(match.id)

    Scaffold(
        containerColor = BgBase,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MATCH ANALYSIS",
                        fontWeight = FontWeight.Bold,
                        color = Gold400,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = scorecardState.refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh analysis",
                            tint = Gold400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (scorecardState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Gold400,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }

            if (scorecardState.errorMessage != null && scorecardState.scorecard == null) {
                ErrorStateCard(
                    title = "Analysis unavailable",
                    message = scorecardState.errorMessage,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                StatisticsTab_Extracted(match = match, scorecard = scorecardState.scorecard)
            }
        }
    }
}

@Composable
private fun ErrorStateCard(
    title: String,
    message: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        border = BorderStroke(1.dp, Gold400.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Gold400,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message ?: "Please try again in a moment.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
