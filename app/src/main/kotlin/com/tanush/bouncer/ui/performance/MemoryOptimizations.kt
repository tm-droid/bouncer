package com.tanush.bouncer.ui.performance

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ==========================================
// BOUNCER - Memory Optimizations
// Efficient memory management utilities
// ==========================================

/**
 * BitmapPool - Memory-efficient bitmap reuse pool
 * 
 * Reduces memory allocation overhead by recycling bitmap objects,
 * particularly useful for image-heavy list views.
 */
object BitmapPool {
    
    // Pool organized by size for efficient matching
    private val pool = ConcurrentHashMap<BitmapKey, MutableList<SoftReference<Bitmap>>>()
    private val maxPoolSize = 20
    private val mutex = Mutex()
    
    private val allocCount = AtomicInteger(0)
    private val reuseCount = AtomicInteger(0)
    private val recycleCount = AtomicInteger(0)
    
    data class BitmapKey(
        val width: Int,
        val height: Int,
        val config: Bitmap.Config
    )
    
    /**
     * Get a bitmap from the pool or create a new one
     */
    suspend fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val key = BitmapKey(width, height, config)
        
        return mutex.withLock {
            val candidates = pool[key]
            
            // Try to find a reusable bitmap
            candidates?.let { list ->
                val iterator = list.iterator()
                while (iterator.hasNext()) {
                    val ref = iterator.next()
                    val bitmap = ref.get()
                    if (bitmap != null && !bitmap.isRecycled) {
                        iterator.remove()
                        reuseCount.incrementAndGet()
                        // Clear the bitmap for reuse
                        bitmap.eraseColor(0)
                        return@withLock bitmap
                    } else {
                        iterator.remove() // Remove null/recycled references
                    }
                }
            }
            
            // No reusable bitmap found, create new one
            allocCount.incrementAndGet()
            Bitmap.createBitmap(width, height, config)
        }
    }
    
    /**
     * Return a bitmap to the pool for reuse
     */
    suspend fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        
        val key = BitmapKey(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        mutex.withLock {
            val list = pool.getOrPut(key) { mutableListOf() }
            
            // Limit pool size per key
            if (list.size < maxPoolSize / pool.size.coerceAtLeast(1)) {
                list.add(SoftReference(bitmap))
                recycleCount.incrementAndGet()
            } else {
                // Pool is full, let GC handle it
                bitmap.recycle()
            }
        }
    }
    
    /**
     * Clear the entire pool
     */
    suspend fun clear() {
        mutex.withLock {
            pool.values.forEach { list ->
                list.forEach { ref ->
                    ref.get()?.recycle()
                }
            }
            pool.clear()
        }
    }
    
    /**
     * Trim pool to reduce memory usage
     */
    suspend fun trim(fraction: Float = 0.5f) {
        mutex.withLock {
            pool.values.forEach { list ->
                val removeCount = (list.size * fraction).toInt()
                repeat(removeCount) {
                    if (list.isNotEmpty()) {
                        val ref = list.removeAt(0)
                        ref.get()?.recycle()
                    }
                }
            }
        }
    }
    
    /**
     * Get pool statistics
     */
    fun getStats(): PoolStats {
        val totalPooled = pool.values.sumOf { list ->
            list.count { it.get() != null && !it.get()!!.isRecycled }
        }
        
        return PoolStats(
            allocations = allocCount.get(),
            reuses = reuseCount.get(),
            recycles = recycleCount.get(),
            currentPoolSize = totalPooled,
            reuseRate = if (allocCount.get() > 0) {
                reuseCount.get().toFloat() / (allocCount.get() + reuseCount.get())
            } else 0f
        )
    }
    
    data class PoolStats(
        val allocations: Int,
        val reuses: Int,
        val recycles: Int,
        val currentPoolSize: Int,
        val reuseRate: Float
    )
}

/**
 * DisposableEffectManager - Centralized cleanup management
 * 
 * Tracks and manages disposable resources for proper cleanup
 * when composables leave composition.
 */
class DisposableEffectManager {
    
    private val disposables = ConcurrentHashMap<String, DisposableResource>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    interface DisposableResource {
        fun dispose()
        val isDisposed: Boolean
    }
    
    /**
     * Register a disposable resource
     */
    fun register(key: String, resource: DisposableResource) {
        // Dispose existing resource with same key
        disposables[key]?.dispose()
        disposables[key] = resource
    }
    
