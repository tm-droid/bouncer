package com.tanush.bouncer.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tanush.bouncer.data.local.entities.MatchEntity

class MatchHistoryPagingSource(
    private val dataFetcher: suspend (limit: Int, offset: Int) -> List<MatchEntity>
) : PagingSource<Int, MatchEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MatchEntity> {
        val offset = params.key ?: 0
        return try {
            val data = dataFetcher(params.loadSize, offset)
            LoadResult.Page(
                data = data,
                prevKey = if (offset == 0) null else offset - params.loadSize,
                nextKey = if (data.isEmpty()) null else offset + params.loadSize
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MatchEntity>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(state.config.pageSize)
        }
    }
}
