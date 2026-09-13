package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Immutable
import com.tanush.bouncer.data.remote.CommentaryEntry

/** The blocks a filtered feed renders, with the lazy-list key of every item in them. */
@Immutable
internal data class CommentaryFeed(
    val blocks: List<CommentaryBlock>,
    val keys: List<CommentaryBlockKeys>,
)

internal fun buildCommentaryFeed(commentary: List<CommentaryEntry>, filter: CommentaryFilter): CommentaryFeed {
    val blocks = buildCommentaryBlocks(filterCommentary(commentary, filter).map { it.parseBall() })
    return CommentaryFeed(blocks, commentaryItemKeys(blocks))
}

/** Lazy-list keys for one [CommentaryBlock]: its innings divider, its over card, and each line. */
@Immutable
internal data class CommentaryBlockKeys(
    val innings: String,
    val over: String,
    val balls: List<String>,
)

/**
 * Keys built from what an item IS, not where it sits.
 *
 * The feed is newest-first, so a key carrying the block index (`ball_${blockIndex}_$i`) changed for
 * every row below a newly arrived ball: each tick recomposed the whole list, `animateItem()` animated
 * rows that had not moved, and a reader scrolled back through an innings was jumped. Keys here come
 * from innings + over + ball label (or the line's text for a non-delivery), so an existing row keeps
 * its key when a ball lands above it.
 *
 * Uniqueness is guaranteed rather than assumed: the same label can legitimately repeat (two
 * innings of a Test, a republished line), and a duplicate key throws (ANDROID-40). A repeat gets an
 * occurrence suffix, counted from the OLDEST end, so the suffix of an existing row does not shift
 * when a newer duplicate arrives at the top.
 */
internal fun commentaryItemKeys(blocks: List<CommentaryBlock>): List<CommentaryBlockKeys> {
    val seen = HashMap<String, Int>()
    fun unique(base: String): String {
        val n = seen.getOrDefault(base, 0)
        seen[base] = n + 1
        return if (n == 0) base else "$base#$n"
    }
    // Walk oldest-first to assign suffixes, then restore the feed's order.
    return blocks.asReversed().map { block ->
        val scope = "${block.innings ?: "-"}_${block.overNumber ?: "-"}"
        val balls = block.balls.asReversed().map { ball ->
            unique("ball_${scope}_${ball.ballLabel ?: "t${ball.ballText.hashCode()}"}")
        }.asReversed()
        CommentaryBlockKeys(
            innings = unique("inns_$scope"),
            over = unique("sep_$scope"),
            balls = balls,
        )
    }.asReversed()
}