    /**
     * Register with a custom dispose action
     */
    fun register(key: String, onDispose: () -> Unit): DisposableResource {
        val resource = object : DisposableResource {
            private var disposed = false
            
            override fun dispose() {
                if (!disposed) {
                    disposed = true
                    onDispose()
                }
            }
            
            override val isDisposed: Boolean get() = disposed
        }
        
        register(key, resource)
        return resource
    }
    
    /**
     * Dispose a specific resource
     */
    fun dispose(key: String) {
        disposables.remove(key)?.dispose()
    }
    
    /**
     * Dispose all resources
     */
    fun disposeAll() {
        disposables.values.forEach { it.dispose() }
        disposables.clear()
    }
    
    /**
     * Get count of active disposables
     */
    fun activeCount(): Int {
        return disposables.count { !it.value.isDisposed }
    }
    
    /**
     * Cleanup disposed resources
     */
    fun cleanup() {
        disposables.entries.removeIf { it.value.isDisposed }
    }
    
    /**
     * Shutdown manager
     */
    fun shutdown() {
        disposeAll()
        scope.cancel()
    }
}

/**
 * Composable helper for DisposableEffectManager
 */
@Composable
fun rememberDisposableEffectManager(): DisposableEffectManager {
    val manager = remember { DisposableEffectManager() }
    
    DisposableEffect(Unit) {
        onDispose {
            manager.disposeAll()
        }
    }
    
    return manager
}

/**
 * FlowDebouncer - Throttles rapid updates for performance
 * 
 * Prevents excessive recomposition from rapid data updates
 * by debouncing flow emissions.
 */
class FlowDebouncer(
    private val defaultDebounceMs: Long = 100L
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Create a debounced version of a flow
     */
    @OptIn(FlowPreview::class)
    fun <T> debounce(
        source: Flow<T>,
        debounceMs: Long = defaultDebounceMs
    ): Flow<T> {
        return source.debounce(debounceMs)
    }
    
    /**
     * Create a throttled version of a flow (first emission, then wait)
     */
    fun <T> throttleFirst(
        source: Flow<T>,
        windowMs: Long = defaultDebounceMs
    ): Flow<T> = flow {
        var lastEmissionTime = 0L
        source.collect { value ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmissionTime >= windowMs) {
                lastEmissionTime = currentTime
                emit(value)
            }
        }
    }
    
    /**
     * Create a throttled version that emits latest value in window
     */
    fun <T> throttleLatest(
        source: Flow<T>,
        windowMs: Long = defaultDebounceMs
    ): Flow<T> = channelFlow {
        var latestValue: T? = null
        var hasValue = false
        val mutex = Mutex()
        
        launch {
            source.collect { value ->
                mutex.withLock {
                    latestValue = value
                    hasValue = true
                }
            }
        }
        
        while (true) {
            delay(windowMs)
            mutex.withLock {
                if (hasValue) {
                    @Suppress("UNCHECKED_CAST")
                    send(latestValue as T)
                    hasValue = false
                }
            }
        }
    }
    
    /**
     * Batch emissions into lists
     */
    fun <T> batch(
        source: Flow<T>,
        batchSize: Int = 10,
        timeoutMs: Long = defaultDebounceMs
    ): Flow<List<T>> = flow {
        val batch = mutableListOf<T>()
        var lastEmitTime = System.currentTimeMillis()
        
        source.collect { value ->
            batch.add(value)
            val currentTime = System.currentTimeMillis()
            
            if (batch.size >= batchSize || currentTime - lastEmitTime >= timeoutMs) {
                emit(batch.toList())
                batch.clear()
                lastEmitTime = currentTime
            }
        }
        
        // Emit remaining items
        if (batch.isNotEmpty()) {
            emit(batch.toList())
        }
    }
    
    fun cancel() {
        scope.cancel()
    }
}

/**
 * Composable helper for debounced state
 */
@OptIn(FlowPreview::class)
@Composable
fun <T> rememberDebouncedState(
    value: T,
    debounceMs: Long = 100L
): State<T> {
    val state = remember { mutableStateOf(value) }
    
    LaunchedEffect(value) {
        delay(debounceMs)
        state.value = value
    }
    
    return state
}

/**
 * LazyComposition - Deferred initialization for heavy composables
 * 
 * Delays composition of expensive content until it's actually needed,
 * improving initial render performance.
 */
@Composable
fun LazyComposition(
    shouldCompose: Boolean,
    delayMs: Long = 0L,
    placeholder: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    var isReady by remember { mutableStateOf(delayMs == 0L) }
    
    LaunchedEffect(shouldCompose) {
        if (shouldCompose && delayMs > 0L) {
            delay(delayMs)
            isReady = true
        } else {
            isReady = shouldCompose
        }
    }
    
    if (isReady && shouldCompose) {
        content()
    } else if (shouldCompose) {
        placeholder()
    }
}

