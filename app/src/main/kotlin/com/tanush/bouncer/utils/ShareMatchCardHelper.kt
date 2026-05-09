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

    private val GOLD = 0xFFD4A843.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val GREY = 0xFF888888.toInt()
    private val DARK_BG = 0xFF1A1A2E.toInt()
    private val DIVIDER = 0xFF333355.toInt()
    private val BRAND = 0xFF555555.toInt()

    private fun textPaint(
        textColor: Int,
        size: Float,
        align: Paint.Align = Paint.Align.LEFT,
        style: Int = Typeface.NORMAL,
    ): Paint = Paint().apply {
        color = textColor
        textSize = size
        isAntiAlias = true
        textAlign = align
        if (style != Typeface.NORMAL) typeface = Typeface.create(Typeface.DEFAULT, style)
    }

    private fun fillPaint(fillColor: Int, stroke: Float = 0f): Paint = Paint().apply {
        color = fillColor
        if (stroke > 0f) strokeWidth = stroke
    }

    private fun generateMatchCardBitmap(
        team1: String, team2: String,
        score1: String, score2: String,
        overs1: String, overs2: String,
        status: String, matchTitle: String,
    ): Bitmap {
        val width = 720
        val height = 400
        val rightX = (width - 40).toFloat()
        val centerX = width / 2f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint(DARK_BG))
        canvas.drawRect(0f, 0f, width.toFloat(), 6f, fillPaint(GOLD))

        canvas.drawText(matchTitle.take(40), centerX, 50f,
            textPaint(GOLD, 28f, Paint.Align.CENTER, Typeface.BOLD))

        val teamPaint = textPaint(WHITE, 36f, style = Typeface.BOLD)
        val scorePaint = textPaint(GOLD, 48f, Paint.Align.RIGHT, Typeface.BOLD)
        val oversPaint = textPaint(GREY, 22f, Paint.Align.RIGHT)

        canvas.drawText(team1.take(20), 40f, 130f, teamPaint)
        canvas.drawText(score1, rightX, 130f, scorePaint)
        if (overs1.isNotBlank()) canvas.drawText("($overs1)", rightX, 160f, oversPaint)

        canvas.drawLine(40f, 185f, rightX, 185f, fillPaint(DIVIDER, stroke = 2f))

        canvas.drawText(team2.take(20), 40f, 240f, teamPaint)
        canvas.drawText(score2, rightX, 240f, scorePaint)
        if (overs2.isNotBlank()) canvas.drawText("($overs2)", rightX, 270f, oversPaint)

        canvas.drawText(status.take(60), centerX, 330f,
            textPaint(GOLD, 24f, Paint.Align.CENTER, Typeface.ITALIC))
        canvas.drawText("Shared via Bouncer Cricket 🏏", centerX, 380f,
            textPaint(BRAND, 18f, Paint.Align.CENTER))

        return bitmap
    }
}
