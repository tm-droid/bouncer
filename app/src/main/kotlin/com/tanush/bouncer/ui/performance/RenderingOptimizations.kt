package com.tanush.bouncer.ui.performance

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.math.abs

// ==========================================
// BOUNCER - Rendering Optimizations
// High-performance rendering utilities for Compose UI
// ==========================================

/**
 * ImageCacheManager - LruCache-based caching for vector and bitmap images
 * 
 * Provides efficient memory management for frequently used images,
 * reducing redundant decoding and memory allocations.
 */
object ImageCacheManager {
    
    // Calculate cache size as 1/8th of available memory
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    
    // Bitmap cache with automatic size-based eviction
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && !oldValue.isRecycled) {
                // Mark for recycling, don't recycle immediately to avoid crashes
                oldValue.prepareToDraw()
            }
        }
    }
    
    // Vector drawable cache (stores as ImageBitmap for Compose)
    private val vectorCache = object : LruCache<String, ImageBitmap>(cacheSize / 2) {
        override fun sizeOf(key: String, bitmap: ImageBitmap): Int {
            return bitmap.width * bitmap.height * 4 / 1024
        }
    }
    
    private val mutex = Mutex()
    
    /**
     * Get or create a cached bitmap with the given key
     */
    suspend fun getBitmap(
        key: String,
        width: Int,
        height: Int,
        create: () -> Bitmap
    ): Bitmap {
        val cacheKey = "$key-${width}x$height"
        
        return mutex.withLock {
            bitmapCache.get(cacheKey) ?: run {
                val bitmap = create()
                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
        }
    }
    
    /**
     * Get or create a cached ImageBitmap for vector drawables
     */
    suspend fun getVectorBitmap(
        key: String,
        width: Int,
        height: Int,
        create: () -> ImageBitmap
    ): ImageBitmap {
        val cacheKey = "$key-${width}x$height"
        
        return mutex.withLock {
            vectorCache.get(cacheKey) ?: run {
                val bitmap = create()
                vectorCache.put(cacheKey, bitmap)
                bitmap
            }
        }
    }
    
    /**
     * Cache an existing bitmap with a specific key
     */
    fun cacheBitmap(key: String, bitmap: Bitmap) {
        bitmapCache.put(key, bitmap)
    }
    
    /**
     * Cache a vector as ImageBitmap with a specific key
     */
    fun cacheVector(key: String, imageBitmap: ImageBitmap) {
        vectorCache.put(key, imageBitmap)
    }
    
    /**
     * Remove a specific cached item
     */
    fun evict(key: String) {
        bitmapCache.remove(key)
        vectorCache.remove(key)
    }
    
    /**
     * Clear all cached images
     */
    fun clearAll() {
        bitmapCache.evictAll()
        vectorCache.evictAll()
    }
    
    /**
     * Get current cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            bitmapCacheSize = bitmapCache.size(),
            bitmapCacheHits = bitmapCache.hitCount(),
            bitmapCacheMisses = bitmapCache.missCount(),
            vectorCacheSize = vectorCache.size(),
            vectorCacheHits = vectorCache.hitCount(),
            vectorCacheMisses = vectorCache.missCount()
        )
    }
    
    data class CacheStats(
        val bitmapCacheSize: Int,
        val bitmapCacheHits: Int,
        val bitmapCacheMisses: Int,
        val vectorCacheSize: Int,
        val vectorCacheHits: Int,
        val vectorCacheMisses: Int
    ) {
        val bitmapHitRate: Float
            get() = if (bitmapCacheHits + bitmapCacheMisses > 0) {
                bitmapCacheHits.toFloat() / (bitmapCacheHits + bitmapCacheMisses)
            } else 0f
            
        val vectorHitRate: Float
            get() = if (vectorCacheHits + vectorCacheMisses > 0) {
                vectorCacheHits.toFloat() / (vectorCacheHits + vectorCacheMisses)
            } else 0f
    }
}

/**
 * Hardware layer modifier for animated elements
 * 
 * Uses graphics layers to optimize rendering of animated content,
 * offloading compositing to the GPU.
 */
@Composable
fun Modifier.hardwareLayer(
    enabled: Boolean = true,
    alpha: Float = 1f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    translationX: Float = 0f,
    translationY: Float = 0f,
    rotationX: Float = 0f,
    rotationY: Float = 0f,
    rotationZ: Float = 0f,
    cameraDistance: Float = 8f
): Modifier {
    return if (enabled) {
        this.graphicsLayer(
            alpha = alpha,
            scaleX = scaleX,
            scaleY = scaleY,
            translationX = translationX,
            translationY = translationY,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            cameraDistance = cameraDistance,
            compositingStrategy = CompositingStrategy.Offscreen
        )
    } else {
        this
    }
}

/**
 * Hardware layer modifier for animatable values
 */
@Composable
fun Modifier.animatedHardwareLayer(
    alphaAnimatable: Animatable<Float, AnimationVector1D>? = null,
    scaleAnimatable: Animatable<Float, AnimationVector1D>? = null,
    rotationAnimatable: Animatable<Float, AnimationVector1D>? = null
): Modifier {
    val alpha = alphaAnimatable?.value ?: 1f
    val scale = scaleAnimatable?.value ?: 1f
    val rotation = rotationAnimatable?.value ?: 0f
    
    return this.graphicsLayer(
        alpha = alpha,
        scaleX = scale,
        scaleY = scale,
        rotationZ = rotation,
        compositingStrategy = CompositingStrategy.Offscreen
    )
}

/**
 * RenderEffectCache - Caches blur and other render effects
 * 
 * Blur operations are expensive; this cache stores pre-rendered
 * blur results for reuse.
 */
object RenderEffectCache {
    
    private val maxCacheSize = 20
    
    private val blurCache = object : LruCache<BlurCacheKey, ImageBitmap>(maxCacheSize) {
        override fun sizeOf(key: BlurCacheKey, bitmap: ImageBitmap): Int {
            return 1 // Count-based eviction
        }
    }
    
    private val mutex = Mutex()
    
    data class BlurCacheKey(
        val contentHash: String,
        val blurRadius: Float,
        val width: Int,
        val height: Int
    )
    
    /**
     * Get or create a cached blur effect result
     */
    suspend fun getCachedBlur(
        contentHash: String,
        blurRadius: Float,
        width: Int,
        height: Int,
        render: () -> ImageBitmap
    ): ImageBitmap {
        val key = BlurCacheKey(contentHash, blurRadius, width, height)
        
        return mutex.withLock {
            blurCache.get(key) ?: run {
                val blurred = render()
                blurCache.put(key, blurred)
                blurred
            }
        }
    }
    
    /**
     * Check if a blur result is cached
     */
    fun isCached(contentHash: String, blurRadius: Float, width: Int, height: Int): Boolean {
        val key = BlurCacheKey(contentHash, blurRadius, width, height)
        return blurCache.get(key) != null
    }
    
    /**
     * Clear all cached blur effects
     */
    fun clearAll() {
        blurCache.evictAll()
    }
    
    /**
     * Generate a hash for content identification
     */
    fun hashContent(vararg elements: Any?): String {
        val combined = elements.joinToString("|") { it?.toString() ?: "null" }
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(combined.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * DrawWithCache optimization wrapper
 * 
 * Provides a composable wrapper that uses drawWithCache for
 * efficient rendering of complex graphics.
 */
fun Modifier.optimizedDraw(
    onBuildCache: () -> CachedDrawResult
): Modifier = this.drawWithCache {
    val cached = onBuildCache()
    onDrawBehind {
        cached.draw(this)
    }
}

/**
 * Result of a cached draw operation
 */
data class CachedDrawResult(
    private val drawBlock: DrawScope.() -> Unit
) {
    fun draw(scope: DrawScope) {
        drawBlock(scope)
    }
}

/**
 * Cached gradient brush builder
 */
@Composable
fun rememberCachedBrush(
    colors: List<Color>,
    startOffset: Offset = Offset.Zero,
    endOffset: Offset = Offset(1f, 1f)
): Brush {
    return remember(colors, startOffset, endOffset) {
        Brush.linearGradient(
            colors = colors,
            start = startOffset,
            end = endOffset
        )
    }
}

/**
 * Cached radial gradient builder
 */
@Composable
fun rememberRadialBrush(
    colors: List<Color>,
    center: Offset = Offset.Unspecified,
    radius: Float = Float.POSITIVE_INFINITY
): Brush {
    return remember(colors, center, radius) {
        Brush.radialGradient(
            colors = colors,
            center = center,
            radius = radius
        )
    }
}

/**
 * StableKey generator for list items
 * 
 * Generates stable, consistent keys for list items to optimize
 * LazyColumn/LazyRow recomposition.
 */
object StableKeyGenerator {
    
    /**
     * Generate a stable key from multiple properties
     */
    fun generate(vararg components: Any?): Any {
        return StableKey(components.toList())
    }
    
    /**
     * Generate a stable key with a prefix
     */
    fun generateWithPrefix(prefix: String, vararg components: Any?): Any {
        return StableKey(listOf(prefix) + components.toList())
    }
    
    /**
     * Generate a hash-based key for complex objects
     */
    fun generateHash(obj: Any?): Int {
        return obj?.hashCode() ?: 0
    }
    
    @Immutable
    data class StableKey(val components: List<Any?>)
}

/**
 * Composable function to generate stable keys
 */
@Composable
inline fun <T> rememberStableKey(
    item: T,
    crossinline keyExtractor: (T) -> Any
): Any {
    return remember(item) {
        keyExtractor(item)
    }
}

/**
 * rememberStableState - Stable state wrapper for composition stability
 * 
 * Wraps state values to prevent unnecessary recomposition when
 * the actual value hasn't changed.
 */
@Composable
fun <T> rememberStableState(
    initialValue: T,
    areEquivalent: (T, T) -> Boolean = { a, b -> a == b }
): StableState<T> {
    return remember { StableState(initialValue, areEquivalent) }
}

/**
 * Stable state container that only notifies on actual value changes
 */
@Stable
class StableState<T>(
    initialValue: T,
    private val areEquivalent: (T, T) -> Boolean
) {
    private var _value by mutableStateOf(initialValue)
    
    var value: T
        get() = _value
        set(newValue) {
            if (!areEquivalent(_value, newValue)) {
                _value = newValue
            }
        }
    
    /**
     * Update value with a transformation function
     */
    fun update(transform: (T) -> T) {
        value = transform(_value)
    }
    
    /**
     * Force update regardless of equivalence
     */
    fun forceUpdate(newValue: T) {
        _value = newValue
    }
}

/**
 * Stable list state for efficient list rendering
 */
@Composable
fun <T> rememberStableList(
    items: List<T>,
    keyExtractor: (T) -> Any = { it as Any }
): StableList<T> {
    return remember(items) {
        StableList(items, keyExtractor)
    }
}

@Stable
class StableList<T>(
    private val items: List<T>,
    private val keyExtractor: (T) -> Any
) : List<T> by items {
    
    fun keyOf(item: T): Any = keyExtractor(item)
    
    fun keyAt(index: Int): Any = keyExtractor(items[index])
    
    fun itemsWithKeys(): List<Pair<Any, T>> {
        return items.map { keyExtractor(it) to it }
    }
}

/**
 * Deferred image loading state
 */
sealed class ImageLoadState {
    object Loading : ImageLoadState()
    data class Success(val bitmap: ImageBitmap) : ImageLoadState()
    data class Error(val exception: Throwable) : ImageLoadState()
}

/**
 * Remember image with caching support
 */
@Composable
fun rememberCachedImage(
    key: String,
    width: Int,
    height: Int,
    load: suspend () -> ImageBitmap
): State<ImageLoadState> {
    val state = remember { mutableStateOf<ImageLoadState>(ImageLoadState.Loading) }
    
    LaunchedEffect(key, width, height) {
        state.value = ImageLoadState.Loading
        try {
            val bitmap = ImageCacheManager.getVectorBitmap(key, width, height) {
                kotlinx.coroutines.runBlocking { load() }
            }
            state.value = ImageLoadState.Success(bitmap)
        } catch (e: Exception) {
            state.value = ImageLoadState.Error(e)
        }
    }
    
    return state
}

/**
 * Performance monitoring for rendering
 */
object RenderingPerformanceMonitor {
    
    private var frameCount = 0
    private var droppedFrames = 0
    private var lastFrameTime = System.nanoTime()
    private val frameTimeThreshold = 16_666_666L // 60 FPS = ~16.67ms per frame
    
    fun onFrameRendered() {
        frameCount++
        val currentTime = System.nanoTime()
        val frameDuration = currentTime - lastFrameTime
        
        if (frameDuration > frameTimeThreshold) {
            droppedFrames++
        }
        
        lastFrameTime = currentTime
    }
    
    fun getStats(): RenderStats {
        return RenderStats(
            totalFrames = frameCount,
            droppedFrames = droppedFrames,
            dropRate = if (frameCount > 0) droppedFrames.toFloat() / frameCount else 0f
        )
    }
    
    fun reset() {
        frameCount = 0
        droppedFrames = 0
        lastFrameTime = System.nanoTime()
    }
    
    data class RenderStats(
        val totalFrames: Int,
        val droppedFrames: Int,
        val dropRate: Float
    )
}
