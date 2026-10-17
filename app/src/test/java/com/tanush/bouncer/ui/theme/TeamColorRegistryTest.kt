package com.tanush.bouncer.ui.theme

import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.v2.DarkColors
import com.tanush.bouncer.ui.theme.v2.LightColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamColorRegistryTest {

    @After
    fun clearExtracted() = TeamColorRegistry.loadExtracted(emptyMap())

    private val grounds = listOf(
        "light" to TeamColorRegistry.Ground(LightColors.background, LightColors.surface, LightColors.onSurface),
        "dark" to TeamColorRegistry.Ground(DarkColors.background, DarkColors.surface, DarkColors.onSurface),
    )

    @Test
    fun `current and former franchise names are both known`() {
        assertTrue(TeamColorRegistry.isKnown("Royal Challengers Bengaluru"))
        assertTrue(TeamColorRegistry.isKnown("Royal Challengers Bangalore"))
        assertTrue(TeamColorRegistry.isKnown("RCB"))
        assertTrue(TeamColorRegistry.isKnown("Durban's Super Giants"))
        assertTrue(TeamColorRegistry.isKnown("St. Lucia Kings"))
    }

    @Test
    fun `every full member is known`() {
        listOf(
            "India", "Australia", "England", "Pakistan", "South Africa", "New Zealand", "West Indies",
            "Sri Lanka", "Bangladesh", "Afghanistan", "Ireland", "Zimbabwe",
        ).forEach { assertTrue("$it should be known", TeamColorRegistry.isKnown(it)) }
    }

    @Test
    fun `fuzzy matching is by whole words`() {
        assertTrue("A sides take the parent's colours", TeamColorRegistry.isKnown("India A"))
        assertTrue(TeamColorRegistry.isKnown("England Lions"))
        assertTrue(TeamColorRegistry.isKnown("India Women"))
        assertFalse("Romania contains 'oman' but is not Oman", TeamColorRegistry.isKnown("Romania"))
        assertFalse("Short codes never fuzzy-match", TeamColorRegistry.isKnown("LL"))
        assertFalse(TeamColorRegistry.isKnown("Amsterdam Flames"))
        assertFalse(TeamColorRegistry.isKnown(null))
        assertFalse(TeamColorRegistry.isKnown(""))
    }

    @Test
    fun `crest-derived colours make a team known and lose to curated ones`() {
        val magenta = Color(0xFFCC00CC)
        TeamColorRegistry.loadExtracted(mapOf("Amsterdam Flames" to (magenta to magenta), "Australia" to (magenta to magenta)))
        assertTrue(TeamColorRegistry.isKnown("Amsterdam Flames"))
        val ground = grounds.first().second
        val australia = TeamColorRegistry.forTeam("Australia", ground)
        val expected = TeamColorRegistry.forTeam("AUS", ground)
        assertEquals("curated gold, not the extracted magenta", expected.primary, australia.primary)
    }

    @Test
    fun `unknown teams are neutral, not hashed`() {
        val ground = grounds.first().second
        val a = TeamColorRegistry.forTeam("Amsterdam Flames", ground)
        val b = TeamColorRegistry.forTeam("Dublin Guardians", ground)
        assertEquals("two unknown sides share the neutral", a.primary, b.primary)
    }

    @Test
    fun `team ink clears AA on both grounds for every curated team`() {
        for ((mode, ground) in grounds) {
            CURATED_TEAM_COLORS.keys.forEach { team ->
                val ink = TeamColorRegistry.forTeam(team, ground).ink
                val onSurface = ink.contrastRatio(ground.surface)
                val onBackground = ink.contrastRatio(ground.background)
                assertTrue("$mode $team ink on surface: $onSurface", onSurface >= 4.5f)
                assertTrue("$mode $team ink on background: $onBackground", onBackground >= 4.5f)
            }
        }
    }

    @Test
    fun `every alias points at a curated team`() {
        TEAM_COLOR_ALIASES.forEach { (alias, key) ->
            assertTrue("$alias -> $key is not in CURATED_TEAM_COLORS", key in CURATED_TEAM_COLORS)
        }
    }
}
