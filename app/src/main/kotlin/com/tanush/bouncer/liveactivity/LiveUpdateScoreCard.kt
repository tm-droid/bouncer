package com.tanush.bouncer.liveactivity

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.tanush.bouncer.R

/**
 * Binds a [LiveUpdateScoreboard] to the designed notification layout.
 *
 * Only reached where the device cannot promote a notification, because `RemoteViews` disqualify
 * promotion outright — see [LiveUpdateNotifier]. That is most of the install base, so this is the
 * card most people actually see.
 *
 * Everything here is `setTextViewText` / `setViewVisibility` / `setImageViewBitmap`: the layout
 * carries the typography and the `@color/` references that flip with night mode, and this only
 * fills it in. A row with nothing to say is set to `GONE` rather than left holding a blank string,
 * so the card shrinks instead of showing gaps.
 */
internal object LiveUpdateScoreCard {

    fun collapsed(context: Context, content: LiveUpdateContent, battingIcon: Bitmap?): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_live_score_collapsed).apply {
            val board = content.scoreboard
            setTextViewText(R.id.collapsed_score, collapsedScore(board))
            setTextViewText(R.id.collapsed_detail, content.text)
            bindLogo(R.id.batting_logo, battingIcon)
        }

    fun expanded(
        context: Context,
        content: LiveUpdateContent,
        battingIcon: Bitmap?,
        opponentIcon: Bitmap?,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_live_score).apply {
        val board = content.scoreboard

        setTextViewText(R.id.batting_name, board.battingName.orEmpty())
        setTextViewText(R.id.batting_score, board.battingScore.orEmpty())
        bindOrHide(R.id.batting_overs, board.battingOvers?.let { "$it ov" })
        setTextViewText(R.id.opponent_name, board.opponentName.orEmpty())
        setTextViewText(R.id.opponent_score, board.opponentScore.orEmpty())
        bindLogo(R.id.batting_logo, battingIcon)
        bindLogo(R.id.opponent_logo, opponentIcon)

        bindPlayer(R.id.batter1_row, R.id.batter1_name, R.id.batter1_figure, board.batters.getOrNull(0))
        bindPlayer(R.id.batter2_row, R.id.batter2_name, R.id.batter2_figure, board.batters.getOrNull(1))
        bindPlayer(R.id.bowler_row, R.id.bowler_name, R.id.bowler_figure, board.bowler)

        // A row of its own, tracked out. Joined onto the status line the codes read as part of the
        // target ("Need 264 runs · RRR 8.59 · 0 0 0 0 4 0") rather than as a sequence of balls.
        bindOrHide(R.id.recent_balls, board.recentBalls.takeIf { it.isNotEmpty() }?.joinToString("  "))
        bindOrHide(R.id.status_line, board.statusLine)
    }

    /** "31/4 (9.5)  ·  RSA 335/9" — both sides on the one line the collapsed card allows. */
    private fun collapsedScore(board: LiveUpdateScoreboard): String {
        val batting = listOfNotNull(
            board.battingName,
            board.battingScore,
            board.battingOvers?.let { "($it)" },
        ).joinToString(" ")
        val opponent = sideLabel(board.opponentName, board.opponentScore)
        return listOfNotNull(batting.takeIf { it.isNotBlank() }, opponent).joinToString("   ")
    }

    private fun RemoteViews.bindPlayer(
        rowId: Int,
        nameId: Int,
        figureId: Int,
        line: LiveUpdateScoreboard.PlayerLine?,
    ) {
        if (line == null) {
            setViewVisibility(rowId, View.GONE)
            return
        }
        setViewVisibility(rowId, View.VISIBLE)
        // The striker's dot leads the name, as it does in the design references. An asterisk after
        // the figure would sit in the numeric column and break its alignment.
        setTextViewText(nameId, if (line.isStriker) "• ${line.name}" else line.name)
        setTextViewText(figureId, line.figure)
    }

    private fun RemoteViews.bindOrHide(viewId: Int, text: String?) {
        if (text.isNullOrBlank()) {
            setViewVisibility(viewId, View.GONE)
            return
        }
        setViewVisibility(viewId, View.VISIBLE)
        setTextViewText(viewId, text)
    }

    private fun RemoteViews.bindLogo(viewId: Int, bitmap: Bitmap?) {
        if (bitmap == null) {
            setViewVisibility(viewId, View.GONE)
            return
        }
        setViewVisibility(viewId, View.VISIBLE)
        setImageViewBitmap(viewId, bitmap)
    }
}
