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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import io.sentry.Sentry

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
    val winProbability: Float = 0.5f,
    val keyPlayer: String? = null,
    val keyPlayerStat: String? = null,
    val isLive: Boolean = false
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
    val infiniteTransition = rememberInfiniteTransition(label = "shareCard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = colors.goldPrimary.copy(alpha = 0.3f)
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
                        colors.goldPrimary.copy(alpha = 0.5f),
                        colors.goldLight.copy(alpha = 0.2f),
                        colors.goldPrimary.copy(alpha = 0.5f)
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
                                        colors.goldPrimary.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                            .border(2.dp, colors.goldPrimary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.team1Name.take(3).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.goldPrimary
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
                        color = colors.goldPrimary
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
            WinProbabilityShareBar(
                probability = data.winProbability,
                team1Name = data.team1Name,
                team2Name = data.team2Name
            )
            
            // Key Player Highlight
            data.keyPlayer?.let { player ->
                Spacer(modifier = Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.1f),
                                    colors.goldPrimary.copy(alpha = 0.2f),
                                    colors.goldPrimary.copy(alpha = 0.1f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, colors.goldPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "KEY PERFORMER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.goldLight,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = player,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.goldPrimary
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
                    color = colors.goldPrimary,
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
                    contentDescription = "Share",
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
                color = if (probability > 0.5f) colors.statusSuccess else colors.goldPrimary
            )
            Text(
                text = "WIN PROBABILITY",
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
                                colors = listOf(colors.goldDark, colors.goldPrimary)
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
        format: ShareCardFormat = ShareCardFormat.INSTAGRAM_STORY
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Background gradient
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0xFF050505.toInt(), 0xFF141414.toInt(), 0xFF050505.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        
        // Gold border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0xFFFFD700.toInt()
        val borderRadius = 40f
        canvas.drawRoundRect(
            RectF(8f, 8f, width - 8f, height - 8f),
            borderRadius, borderRadius, paint
        )
        paint.style = Paint.Style.FILL
        
        // Content positioning
        val centerX = width / 2f
        var currentY = 100f
        
        // Match Type
        paint.color = 0xFF64748B.toInt()
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(data.matchType.uppercase(), centerX, currentY, paint)
        
        currentY += 100f
        
        // Live Indicator
        if (data.isLive) {
            paint.color = 0xFFEF4444.toInt()
            canvas.drawRoundRect(
                RectF(centerX - 60, currentY - 30, centerX + 60, currentY + 10),
                20f, 20f, paint
            )
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = 28f
            canvas.drawText("LIVE", centerX, currentY, paint)
            currentY += 60f
        }
        
        currentY += 80f
        
        // Team 1
        paint.color = 0xFFFFD700.toInt()
        paint.textSize = 48f
        canvas.drawText(data.team1Name.uppercase(), centerX, currentY, paint)
        
        currentY += 80f
        
        // Team 1 Score
        paint.textSize = 96f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(data.team1Score, centerX, currentY, paint)
        
        currentY += 100f
        
        // VS
        paint.color = 0xFF64748B.toInt()
        paint.textSize = 48f
        canvas.drawText("VS", centerX, currentY, paint)
        
        currentY += 100f
        
        // Team 2
        paint.color = 0xFF94A3B8.toInt()
        paint.textSize = 48f
        canvas.drawText(data.team2Name.uppercase(), centerX, currentY, paint)
        
        currentY += 80f
        
        // Team 2 Score
        paint.textSize = 72f
        canvas.drawText(data.team2Score ?: "-", centerX, currentY, paint)
        
        currentY += 120f
        
        // Status
        paint.color = 0xFF94A3B8.toInt()
        paint.textSize = 40f
        canvas.drawText(data.matchStatus, centerX, currentY, paint)
        
        currentY += 120f
        
        // Win Probability Bar
        val barWidth = width * 0.8f
        val barHeight = 24f
        val barLeft = (width - barWidth) / 2
        
        // Background
        paint.color = 0xFF0F0F0F.toInt()
        canvas.drawRoundRect(
            RectF(barLeft, currentY, barLeft + barWidth, currentY + barHeight),
            barHeight / 2, barHeight / 2, paint
        )
        
        // Team 1 Progress
        val team1Width = barWidth * data.winProbability
        paint.shader = LinearGradient(
            barLeft, 0f, barLeft + team1Width, 0f,
            intArrayOf(0xFFB39800.toInt(), 0xFFFFD700.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(barLeft, currentY, barLeft + team1Width, currentY + barHeight),
            barHeight / 2, barHeight / 2, paint
        )
        paint.shader = null
        
        currentY += barHeight + 40f
        
        // Probability Labels
        paint.textSize = 32f
        paint.textAlign = Paint.Align.LEFT
        paint.color = 0xFF22C55E.toInt()
        canvas.drawText("${(data.winProbability * 100).toInt()}%", barLeft, currentY, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        paint.color = 0xFFEF4444.toInt()
        canvas.drawText("${((1 - data.winProbability) * 100).toInt()}%", barLeft + barWidth, currentY, paint)
        
        // Bouncer Branding at bottom
        paint.color = 0xFFFFD700.toInt()
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BOUNCER", centerX, height - 80f, paint)
        
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
        try {
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
                    "${context.packageName}.provider",
                    file
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            null
        }
    }
    
    /**
     * Launches share intent with the generated image
     */
    fun shareImage(context: Context, imageUri: Uri, text: String? = null) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        
            context.startActivity(Intent.createChooser(shareIntent, "Share Match Update"))
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
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
                val uri = ShareCardGenerator.saveBitmapForSharing(context, bitmap)
                
                uri?.let { ShareCardGenerator.shareImage(context, it) }
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
                contentDescription = "Share",
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
