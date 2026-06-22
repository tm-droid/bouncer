package com.tanush.bouncer.liveactivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.sentry.Sentry
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MatchUpdateDataSource {
    suspend fun fetchMatchState(matchId: String): MatchState?
    suspend fun fetchLatestUpdate(matchId: String, previous: MatchState?): LiveMatchUpdate?
}

class NoopMatchUpdateDataSource : MatchUpdateDataSource {
    override suspend fun fetchMatchState(matchId: String): MatchState? = null

    override suspend fun fetchLatestUpdate(matchId: String, previous: MatchState?): LiveMatchUpdate? = null
}

class LiveActivityRepository(
    private val context: Context,
    private val dataSource: MatchUpdateDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val cacheMutex = Mutex()
    private val stateCache = LinkedHashMap<String, MatchState>()
    private val recentUpdateCache = ArrayDeque<LiveMatchUpdate>()

    suspend fun getCachedState(matchId: String): MatchState? =
        cacheMutex.withLock { stateCache[matchId] }

    suspend fun refreshState(matchId: String): MatchState? {
        if (!isNetworkAvailable()) return getCachedState(matchId)
        val fresh = dataSource.fetchMatchState(matchId) ?: return getCachedState(matchId)
        cacheMutex.withLock {
            stateCache[matchId] = fresh
        }
        return fresh
    }

    fun subscribeToMatch(
        matchId: String,
        intervalMs: Long = 7_000L
    ): Flow<Pair<MatchState, LiveMatchUpdate?>> = flow {
        var errorBackoffMs = 0L
        while (true) {
            try {
                val previous = getCachedState(matchId)
                val state = refreshState(matchId)
                if (state != null) {
                    val update = dataSource.fetchLatestUpdate(matchId, previous)
                    if (update != null) cacheUpdate(update)
                    emit(state to update)
                }
                errorBackoffMs = 0L
                delay(intervalMs)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Sentry.captureException(e)
                errorBackoffMs = minOf(if (errorBackoffMs == 0L) 5_000L else errorBackoffMs * 2, 60_000L)
                delay(errorBackoffMs)
            }
        }
    }.flowOn(ioDispatcher)

    suspend fun cachedRecentUpdates(limit: Int = 20): List<LiveMatchUpdate> =
        cacheMutex.withLock { recentUpdateCache.takeLast(limit) }

    suspend fun cacheUpdate(update: LiveMatchUpdate) {
        cacheMutex.withLock {
            if (recentUpdateCache.size >= 50) {
                recentUpdateCache.removeFirst()
            }
            recentUpdateCache.addLast(update)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