/**
 * Deferred composable that initializes after a delay
 */
@Composable
fun DeferredComposition(
    delayMs: Long = 100L,
    placeholder: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    LazyComposition(
        shouldCompose = true,
        delayMs = delayMs,
        placeholder = placeholder,
        content = content
    )
}

/**
 * Remember with lazy initialization
 */
@Composable
inline fun <T> rememberLazy(
    crossinline initializer: () -> T
): Lazy<T> {
    return remember { lazy(LazyThreadSafetyMode.NONE) { initializer() } }
}

/**
 * WeakReferenceCache - Cache using weak references
 * 
 * Stores temporary data that can be garbage collected
 * when memory is needed.
 */
class WeakReferenceCache<K, V> {
    
    private val cache = ConcurrentHashMap<K, WeakReference<V>>()
    private val creationTime = ConcurrentHashMap<K, Long>()
    
    /**
     * Get value from cache
     */
    fun get(key: K): V? {
        val ref = cache[key] ?: return null
        val value = ref.get()
        
        if (value == null) {
            // Reference was collected, clean up
            cache.remove(key)
            creationTime.remove(key)
        }
        
        return value
    }
    
    /**
     * Put value into cache
     */
    fun put(key: K, value: V) {
        cache[key] = WeakReference(value)
        creationTime[key] = System.currentTimeMillis()
    }
    
    /**
     * Get or create value
     */
    fun getOrPut(key: K, defaultValue: () -> V): V {
        val existing = get(key)
        if (existing != null) return existing
        
        val newValue = defaultValue()
        put(key, newValue)
        return newValue
    }
    
    /**
     * Remove specific key
     */
    fun remove(key: K) {
        cache.remove(key)
        creationTime.remove(key)
    }
    
    /**
     * Clear all entries
     */
    fun clear() {
        cache.clear()
        creationTime.clear()
    }
    
    /**
     * Get cache size (may include collected references)
     */
    fun size(): Int = cache.size
    
    /**
     * Get count of valid (non-collected) entries
     */
    fun validCount(): Int {
        return cache.count { it.value.get() != null }
    }
    
    /**
     * Cleanup collected references
     */
    fun cleanup() {
        val keysToRemove = cache.entries
            .filter { it.value.get() == null }
            .map { it.key }
        
        keysToRemove.forEach { key ->
            cache.remove(key)
            creationTime.remove(key)
        }
    }
    
    /**
     * Remove entries older than specified age
     */
    fun removeOlderThan(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        val keysToRemove = creationTime.entries
            .filter { now - it.value > maxAgeMs }
            .map { it.key }
        
        keysToRemove.forEach { key ->
            cache.remove(key)
            creationTime.remove(key)
        }
    }
}

/**
 * SoftReferenceCache - Cache using soft references
 * 
 * Similar to WeakReferenceCache but retains values longer,
 * only releasing them when memory is critically low.
 */
class SoftReferenceCache<K, V> {
    
    private val cache = ConcurrentHashMap<K, SoftReference<V>>()
    
    fun get(key: K): V? {
        val ref = cache[key] ?: return null
        val value = ref.get()
        
        if (value == null) {
            cache.remove(key)
        }
        
        return value
    }
    
    fun put(key: K, value: V) {
        cache[key] = SoftReference(value)
    }
    
    fun getOrPut(key: K, defaultValue: () -> V): V {
        val existing = get(key)
        if (existing != null) return existing
        
        val newValue = defaultValue()
        put(key, newValue)
        return newValue
    }
    
    fun remove(key: K) {
        cache.remove(key)
    }
    
    fun clear() {
        cache.clear()
    }
    
    fun size(): Int = cache.size
    
    fun validCount(): Int = cache.count { it.value.get() != null }
    
    fun cleanup() {
        val keysToRemove = cache.entries
            .filter { it.value.get() == null }
            .map { it.key }
        keysToRemove.forEach { cache.remove(it) }
    }
}

/**
 * MemoryWarningHandler - Responds to low memory situations
 * 
 * Implements ComponentCallbacks2 to receive memory pressure
 * notifications and trigger cleanup operations.
 */
