package com.tanush.bouncer.ui.performance

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.sentry.Sentry

private inline fun <T> safe(block: () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

object BitmapPool {

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

    suspend fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val key = BitmapKey(width, height, config)

        return mutex.withLock {
            safe {
                val candidates = pool[key]
                candidates?.let { list ->
                    val iterator = list.iterator()
                    while (iterator.hasNext()) {
                        val ref = iterator.next()
                        val bitmap = ref.get()
                        if (bitmap != null && !bitmap.isRecycled) {
                            iterator.remove()
                            reuseCount.incrementAndGet()
                            bitmap.eraseColor(0)
                            return@safe bitmap
                        } else {
                            iterator.remove()
                        }
                    }
                }
                allocCount.incrementAndGet()
                Bitmap.createBitmap(width, height, config)
            }
        }
    }

    suspend fun release(bitmap: Bitmap) = safe {
        if (bitmap.isRecycled) return@safe
        val key = BitmapKey(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        mutex.withLock {
            val list = pool.getOrPut(key) { mutableListOf() }
            if (list.size < maxPoolSize / pool.size.coerceAtLeast(1)) {
                list.add(SoftReference(bitmap))
                recycleCount.incrementAndGet()
            } else {
                bitmap.recycle()
            }
        }
    }

    suspend fun clear() = safe {
        mutex.withLock {
            pool.values.forEach { list -> list.forEach { it.get()?.recycle() } }
            pool.clear()
        }
    }

    suspend fun trim(fraction: Float = 0.5f) = safe {
        mutex.withLock {
            pool.values.forEach { list ->
                val removeCount = (list.size * fraction).toInt()
                repeat(removeCount) {
                    if (list.isNotEmpty()) {
                        list.removeAt(0).get()?.recycle()
                    }
                }
            }
        }
    }

    fun getStats(): PoolStats = safe {
        val totalPooled = pool.values.sumOf { list -> list.count { it.get()?.isRecycled == false } }
        PoolStats(
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

class DisposableEffectManager {

    private val disposables = ConcurrentHashMap<String, DisposableResource>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    interface DisposableResource {
        fun dispose()
        val isDisposed: Boolean
    }

    fun register(key: String, resource: DisposableResource) = safe {
        disposables[key]?.dispose()
        disposables[key] = resource
    }

    fun register(key: String, onDispose: () -> Unit): DisposableResource = safe {
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
        resource
    }

    fun dispose(key: String) = safe { disposables.remove(key)?.dispose(); Unit }

    fun disposeAll() = safe {
        disposables.values.forEach { it.dispose() }
        disposables.clear()
    }

    fun activeCount(): Int = safe { disposables.count { !it.value.isDisposed } }

    fun cleanup() = safe { disposables.entries.removeIf { it.value.isDisposed }; Unit }

    fun shutdown() = safe {
        disposeAll()
        scope.cancel()
    }
}

@Composable
fun rememberDisposableEffectManager(): DisposableEffectManager {
    val manager = remember { DisposableEffectManager() }
    DisposableEffect(Unit) {
        onDispose { manager.disposeAll() }
    }
    return manager
}

class FlowDebouncer(
    private val defaultDebounceMs: Long = 100L
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @OptIn(FlowPreview::class)
    fun <T> debounce(
        source: Flow<T>,
        debounceMs: Long = defaultDebounceMs
    ): Flow<T> = source.debounce(debounceMs)

    fun <T> throttleFirst(
        source: Flow<T>,
        windowMs: Long = defaultDebounceMs
    ): Flow<T> = flow {
        var lastEmissionTime = 0L
        source.collect { value ->
            safe {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEmissionTime >= windowMs) {
                    lastEmissionTime = currentTime
                    emit(value)
                }
            }
        }
    }

    fun <T> throttleLatest(
        source: Flow<T>,
        windowMs: Long = defaultDebounceMs
    ): Flow<T> = channelFlow {
        var latestValue: T? = null
        var hasValue = false
        val mutex = Mutex()

        launch {
            safe {
                source.collect { value ->
                    mutex.withLock {
                        latestValue = value
                        hasValue = true
                    }
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

    fun <T> batch(
        source: Flow<T>,
        batchSize: Int = 10,
        timeoutMs: Long = defaultDebounceMs
    ): Flow<List<T>> = flow {
        val batch = mutableListOf<T>()
        var lastEmitTime = System.currentTimeMillis()

        source.collect { value ->
            safe {
                batch.add(value)
                val currentTime = System.currentTimeMillis()
                if (batch.size >= batchSize || currentTime - lastEmitTime >= timeoutMs) {
                    emit(batch.toList())
                    batch.clear()
                    lastEmitTime = currentTime
                }
            }
        }

        if (batch.isNotEmpty()) {
            emit(batch.toList())
        }
    }

    fun cancel() = safe { scope.cancel() }
}

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

@Composable
inline fun <T> rememberLazy(
    crossinline initializer: () -> T
): Lazy<T> = remember { lazy(LazyThreadSafetyMode.NONE) { initializer() } }

class WeakReferenceCache<K, V> {

    private val cache = ConcurrentHashMap<K, WeakReference<V>>()
    private val creationTime = ConcurrentHashMap<K, Long>()

    fun get(key: K): V? = safe {
        val ref = cache[key] ?: return@safe null
        val value = ref.get()
        if (value == null) {
            cache.remove(key)
            creationTime.remove(key)
        }
        value
    }

    fun put(key: K, value: V) = safe {
        cache[key] = WeakReference(value)
        creationTime[key] = System.currentTimeMillis()
    }

    fun getOrPut(key: K, defaultValue: () -> V): V = safe {
        val existing = get(key)
        if (existing != null) return@safe existing
        val newValue = defaultValue()
        put(key, newValue)
        newValue
    }

    fun remove(key: K) = safe {
        cache.remove(key)
        creationTime.remove(key)
    }

    fun clear() = safe {
        cache.clear()
        creationTime.clear()
    }

    fun size(): Int = cache.size

    fun validCount(): Int = safe { cache.count { it.value.get() != null } }

    fun cleanup() = safe {
        val keysToRemove = cache.entries.filter { it.value.get() == null }.map { it.key }
        keysToRemove.forEach { key ->
            cache.remove(key)
            creationTime.remove(key)
        }
    }

    fun removeOlderThan(maxAgeMs: Long) = safe {
        val now = System.currentTimeMillis()
        val keysToRemove = creationTime.entries.filter { now - it.value > maxAgeMs }.map { it.key }
        keysToRemove.forEach { key ->
            cache.remove(key)
            creationTime.remove(key)
        }
    }
}

class SoftReferenceCache<K, V> {

    private val cache = ConcurrentHashMap<K, SoftReference<V>>()

    fun get(key: K): V? = safe {
        val ref = cache[key] ?: return@safe null
        val value = ref.get()
        if (value == null) cache.remove(key)
        value
    }

    fun put(key: K, value: V) = safe { cache[key] = SoftReference(value); Unit }

    fun getOrPut(key: K, defaultValue: () -> V): V = safe {
        val existing = get(key)
        if (existing != null) return@safe existing
        val newValue = defaultValue()
        put(key, newValue)
        newValue
    }

    fun remove(key: K) = safe { cache.remove(key); Unit }

    fun clear() = safe { cache.clear() }

    fun size(): Int = cache.size

    fun validCount(): Int = cache.count { it.value.get() != null }

    fun cleanup() = safe {
        val keysToRemove = cache.entries.filter { it.value.get() == null }.map { it.key }
        keysToRemove.forEach { cache.remove(it) }
    }
}

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

    fun register() = safe {
        if (!isRegistered) {
            context.registerComponentCallbacks(this)
            isRegistered = true
        }
    }

    fun unregister() = safe {
        if (isRegistered) {
            context.unregisterComponentCallbacks(this)
            isRegistered = false
        }
    }

    fun addListener(listener: MemoryWarningListener) = safe { listeners.add(listener); Unit }

    fun removeListener(listener: MemoryWarningListener) = safe { listeners.remove(listener); Unit }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = safe {
        listeners.forEach { it.onLowMemory() }
        performEmergencyCleanup()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onTrimMemory(level: Int) = safe {
        lastMemoryLevel = level
        listeners.forEach { it.onTrimMemory(level) }

        @Suppress("DEPRECATION")
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> performAggressiveCleanup()
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> performModerateCleanup()
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> performLightCleanup()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    }

    private fun performEmergencyCleanup() = safe {
        CoroutineScope(Dispatchers.Default).launch {
            ImageCacheManager.clearAll()
            RenderEffectCache.clearAll()
            BitmapPool.clear()
        }
        Unit
    }

    private fun performAggressiveCleanup() = safe {
        CoroutineScope(Dispatchers.Default).launch {
            RenderEffectCache.clearAll()
            BitmapPool.trim(0.75f)
        }
        Unit
    }

    private fun performModerateCleanup() = safe {
        CoroutineScope(Dispatchers.Default).launch {
            BitmapPool.trim(0.5f)
        }
        Unit
    }

    private fun performLightCleanup() = safe {
        CoroutineScope(Dispatchers.Default).launch {
            BitmapPool.trim(0.25f)
        }
        Unit
    }

    fun getMemoryInfo(): MemoryInfo = safe {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        MemoryInfo(
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
            safe {
                handler.removeListener(listener)
                handler.unregister()
            }
        }
    }

    return handler
}

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

    fun setValue(newValue: T) = safe {
        _value = newValue
        isCleared = false
    }

    fun clear() = safe {
        _value = null
        isCleared = true
    }
}

@Composable
fun <T> rememberMemoryOptimizedState(
    initialValue: T,
    recreateOnClear: () -> T = { initialValue }
): MemoryOptimizedMutableState<T> = remember { MemoryOptimizedMutableState(initialValue, recreateOnClear) }
