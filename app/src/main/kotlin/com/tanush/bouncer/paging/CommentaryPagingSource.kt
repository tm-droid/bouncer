package com.tanush.bouncer.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tanush.bouncer.data.local.dao.CommentaryDao
import com.tanush.bouncer.data.local.entities.CommentaryEntity

class CommentaryPagingSource(
    private val commentaryDao: CommentaryDao,
    private val matchId: String
) : PagingSource<Int, CommentaryEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CommentaryEntity> {
        val offset = params.key ?: 0
        return try {
            val data = commentaryDao.getCommentaryPage(matchId, params.loadSize, offset)
            LoadResult.Page(
                data = data,
                prevKey = if (offset == 0) null else offset - params.loadSize,
                nextKey = if (data.isEmpty()) null else offset + params.loadSize
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CommentaryEntity>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(state.config.pageSize)
        }
    }
}