class MemoryWarningHandler(
    private val context: Context
) : ComponentCallbacks2 {
    
    private val listeners = mutableListOf<MemoryWarningListener>()
    private var lastMemoryLevel = 0
    private var isRegistered = false
    
    interface MemoryWarningListener {
        fun onLowMemory()
        fun onTrimMemory(level: Int)
    }
    
    /**
     * Register this handler to receive memory callbacks
     */
    fun register() {
        if (!isRegistered) {
            context.registerComponentCallbacks(this)
            isRegistered = true
        }
    }
    
    /**
     * Unregister this handler
     */
    fun unregister() {
        if (isRegistered) {
            context.unregisterComponentCallbacks(this)
            isRegistered = false
        }
    }
    
    /**
     * Add a listener for memory warnings
     */
    fun addListener(listener: MemoryWarningListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove a listener
     */
    fun removeListener(listener: MemoryWarningListener) {
        listeners.remove(listener)
    }
    
    override fun onLowMemory() {
        listeners.forEach { it.onLowMemory() }
        performEmergencyCleanup()
    }
    
    override fun onTrimMemory(level: Int) {
        lastMemoryLevel = level
        listeners.forEach { it.onTrimMemory(level) }
        
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                performAggressiveCleanup()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                performModerateCleanup()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                performLightCleanup()
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // Configuration changes might require cache invalidation
    }
    
    private fun performEmergencyCleanup() {
        CoroutineScope(Dispatchers.Default).launch {
            // Clear all caches
            ImageCacheManager.clearAll()
            RenderEffectCache.clearAll()
            BitmapPool.clear()
        }
    }
    
    private fun performAggressiveCleanup() {
        CoroutineScope(Dispatchers.Default).launch {
            // Clear most caches, keep only essential data
            RenderEffectCache.clearAll()
            BitmapPool.trim(0.75f)
        }
    }
    
    private fun performModerateCleanup() {
        CoroutineScope(Dispatchers.Default).launch {
            // Trim caches by half
            BitmapPool.trim(0.5f)
        }
    }
    
    private fun performLightCleanup() {
        CoroutineScope(Dispatchers.Default).launch {
            // Light cleanup - remove oldest entries
            BitmapPool.trim(0.25f)
        }
    }
    
    /**
     * Get current memory info
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return MemoryInfo(
            totalMemory = runtime.totalMemory(),
            freeMemory = runtime.freeMemory(),
            maxMemory = runtime.maxMemory(),
            usedMemory = runtime.totalMemory() - runtime.freeMemory(),
            availableMemory = memoryInfo.availMem,
            lowMemoryThreshold = memoryInfo.threshold,
            isLowMemory = memoryInfo.lowMemory,
            lastTrimLevel = lastMemoryLevel
        )
    }
    
    data class MemoryInfo(
        val totalMemory: Long,
        val freeMemory: Long,
        val maxMemory: Long,
        val usedMemory: Long,
        val availableMemory: Long,
        val lowMemoryThreshold: Long,
        val isLowMemory: Boolean,
        val lastTrimLevel: Int
    ) {
        val usagePercent: Float
            get() = (usedMemory.toFloat() / maxMemory) * 100
        
        val availablePercent: Float
            get() = (freeMemory.toFloat() / maxMemory) * 100
    }
}

/**
 * Composable helper for memory warning handling
 */
@Composable
fun rememberMemoryWarningHandler(
    context: Context,
    onLowMemory: () -> Unit = {},
    onTrimMemory: (Int) -> Unit = {}
): MemoryWarningHandler {
    val handler = remember { MemoryWarningHandler(context) }
    
    DisposableEffect(handler) {
        val listener = object : MemoryWarningHandler.MemoryWarningListener {
            override fun onLowMemory() = onLowMemory()
            override fun onTrimMemory(level: Int) = onTrimMemory(level)
        }
        
        handler.addListener(listener)
        handler.register()
        
        onDispose {
            handler.removeListener(listener)
            handler.unregister()
        }
    }
    
    return handler
}

/**
 * MemoryOptimizedMutableState - State that can be cleared on low memory
 */
class MemoryOptimizedMutableState<T>(
    initialValue: T,
    private val recreateOnClear: () -> T
) {
    private var _value by mutableStateOf<T?>(initialValue)
    private var isCleared = false
    
    val value: T
        get() {
            if (isCleared || _value == null) {
                _value = recreateOnClear()
                isCleared = false
            }
            return _value!!
        }
    
    fun setValue(newValue: T) {
        _value = newValue
        isCleared = false
    }
    
    fun clear() {
        _value = null
        isCleared = true
    }
}

/**
 * Create a memory-optimized mutable state
 */
@Composable
fun <T> rememberMemoryOptimizedState(
    initialValue: T,
    recreateOnClear: () -> T = { initialValue }
): MemoryOptimizedMutableState<T> {
    return remember { MemoryOptimizedMutableState(initialValue, recreateOnClear) }
}
