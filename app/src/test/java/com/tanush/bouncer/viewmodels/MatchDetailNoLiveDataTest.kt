package com.tanush.bouncer.viewmodels

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** "Nothing arrived" is a failure for a match in play, and the normal state for a fixture. */
class MatchDetailNoLiveDataTest {

    private val empty = MatchDetailViewModel.UiState()

    @Test
    fun `an upcoming match with no data is not a failure`() {
        assertNull(noLiveDataError(empty, upcoming = true))
    }

    @Test
    fun `a match in play with no data is a failure`() {
        assertNotNull(noLiveDataError(empty, upcoming = false))
    }
}
