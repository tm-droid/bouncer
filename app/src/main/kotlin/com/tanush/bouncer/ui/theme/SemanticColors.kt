package com.tanush.bouncer.ui.theme

/**
 * Bouncer Semantic Color System
 * 
 * This file documents the semantic color token system that replaces
 * hardcoded gold (#D4AF37) references with dynamic, team-based colors.
 * 
 * ## Migration Guide
 * 
 * ### OLD (Hardcoded Gold):
 * ```kotlin
 * Box(modifier = Modifier.background(GoldPrimary))
 * Text("Score", color = GoldPrimary)
 * ```
 * 
 * ### NEW (Dynamic Team Colors):
 * ```kotlin
 * val teamColors = rememberTeamColors("Mumbai Indians")
 * Box(modifier = Modifier.background(teamColors.primary))
 * Text("Score", color = teamColors.primary)
 * ```
 * 
 * ## Semantic Color Categories
 * 
 * ### 1. Team Colors (Dynamic)
 * Use team colors for match-specific UI elements:
 * - `teamColors.primary` - Team's main color (replaces GoldPrimary in match context)
 * - `teamColors.secondary` - Team's secondary color
 * - `teamColors.accent` - Subtle accent (20% opacity of primary)
 * - `teamColors.onPrimary` - Auto-contrast text color
 * 
 * **When to use:**
 * - Match cards, scoreboards
 * - Team headers, badges
 * - Live match indicators
 * - Team-specific charts
 * 
 * ### 2. Brand Colors (Static)
 * Use for Bouncer brand identity (non-match UI):
 * - `GoldPrimary` (#D4AF37) - Premium brand accent
 * - `GoldDark` - Darker gold variant
 * - `GoldLight` - Lighter gold variant
 * 
 * **When to use:**
 * - App chrome (bottom nav, headers)
 * - Premium features, badges
 * - Onboarding, settings
 * - Generic UI without team context
 * 
 * ### 3. Semantic Status Colors
 * Use for states and feedback:
 * - `StatusSuccess` (Green) - Success, wins, boundaries
 * - `StatusError` (Red) - Errors, losses, wickets
 * - `StatusWarning` (Orange) - Warnings, delays
 * - `StatusInfo` (Blue) - Information, live indicators
 * 
 * **When to use:**
 * - Alerts, toasts
 * - Form validation
 * - Match outcomes
 * - Ball-by-ball events
 * 
 * ### 4. Ball Event Colors
 * Cricket-specific event colors:
 * - `BallDot` (Gray) - Dot balls
 * - `BallBoundary` (Green) - Fours
 * - `BallSix` (Gold) - Sixes
 * - `BallWicket` (Red) - Wickets
 * - `BallWide` (Amber) - Wide balls
 * - `BallNoBall` (Orange) - No balls
 * 
 * ### 5. Background & Surface Colors
 * Dark theme hierarchy:
 * - `BackgroundBase` (#0F1115) - App background
 * - `BackgroundElevated` (#1A1A1A) - Elevated surfaces
 * - `Surface1` - First elevation level
 * - `Surface2` - Second elevation level
 * - `Surface3` - Third elevation level
 * 
 * ### 6. Text Colors
 * Hierarchical text colors:
 * - `TextPrimary` (#F9FAFB) - Primary text
 * - `TextSecondary` (#9CA3AF) - Secondary text
 * - `TextTertiary` (#6B7280) - Tertiary text
 * - `TextMuted` (#4B5563) - Muted/disabled text
 * 
 * ### 7. Glass Effects
 * Glass-morphism colors:
 * - `GlassBackground` - Frosted glass background
 * - `GlassBorder` - Glass border
 * - `GlassOverlay` - Glass overlay
 * 
 * ## Usage Examples
 * 
 * ### Example 1: Match Card with Team Colors
 * ```kotlin
 * @Composable
 * fun MatchCard(match: CricketMatch) {
 *     val homeColors = rememberTeamColors(match.teams[0].name)
 *     val awayColors = rememberTeamColors(match.teams[1].name)
 *     
 *     Card(
 *         modifier = Modifier
 *             .background(
 *                 Brush.horizontalGradient(
 *                     colors = listOf(
 *                         homeColors.primary.copy(alpha = 0.1f),
 *                         awayColors.primary.copy(alpha = 0.1f)
 *                     )
 *                 )
 *             )
 *     ) {
 *         Row {
 *             TeamSection(
 *                 team = match.teams[0],
 *                 color = homeColors.primary
 *             )
 *             TeamSection(
 *                 team = match.teams[1],
 *                 color = awayColors.primary
 *             )
 *         }
 *     }
 * }
 * ```
 * 
 * ### Example 2: Score with Auto-Contrast
 * ```kotlin
 * @Composable
 * fun TeamScore(teamName: String, score: String) {
 *     val teamColors = rememberTeamColors(teamName)
 *     
 *     Box(
 *         modifier = Modifier.background(teamColors.primary)
 *     ) {
 *         Text(
 *             text = score,
 *             color = teamColors.onPrimary, // Auto white/black
 *             fontSize = 32.sp,
 *             fontWeight = FontWeight.Bold
 *         )
 *     }
 * }
 * ```
 * 
 * ### Example 3: Ball-by-Ball with Event Colors
 * ```kotlin
 * @Composable
 * fun BallIcon(ball: String) {
 *     val color = when {
 *         ball == "0" -> BallDot
 *         ball == "4" -> BallBoundary
 *         ball == "6" -> BallSix
 *         ball == "W" -> BallWicket
 *         ball.contains("wd") -> BallWide
 *         else -> BallRun
 *     }
 *     
 *     Box(
 *         modifier = Modifier
 *             .size(32.dp)
 *             .background(color, CircleShape)
 *     ) {
 *         Text(ball, color = color.contrastingTextColor())
 *     }
 * }
 * ```
 * 
 * ### Example 4: Generic UI (No Team Context)
 * ```kotlin
 * @Composable
 * fun BottomNavBar() {
 *     NavigationBar(
 *         containerColor = BackgroundElevated
 *     ) {
 *         NavigationBarItem(
 *             selected = true,
 *             onClick = { },
 *             icon = { Icon(...) },
 *             colors = NavigationBarItemDefaults.colors(
 *                 selectedIconColor = GoldPrimary, // Use brand gold
 *                 indicatorColor = GoldPrimary.copy(alpha = 0.2f)
 *             )
 *         )
 *     }
 * }
 * ```
 * 
 * ### Example 5: Ensuring Contrast
 * ```kotlin
 * @Composable
 * fun DynamicBadge(teamName: String) {
 *     val teamColors = rememberTeamColors(teamName)
 *     
 *     // Ensure badge background is dark enough
 *     val safeBackground = teamColors.primary
 *         .ensureContrast(TextPrimary, minimumRatio = 4.5f)
 *     
 *     Box(
 *         modifier = Modifier.background(safeBackground)
 *     ) {
 *         Text(
 *             text = teamName,
 *             color = TextPrimary
 *         )
 *     }
 * }
 * ```
 * 
 * ## Decision Tree: Which Color to Use?
 * 
 * ```
 * Is this a match-specific UI element?
 *   ├─ YES → Use team colors (rememberTeamColors)
 *   │    └─ Primary: teamColors.primary
 *   │    └─ Accent: teamColors.accent
 *   │    └─ Text on color: teamColors.onPrimary
 *   │
 *   └─ NO → Is this a status/feedback?
 *        ├─ YES → Use semantic status colors
 *        │    └─ Success → StatusSuccess (green)
 *        │    └─ Error → StatusError (red)
 *        │    └─ Warning → StatusWarning (orange)
 *        │    └─ Info → StatusInfo (blue)
 *        │
 *        └─ NO → Use brand colors
 *             └─ Accent → GoldPrimary
 *             └─ Background → BackgroundBase/Surface1/2/3
 *             └─ Text → TextPrimary/Secondary/Tertiary
 * ```
 * 
 * ## Accessibility Guidelines
 * 
 * All color combinations must meet WCAG AA standards:
 * - **Normal text**: 4.5:1 contrast ratio minimum
 * - **Large text**: 3:1 contrast ratio minimum
 * - **UI components**: 3:1 contrast ratio minimum
 * 
 * Use the provided utilities:
 * - `color.contrastingTextColor()` - Get white or black
 * - `color.ensureContrast(background)` - Adjust for minimum contrast
 * - `color.meetsWCAG_AA(background)` - Check compliance
 * 
 * ## Performance Considerations
 * 
 * - Use `rememberTeamColors()` to cache team colors
 * - Avoid recalculating colors on every recomposition
 * - Use `derivedStateOf` for computed colors
 * - Team colors are memoized by team name
 * 
 * ## Testing
 * 
 * Test with multiple teams to ensure colors work:
 * - Light teams (Australia gold, RCB red)
 * - Dark teams (New Zealand black, Pakistan green)
 * - Unknown teams (should get deterministic hash colors)
 * - Both light and dark backgrounds
 * 
 * @see TeamColorScheme.kt for team color implementation
 * @see ColorUtils.kt for contrast utilities
 * @see BouncerColors.kt for color definitions
 */
