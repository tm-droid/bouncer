# Bouncer Haptic System Usage Guide

## Overview
Premium haptic feedback system for Bouncer cricket app with cricket-specific patterns.

## Quick Start

### 1. Using `rememberHaptic()` in Composables

```kotlin
@Composable
fun ScoreCard() {
    val haptic = rememberHaptic()
    
    Button(onClick = {
        haptic.mediumImpact()
        // Handle click
    }) {
        Text("Select Team")
    }
}
```

### 2. Using `hapticClickable` Modifier

```kotlin
@Composable
fun MatchCard(match: Match) {
    Card(
        modifier = Modifier
            .hapticClickable(HapticType.MediumImpact) {
                // Navigate to match details
            }
    ) {
        // Card content
    }
}
```

### 3. Cricket Event Haptics

```kotlin
@Composable
fun LiveMatchScore(viewModel: MatchViewModel) {
    val haptic = rememberHaptic()
    
    LaunchedEffect(viewModel.lastBall) {
        when (viewModel.lastBall?.runs) {
            4 -> haptic.boundary()  // Medium burst for fours
            6 -> haptic.six()       // Strong celebratory pattern
        }
        
        if (viewModel.lastBall?.isWicket == true) {
            haptic.wicket()  // Dramatic pattern
        }
    }
}
```

### 4. Score Updates

```kotlin
@Composable
fun ScoreTicker(score: Int) {
    val haptic = rememberHaptic()
    
    LaunchedEffect(score) {
        haptic.scoreTick()  // Subtle tick for each run
    }
}
```

## Available Haptic Types

### UI Interactions
- `lightTap()` - Button presses, tab switches
- `mediumImpact()` - Card selections, toggles
- `heavyImpact()` - Important actions, milestones
- `success()` - Completed actions (double tap)
- `error()` - Errors (triple quick pattern)
- `swipe()` - Light feedback during swipe gestures
- `selectionTick()` - Very light tick for selection changes

### Cricket-Specific
- `scoreTick()` - Subtle tick for score changes
- `boundary()` - Medium burst for 4s
- `six()` - Strong celebratory pattern for 6s
- `wicket()` - Dramatic pattern for wickets

## Advanced Usage

### Custom Haptic Logic with Controller

```kotlin
@Composable
fun SwipeableMatchCard() {
    val haptic = rememberHaptic()
    
    Box(
        modifier = Modifier
            .clickableWithHaptic { controller ->
                // Custom logic
                if (isImportantMatch) {
                    controller.heavyImpact()
                } else {
                    controller.lightTap()
                }
            }
    )
}
```

### Direct Object Usage (Non-Composable)

```kotlin
fun onBallUpdate(view: View, ball: Ball) {
    when {
        ball.isWicket -> BouncerHaptics.wicket(view)
        ball.runs == 6 -> BouncerHaptics.six(view)
        ball.runs == 4 -> BouncerHaptics.boundary(view)
        else -> BouncerHaptics.scoreTick(view)
    }
}
```

## Implementation Details

- Uses `HapticFeedbackConstants` for standard patterns
- Falls back to `VibrationEffect` (API 26+) for custom patterns
- Automatic API level handling (R/Q/O compatibility)
- Coroutine-based for complex multi-tap patterns
- Zero performance overhead when not in use

## Best Practices

1. **Don't Overuse**: Reserve haptics for meaningful interactions
2. **Match Intensity**: Use light haptics for frequent actions, heavy for rare events
3. **Cricket Context**: Use cricket-specific haptics for match events
4. **Performance**: Haptics are lightweight, but avoid triggering on every frame
5. **Accessibility**: Haptics should complement, not replace visual feedback

## Integration Example

```kotlin
@Composable
fun MatchScreen(viewModel: MatchViewModel) {
    val haptic = rememberHaptic()
    
    // UI interactions
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = {
                    haptic.lightTap()
                    navController.navigateUp()
                }
            ) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
        }
    )
    
    // Cricket events
    LaunchedEffect(viewModel.currentBall) {
        viewModel.currentBall?.let { ball ->
            when {
                ball.isWicket -> haptic.wicket()
                ball.runs == 6 -> haptic.six()
                ball.runs == 4 -> haptic.boundary()
                ball.runs > 0 -> haptic.scoreTick()
            }
        }
    }
    
    // Card interactions
    MatchCard(
        modifier = Modifier.hapticClickable(HapticType.MediumImpact) {
            viewModel.selectMatch()
        }
    )
}
```
