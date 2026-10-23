package com.tanush.bouncer.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import io.sentry.Sentry
import com.tanush.bouncer.ui.theme.GoldPrimary
import com.tanush.bouncer.ui.theme.GoldLight

/**
 * EliteShareCard - Premium Social Media Share Image Generator
 * 
 * Creates broadcast-quality match summary images for sharing:
 * - High-resolution output (1080x1920 or 1200x630)
 * - Premium gold/dark aesthetic matching app theme
 * - Win probability graph visualization
 * - Key player highlights
 * - Bouncer branding
 */

/**
 * Data class for share card content
 */
data class ShareCardData(
    val team1Name: String,
    val team2Name: String,
    val team1Score: String,
    val team2Score: String?,
    val matchStatus: String,
    val matchType: String,
    val venue: String? = null,
    // Null when the server supplied no probability — the share card then omits the bar rather
    // than printing a fabricated 50/50.
    val winProbability: Float? = null,
    val keyPlayer: String? = null,
    val keyPlayerStat: String? = null,
    val isLive: Boolean = false,
    /** Full team names: printed when they fit, and the key for the teams' colours. */
    val team1FullName: String? = null,
    val team2FullName: String? = null,
)

/**
 * Share card format options
 */
enum class ShareCardFormat(val width: Int, val height: Int) {
    INSTAGRAM_STORY(1080, 1920),
    INSTAGRAM_POST(1080, 1080),
    TWITTER_CARD(1200, 630),
    WHATSAPP_STATUS(1080, 1920)
}

/**
 * Main composable for the Elite Share Card preview
 */
