package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.R
import com.tanush.bouncer.models.MatchContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which of the server's two pieces of prose the Overview shows, and when it shows neither. */
class MatchSurfaceTest {

    private val both = MatchContext(
        preview = MatchContext.Surface("India play West Indies."),
        summary = MatchContext.Surface("India won by 4 wkts."),
    )

    @Test
    fun `a match in play or still to start shows its preview`() {
        assertEquals(R.string.match_preview_title to "India play West Indies.", matchSurfaceFor(both, isCompleted = false))
    }

    @Test
    fun `a finished match shows its summary, and never falls back to the preview`() {
        assertEquals(R.string.match_summary_title to "India won by 4 wkts.", matchSurfaceFor(both, isCompleted = true))
        assertNull(matchSurfaceFor(both.copy(summary = null), isCompleted = true))
    }

    @Test
    fun `no context, or blank text, shows nothing`() {
        assertNull(matchSurfaceFor(null, isCompleted = false))
        assertNull(matchSurfaceFor(MatchContext(preview = MatchContext.Surface("  ")), isCompleted = false))
    }
}
