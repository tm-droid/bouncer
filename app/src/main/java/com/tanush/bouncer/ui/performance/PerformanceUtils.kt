package com.tanush.bouncer.ui.performance

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 1. rememberStableCoroutineScope - cached coroutine scope
 * Returns a stable coroutine scope that survives recomposition
 */
@Composable
fun rememberStableCoroutineScope(): CoroutineScope {
    return rememberCoroutineScope()
}

/**
 * 2. Modifier.debouncedClickable - prevents rapid multi-clicks
 * Debounces click events to prevent accidental double-taps
 */
fun Modifier.debouncedClickable(
    debounceInterval: Long = 300L,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    var lastClickTime = remember { 0L }
    
    clickable(
        interactionSource = interactionSource,
        indication = null
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceInterval) {
            lastClickTime = currentTime
            onClick()
        }
    }
}

/**
 * 3. ImagePreloader - preloads Coil images
 * Preloads images in the background with low priority
 */
object ImagePreloader {
    fun preload(context: Context, urls: List<String>) {
        val imageLoader = ImageLoader.Builder(context).build()
        
        urls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .build()
            
            imageLoader.enqueue(request)
        }
    }
}

/**
 * 4. BatchApiHelper - batches multiple API calls
 * Executes multiple suspending functions concurrently with controlled parallelism
 */
object BatchApiHelper {
    suspend fun <T> batchLoad(
        calls: List<suspend () -> T>,
        maxConcurrency: Int = 3
    ): List<Result<T>> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency)
        
        calls.map { call ->
            async {
                semaphore.withPermit {
                    try {
                        Result.success(call())
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
    }
}

/**
 * 5. ScreenPreloader - preloads data for screens
 * Fire-and-forget coroutine that warms caches for upcoming screens
 */
object ScreenPreloader {
    private var preloadScope: CoroutineScope? = null
    
    fun initialize(scope: CoroutineScope) {
        preloadScope = scope
    }
    
    fun preloadMatchDetail(matchId: String, repository: Any?) {
        preloadScope?.launch {
            // Fire-and-forget: warm cache for match detail data
            try {
                // Placeholder for actual repository call
                // repository?.fetchMatchDetails(matchId)
                delay(100) // Simulate preload
            } catch (e: Exception) {
                // Silently fail - this is just optimization
            }
        }
    }
    
    fun preloadNews() {
        preloadScope?.launch {
            try {
                // Placeholder for actual news fetch
                // newsRepository.fetchLatestNews()
                delay(100) // Simulate preload
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
}

/**
 * 6. Modifier.skipRecomposition reminder
 * Note: Use @Stable annotation on data classes to skip unnecessary recomposition
 * 
 * Example:
 * @Stable
 * data class MyStableData(val value: String)
 */

/**
 * 7. LazyListOptimizations - helpers for LazyList performance
 */
object LazyListOptimizations {
    /**
     * Determines if the lazy list is scrolling upward
     * Useful for hiding/showing headers based on scroll direction
     */
    fun LazyListState.isScrollingUp(): Boolean {
        return firstVisibleItemScrollOffset < (layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0)
    }
}
