package com.tanush.bouncer.ui.components.premium

import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.network.MatchSquads
import com.tanush.bouncer.network.SquadPlayer
import com.tanush.bouncer.network.TeamSquad
import org.junit.Assert.assertEquals
import org.junit.Test

/** The rest of the batting side's eleven, joined on profile ids and never on names. */
class YetToBatTest {

    private fun player(id: String, name: String) = SquadPlayer(id = id, name = name, role = "", captain = false, keeper = false)
    private fun side(id: String, vararg xi: SquadPlayer) =
        TeamSquad(teamId = id, name = id, shortName = id, playingXi = xi.toList(), bench = emptyList(), squad = emptyList())

    private val squads = MatchSquads(
        side("BAN", player("1", "Tanzid Hasan"), player("2", "Parvez Hossain Emon"), player("3", "Saif Hassan")),
        side("AFG", player("11", "Rahmanullah Gurbaz"), player("12", "Rahmat Shah"), player("13", "Rashid Khan")),
    )

    private fun bat(name: String, url: String) = BatsmanStats(name, "c X b Y", "10", "12", "1", "0", "83.3", profileUrl = url)
    private fun innings(vararg batters: BatsmanStats) =
        Innings("AFG", "161-3", "35.3", batters.toList(), emptyList(), emptyList(), extras = "", total = "")

    @Test
    fun `the eleven that holds the innings' batters, less those who batted`() {
        // "Rahmat Shah (c)" on the card and "Rahmat Shah" on the sheet: only the id matches.
        val inn = innings(bat("Rahmanullah Gurbaz", "/profiles/11/rahmanullah-gurbaz"), bat("Rahmat Shah (c)", "/profiles/12/rahmat-shah"))
        assertEquals(listOf("Rashid Khan"), yetToBat(inn, squads).map { it.name })
    }

    @Test
    fun `a batter without an id empties the list rather than listing someone who batted`() {
        val inn = innings(bat("Rahmanullah Gurbaz", "/profiles/11/x"), bat("Rahmat Shah", ""))
        assertEquals(emptyList<SquadPlayer>(), yetToBat(inn, squads))
    }

    @Test
    fun `no named elevens, or nobody in yet, gives nothing`() {
        val preToss = MatchSquads(squads.team1.copy(playingXi = emptyList()), squads.team2.copy(playingXi = emptyList()))
        assertEquals(emptyList<SquadPlayer>(), yetToBat(innings(bat("Rahmat Shah", "/profiles/12/x")), preToss))
        assertEquals(emptyList<SquadPlayer>(), yetToBat(innings(), squads))
        assertEquals(emptyList<SquadPlayer>(), yetToBat(innings(bat("Rahmat Shah", "/profiles/12/x")), null))
    }
}
