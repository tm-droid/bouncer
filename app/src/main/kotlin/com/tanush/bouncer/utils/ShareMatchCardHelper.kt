package com.tanush.bouncer.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import io.sentry.Sentry
import io.sentry.Breadcrumb
import java.io.File
import java.io.FileOutputStream

object ShareMatchCardHelper {

    fun shareMatchAsText(
        context: Context,
        team1: String,
        team2: String,
        score1: String,
        score2: String,
        status: String,
        matchTitle: String,
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "share"
            message = "Sharing match: $team1 vs $team2"
        })

        val text = buildString {
            appendLine("🏏 $matchTitle")
            appendLine()
            appendLine("$team1: $score1")
            appendLine("$team2: $score2")
            appendLine()
            appendLine(status)
            appendLine()
            appendLine("📱 Shared via Bouncer Cricket")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Match Score"))
    }

    fun shareMatchAsImage(
        context: Context,
        team1: String,
        team2: String,
        score1: String,
        score2: String,
        overs1: String,
        overs2: String,
        status: String,
        matchTitle: String,
    ) {
        try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "share"
                message = "Sharing match card image: $team1 vs $team2"
            })

            val bitmap = generateMatchCardBitmap(
                team1, team2, score1, score2, overs1, overs2, status, matchTitle
            )

            val cachePath = File(context.cacheDir, "share_images")
            cachePath.mkdirs()
            val file = File(cachePath, "match_card.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "🏏 $team1 vs $team2 — Shared via Bouncer Cricket")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Match Card"))
        } catch (e: Exception) {
            Sentry.captureException(e)
            // Fall back to text share
            shareMatchAsText(context, team1, team2, score1, score2, status, matchTitle)
        }
    }

    private fun generateMatchCardBitmap(
        team1: String, team2: String,
        score1: String, score2: String,
        overs1: String, overs2: String,
        status: String, matchTitle: String,
    ): Bitmap {
        val width = 720
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark background
        val bgPaint = Paint().apply { color = 0xFF1A1A2E.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Gold accent bar at top
        val accentPaint = Paint().apply { color = 0xFFD4A843.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), 6f, accentPaint)

        // Title
        val titlePaint = Paint().apply {
            color = 0xFFD4A843.toInt()
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(matchTitle.take(40), width / 2f, 50f, titlePaint)

        // Team 1
        val teamPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(team1.take(20), 40f, 130f, teamPaint)

        val scorePaint = Paint().apply {
            color = 0xFFD4A843.toInt()
            textSize = 48f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(score1, (width - 40).toFloat(), 130f, scorePaint)

        // Overs 1
        val oversPaint = Paint().apply {
            color = 0xFF888888.toInt()
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        if (overs1.isNotBlank()) {
            canvas.drawText("($overs1)", (width - 40).toFloat(), 160f, oversPaint)
        }

        // Divider
        val dividerPaint = Paint().apply { color = 0xFF333355.toInt(); strokeWidth = 2f }
        canvas.drawLine(40f, 185f, (width - 40).toFloat(), 185f, dividerPaint)

        // Team 2
        canvas.drawText(team2.take(20), 40f, 240f, teamPaint)
        canvas.drawText(score2, (width - 40).toFloat(), 240f, scorePaint)
        if (overs2.isNotBlank()) {
            canvas.drawText("($overs2)", (width - 40).toFloat(), 270f, oversPaint)
        }

        // Status
        val statusPaint = Paint().apply {
            color = 0xFFD4A843.toInt()
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(status.take(60), width / 2f, 330f, statusPaint)

        // Branding
        val brandPaint = Paint().apply {
            color = 0xFF555555.toInt()
            textSize = 18f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Shared via Bouncer Cricket 🏏", width / 2f, 380f, brandPaint)

        return bitmap
    }
}
