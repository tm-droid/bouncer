package com.tanush.bouncer.services

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class CommentaryScorer {
    // Example keywords for excitement/sentiment
    private val excitementWords = setOf(
        "six", "four", "wicket", "out!", "amazing", "unbelievable", 
        "incredible", "massive", "huge", "crucial", "fifty", "hundred", 
        "milestone", "record"
    )
    
    // Single source of truth for non-ball content — partitioned by type
    private val categoryRules: List<Pair<Set<String>, CommentaryType>> = listOf(
        setOf(
            "toss", "pitch report", "pitch condition", "weather", "playing xi", "playing 11",
            "team news", "coin", "elected to", "won the toss", "preview"
        ) to CommentaryType.PRE_MATCH,
        setOf(
            "interview", "presentation", "man of the match", "player of the match",
            "post-match", "post match", "ceremony", "handshake", "trophy"
        ) to CommentaryType.POST_MATCH,
        setOf(
            "innings break", "strategic timeout", "drinks break", "rain delay",
            "bad light", "covers", "super over", "dls", "revised target"
        ) to CommentaryType.MATCH_BREAK,
        setOf(
            "welcome", "good morning", "good evening", "good afternoon",
            "joining us", "studio", "broadcast", "expert", "analyst", "recap", "captain"
        ) to CommentaryType.UNKNOWN,
    )
    // Flat set derived automatically — no duplication
    private val nonBallKeywords: Set<String> = categoryRules.flatMap { (kw, _) -> kw }.toSet()
    private val ballIndicatorKeywords = setOf(
        "to ", "runs", "dot", "single", "double", "boundary",
        "bowled", "lbw", "caught", "stumped", "run out", "no ball", "wide", "appeal"
    )
    
    // Regex pattern for ball number format (e.g., "0.1", "18.5", "45.6")
    private val ballNumberPattern = Regex("^\\d{1,2}\\.\\d\\s*[:,]?")
    private val ballNumberLoosePattern = Regex("\\d{1,2}\\.\\d\\s")
    
    /**
     * Determines if the commentary is ball-by-ball content
     * Returns true if it appears to be actual ball commentary
     */
    fun isBallByBallCommentary(snippet: String): Boolean {
        try {
            if (snippet.isBlank()) return false
            val lower = snippet.lowercase().trim()
            if (nonBallKeywords.any { lower.contains(it) }) return false
            return ballNumberPattern.containsMatchIn(snippet)
                || snippet.contains(ballNumberLoosePattern)
                || ballIndicatorKeywords.any { lower.contains(it) }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Categorizes the type of non-ball commentary
     */
    fun categorizeCommentary(snippet: String): CommentaryType {
        try {
            if (snippet.isBlank()) return CommentaryType.UNKNOWN
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.commentary"
                message = "Categorizing commentary"
                level = SentryLevel.DEBUG
                setData("snippetLength", snippet.length)
            })
            val lower = snippet.lowercase()
            return categoryRules
                .firstOrNull { (kw, _) -> kw.any { lower.contains(it) } }?.second
                ?: if (isBallByBallCommentary(snippet)) CommentaryType.BALL_BY_BALL else CommentaryType.UNKNOWN
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    enum class CommentaryType {
        BALL_BY_BALL,
        PRE_MATCH,
        POST_MATCH,
        MATCH_BREAK,
        UNKNOWN
    }

    /**
     * Scores a commentary snippet based on length, sentiment, key names, and repetition
     * Also considers whether it's ball-by-ball content
     */
    fun score(snippet: String, keyNames: List<String>, recentHistory: List<String>, requireBallByBall: Boolean = true): Double {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.commentary"
                message = "Scoring commentary snippet"
                level = SentryLevel.DEBUG
                setData("keyNamesCount", keyNames.size)
                setData("historyCount", recentHistory.size)
            })
            
            var score = 0.0
        
            // Heavily penalize non-ball-by-ball content when expecting ball commentary
            if (requireBallByBall && !isBallByBallCommentary(snippet)) {
                score -= 5.0
            }
        
            // Length: prefer 40-180 chars
            val length = snippet.length
            if (length in 41..179) {
                score += 1.0
            } else if (length >= 180) {
                score += 0.5
            } else {
                score -= 0.5
            }

            // Sentiment/excitement
            val lower = snippet.lowercase()
            if (excitementWords.any { lower.contains(it) }) {
                score += 1.0
            }
            if (snippet.contains("!")) {
                score += 0.5
            }

            // Key player/team names
            for (name in keyNames) {
                if (lower.contains(name.lowercase())) {
                    score += 0.5
                }
            }

            // Repetition: penalize if similar to recent
            val recentToCheck = recentHistory.takeLast(3)
            for (recent in recentToCheck) {
                if (recent.lowercase().trim() == lower.trim()) {
                    score -= 1.0
                }
            }

            return score
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    enum class ComparisonResult {
        UseFirst,
        UseSecond,
        FuseBoth
    }

    /**
     * Compares two snippets and decides which to use, or if both should be fused by AI
     * When match is live, prefers ball-by-ball content
     */
    fun compare(
        snippet1: String, 
        snippet2: String, 
        keyNames: List<String>, 
        recentHistory: List<String>, 
        threshold: Double = 2.0, // Increased threshold to favor fusion
        requireBallByBall: Boolean = true
    ): ComparisonResult {
        // First check if either is valid ball-by-ball content
        val isBall1 = isBallByBallCommentary(snippet1)
        val isBall2 = isBallByBallCommentary(snippet2)
        
        // If neither is ball-by-ball and we require it, return a special indicator
        // The caller should handle showing appropriate state for non-ball content
        if (requireBallByBall && !isBall1 && !isBall2) {
            // Both are non-ball content (like pitch report, interview)
            // Still return the better one, but caller should know it's not ball content
            val score1 = score(snippet1, keyNames, recentHistory, requireBallByBall = false)
            val score2 = score(snippet2, keyNames, recentHistory, requireBallByBall = false)
            return if (score1 >= score2) ComparisonResult.UseFirst else ComparisonResult.UseSecond
        }
        
        // If only one is ball-by-ball, prefer that one
        if (requireBallByBall) {
            if (isBall1 && !isBall2) return ComparisonResult.UseFirst
            if (isBall2 && !isBall1) return ComparisonResult.UseSecond
        }
        
        val score1 = score(snippet1, keyNames, recentHistory, requireBallByBall)
        val score2 = score(snippet2, keyNames, recentHistory, requireBallByBall)
        
        // If both have decent content (score > 1.0), prefer fusion unless one is garbage
        if (score1 > 1.0 && score2 > 1.0) {
            return ComparisonResult.FuseBoth
        }
        
        return if (Math.abs(score1 - score2) < threshold) {
            ComparisonResult.FuseBoth
        } else if (score1 > score2) {
            ComparisonResult.UseFirst
        } else {
            ComparisonResult.UseSecond
        }
    }
}