@Composable
fun EliteShareCardPreview(
    data: ShareCardData,
    modifier: Modifier = Modifier,
    onShareClick: () -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = GoldPrimary.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.backgroundBase,
                        Color.White.copy(alpha = 0.08f),
                        colors.backgroundBase
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GoldPrimary.copy(alpha = 0.5f),
                        GoldLight.copy(alpha = 0.2f),
                        GoldPrimary.copy(alpha = 0.5f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Live Indicator + Match Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (data.isLive) {
                    Box(
                        modifier = Modifier
                            .background(colors.statusLive, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Text(
                    text = data.matchType.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                    letterSpacing = 2.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Teams Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Team Avatar
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        GoldPrimary.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                            .border(2.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.team1Name.take(3).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = data.team1Name.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = data.team1Score,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary
                    )
                }
                
                // VS
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textTertiary
                        )
                    }
                }
                
                // Team 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Team Avatar
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        colors.textSecondary.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                            .border(2.dp, colors.textSecondary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.team2Name.take(3).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = data.team2Name.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = data.team2Score ?: "-",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Status
            Text(
                text = data.matchStatus,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Win Probability Bar
            data.winProbability?.let { probability ->
                WinProbabilityShareBar(
                    probability = probability,
                    team1Name = data.team1Name,
                    team2Name = data.team2Name
                )
            }
            
            // Key Player Highlight
            data.keyPlayer?.let { player ->
                Spacer(modifier = Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    GoldPrimary.copy(alpha = 0.1f),
                                    GoldPrimary.copy(alpha = 0.2f),
                                    GoldPrimary.copy(alpha = 0.1f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.share_key_performer),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = player,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                        data.keyPlayerStat?.let { stat ->
                            Text(
                                text = stat,
                                fontSize = 14.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bouncer Branding
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .weight(1f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, GlassBorder)
                            )
                        )
                )
                
                Text(
                    text = "BOUNCER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .weight(1f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(GlassBorder, Color.Transparent)
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Share Button
            Button(
                onClick = onShareClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.goldPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.common_share),
                    tint = colors.backgroundBase,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SHARE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.backgroundBase,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun WinProbabilityShareBar(
    probability: Float,
    team1Name: String,
    team2Name: String
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(probability * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (probability > 0.5f) colors.statusSuccess else hierarchy.secondary.primary
            )
            Text(
                text = stringResource(R.string.common_win_probability),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textTertiary,
                letterSpacing = 1.sp
            )
            Text(
                text = "${((1 - probability) * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (probability < 0.5f) colors.statusLive else colors.textSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(GlassSurface)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Team 1 bar
                Box(
                    modifier = Modifier
                        .weight(probability.coerceIn(0.05f, 0.95f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                            )
                        )
                )
                
                // Team 2 bar
                Box(
                    modifier = Modifier
                        .weight((1 - probability).coerceIn(0.05f, 0.95f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(colors.textSecondary.copy(alpha = 0.5f), colors.textSecondary)
                            )
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = team1Name.take(10),
                fontSize = 10.sp,
                color = colors.textTertiary
            )
            Text(
                text = team2Name.take(10),
                fontSize = 10.sp,
                color = colors.textTertiary
            )
        }
    }
}

/**
 * Generates a bitmap image of the share card
 */
object ShareCardGenerator {
    
    /**
     * Creates a high-resolution share card bitmap
     */
    fun generateShareBitmap(
        data: ShareCardData,
        format: ShareCardFormat = ShareCardFormat.INSTAGRAM_STORY,
        /** The app's face (IBM Plex Sans Bold); the platform default bold when not supplied. */
        typeface: Typeface? = null,
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = typeface ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.typeface = bold
        paint.textAlign = Paint.Align.CENTER

        // Background: the app's dark ground, flat. The gold frame went — gold marks what can be tapped.
        paint.color = SHARE_BACKGROUND
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val centerX = width / 2f
        val name1 = data.team1FullName?.takeIf { it.length <= MAX_NAME_CHARS } ?: data.team1Name
        val name2 = data.team2FullName?.takeIf { it.length <= MAX_NAME_CHARS } ?: data.team2Name
        val key1 = data.team1FullName ?: data.team1Name
        val key2 = data.team2FullName ?: data.team2Name
        // The score block is centred in the canvas rather than starting at the top, which left the
        // lower half of a story card empty above the wordmark.
        var currentY = height * TOP_FRACTION

        paint.color = SHARE_MUTED
        paint.textSize = 36f
        canvas.drawText(data.matchType, centerX, currentY, paint)
        currentY += 70f
        if (data.isLive) {
            paint.color = SHARE_LIVE
            canvas.drawCircle(centerX - 70f, currentY - 12f, 10f, paint)
            paint.textSize = 32f
            canvas.drawText("LIVE", centerX + 10f, currentY, paint)
            currentY += 60f
        }
        currentY += 80f

        currentY = drawSide(canvas, paint, name1, data.team1Score, inkFor(key1), centerX, currentY)
        paint.color = SHARE_MUTED
        paint.textSize = 40f
        canvas.drawText("v", centerX, currentY, paint)
        currentY += 90f
        currentY = drawSide(canvas, paint, name2, data.team2Score ?: "–", inkFor(key2), centerX, currentY)

        paint.color = SHARE_TEXT_SECONDARY
        paint.textSize = 40f
        canvas.drawText(data.matchStatus, centerX, currentY + 20f, paint)
        currentY += 140f

        // Win probability — omitted entirely when the server supplied no probability.
        data.winProbability?.let { probability ->
            drawProbability(canvas, paint, probability, key1, key2, data.team1Name, data.team2Name, width, currentY)
        }

        // The wordmark: the one place gold is not an affordance.
        paint.color = SHARE_GOLD
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = bold
        canvas.drawText("Bouncer", centerX, height - 80f, paint)

        return bitmap
    }

    /** A side: its name in its colour (white for a side the registry does not know), then the score. */
    private fun drawSide(canvas: Canvas, paint: Paint, name: String, score: String, ink: Int, x: Float, y: Float): Float {
        paint.color = ink
        paint.textSize = 52f
        canvas.drawText(name, x, y, paint)
        paint.color = SHARE_TEXT
        paint.textSize = 104f
        canvas.drawText(score, x, y + 120f, paint)
        return y + 210f
    }

    private fun inkFor(team: String): Int =
        if (TeamColorRegistry.isKnown(team)) TeamColorRegistry.forTeam(team).ink.toArgb() else SHARE_TEXT

    /** Team colours on the bar only when both sides are known; two neutrals otherwise. */
    private fun drawProbability(
        canvas: Canvas,
        paint: Paint,
        probability: Float,
        key1: String,
        key2: String,
        code1: String,
        code2: String,
        width: Int,
        top: Float,
    ) {
        val bothKnown = TeamColorRegistry.isKnown(key1) && TeamColorRegistry.isKnown(key2)
        val left = width * (1 - BAR_FRACTION) / 2
        val right = left + width * BAR_FRACTION
        val split = left + (right - left) * probability.coerceIn(0f, 1f)
        val barHeight = 24f
        paint.color = if (bothKnown) TeamColorRegistry.forTeam(key2).primary.toArgb() else SHARE_MUTED
        canvas.drawRoundRect(RectF(left, top, right, top + barHeight), barHeight / 2, barHeight / 2, paint)
        paint.color = if (bothKnown) TeamColorRegistry.forTeam(key1).primary.toArgb() else SHARE_TEXT
        canvas.drawRoundRect(RectF(left, top, split, top + barHeight), barHeight / 2, barHeight / 2, paint)
        paint.color = SHARE_TEXT_SECONDARY
        paint.textSize = 32f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$code1 ${(probability * PERCENT).toInt()}%", left, top + barHeight + 44f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${((1 - probability) * PERCENT).toInt()}% $code2", right, top + barHeight + 44f, paint)
        paint.textAlign = Paint.Align.CENTER
    }

    private const val SHARE_BACKGROUND = 0xFF0C1015.toInt()
    private const val SHARE_TEXT = 0xFFFFFFFF.toInt()
    private const val SHARE_TEXT_SECONDARY = 0xFF94A3B8.toInt()
    private const val SHARE_MUTED = 0xFF64748B.toInt()
    private const val SHARE_LIVE = 0xFFEF4444.toInt()
    private const val SHARE_GOLD = 0xFFE0B74F.toInt()
    private const val MAX_NAME_CHARS = 22
    private const val TOP_FRACTION = 0.22f
    private const val BAR_FRACTION = 0.8f
    private const val PERCENT = 100
    
    fun generatePlayerInningsCard(
        playerName: String,
        teamName: String,
        runs: String,
        balls: String,
        fours: String,
        sixes: String,
        strikeRate: String,
        dismissal: String,
        matchTitle: String,
        format: ShareCardFormat = ShareCardFormat.INSTAGRAM_POST
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0xFF050505.toInt(), 0xFF141414.toInt(), 0xFF050505.toInt()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Gold top bar
        paint.color = 0xFFFFD700.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), 8f, paint)

        // Gold border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(RectF(12f, 12f, width - 12f, height - 12f), 32f, 32f, paint)
        paint.style = Paint.Style.FILL

        val centerX = width / 2f
        var y = 120f

        // Team name
        paint.color = 0xFF94A3B8.toInt()
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(teamName.uppercase(), centerX, y, paint)
        y += 80f

        // Player name
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = if (playerName.length > 18) 56f else 72f
        canvas.drawText(playerName, centerX, y, paint)
        y += 40f

        // Divider
        paint.color = 0xFF333333.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawLine(width * 0.15f, y, width * 0.85f, y, paint)
        paint.style = Paint.Style.FILL
        y += 100f

        // Runs (large)
        paint.color = 0xFFFFD700.toInt()
        paint.textSize = 160f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(runs, centerX, y, paint)
        y += 20f

        // Balls
        paint.color = 0xFF94A3B8.toInt()
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("($balls balls)", centerX, y, paint)
        y += 80f

        // Stats row: 4s | 6s | SR
        val statSpacing = width / 4f
        val statLabels = listOf("4s", "6s", "SR")
        val statValues = listOf(fours, sixes, strikeRate)
        statValues.forEachIndexed { i, value ->
            val x = statSpacing * (i + 1)
            paint.color = 0xFFFFD700.toInt()
            paint.textSize = 60f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(value, x, y, paint)
            paint.color = 0xFF64748B.toInt()
            paint.textSize = 28f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(statLabels[i], x, y + 40f, paint)
        }
        y += 120f

        // Dismissal
        if (dismissal.isNotBlank()) {
            paint.color = 0xFF64748B.toInt()
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            val dismissalText = dismissal.take(55)
            canvas.drawText(dismissalText, centerX, y, paint)
            y += 50f
        }

        // Match title
        paint.color = 0xFF475569.toInt()
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(matchTitle.take(50), centerX, y, paint)

        // Bouncer branding
        paint.color = 0xFFFFD700.toInt()
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BOUNCER", centerX, height - 60f, paint)

        return bitmap
    }

    /**
     * Saves bitmap to cache and returns shareable URI
     */
    suspend fun saveBitmapForSharing(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "bouncer_match_${System.currentTimeMillis()}.png"
    ): Uri? = withContext(Dispatchers.IO) {
        // One capture: the nested try reported every failure twice.
        try {
            val cacheDir = File(context.cacheDir, "share_images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            null
        }
    }
    
    /**
     * Launches share intent with the generated image
     */
    fun shareImage(context: Context, imageUri: Uri, text: String? = null): Boolean = try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Match Update"))
        true
    } catch (e: Exception) {
        // Reported, not rethrown: the caller runs in a composition scope, where a rethrow is a
        // crash, and its spinner was left on.
        Sentry.captureException(e)
        false
    }

    /**
     * Saves and shares [bitmap], telling the user when it could not. A failed share used to do
     * nothing at all: the button stopped spinning and no sheet appeared.
     */
    suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
        val shared = saveBitmapForSharing(context, bitmap)?.let { shareImage(context, it) } == true
        if (!shared) {
            android.widget.Toast.makeText(
                context, com.tanush.bouncer.R.string.share_failed, android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * Composable wrapper for easy share card generation
 */
@Composable
fun ShareCardButton(
    match: CricketMatch,
    winProbability: Float,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)
    
    Button(
        onClick = {
            isGenerating = true
            scope.launch {
                val data = ShareCardData(
                    team1Name = team1?.acronym?.ifEmpty { team1.name.take(3) } ?: "Team 1",
                    team2Name = team2?.acronym?.ifEmpty { team2.name.take(3) } ?: "Team 2",
                    team1Score = team1?.score ?: "-",
                    team2Score = team2?.score,
                    matchStatus = match.status,
                    matchType = match.matchType,
                    venue = match.venue,
                    winProbability = winProbability,
                    isLive = match.isLive
                )
                
                val bitmap = ShareCardGenerator.generateShareBitmap(data)
                ShareCardGenerator.shareBitmap(context, bitmap)
                isGenerating = false
            }
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.goldPrimary
        ),
        enabled = !isGenerating
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.backgroundBase,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.common_share),
                tint = colors.backgroundBase,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isGenerating) "GENERATING..." else "SHARE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.backgroundBase
        )
    }
}
