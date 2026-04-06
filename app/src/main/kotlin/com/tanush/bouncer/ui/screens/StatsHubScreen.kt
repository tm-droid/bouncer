package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHubScreen(
    onBackClick: () -> Unit,
    onOpenTrophy: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenAchievements: () -> Unit
) {
    val colors = BouncerDesignSystem.colors

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "STATISTICS HUB",
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Overview Cards
                GlassCard(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    importance = BouncerImportance.CRITICAL
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("OVERVIEW", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(label = "Matches", value = "1,234", trend = 2.3f)
                            StatCard(label = "Runs", value = "98,321", trend = 1.2f)
                            StatCard(label = "Wkts", value = "4,321", trend = -0.6f)
                        }
                    }
                }

                // Trending Stats
                GlassCard(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    importance = BouncerImportance.STANDARD
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TRENDING", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        MiniSparkline(data = listOf(120f, 130f, 125f, 140f, 150f, 160f), color = colors.goldPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(label = "Form", value = "+8%", trend = 8f)
                            StatCard(label = "Strike", value = "+3%", trend = 3f)
                        }
                    }
                }

                // Quick Links
                GlassCard(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    importance = BouncerImportance.STANDARD
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("QUICK LINKS", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = onOpenAnalytics) { Text("Open Analytics") }
                            Button(onClick = onOpenTrophy) { Text("Trophy Room") }
                            Button(onClick = onOpenAchievements) { Text("Achievements") }
                        }
                    }
                }

                // Preserve access to other stats screens
                GlassCard(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    importance = BouncerImportance.MINIMAL
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DEEP DIVE", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Compare players, run custom filters, or open detailed match stats from here.", color = colors.textPrimary)
                    }
                }
            }
        }
    }
}
