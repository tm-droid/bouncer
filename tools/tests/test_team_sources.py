"""Offline tests for the team branding tools. Run: python -m unittest discover -s tools/tests"""

import datetime
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import team_sources as ts  # noqa: E402
import update_logos_extra as extra  # noqa: E402


class VariantTest(unittest.TestCase):
    def test_shared_fixture_cases(self):
        with open(ts.VARIANTS_PATH, encoding="utf-8") as handle:
            cases = json.load(handle)["cases"]
        self.assertTrue(cases)
        for name, expected in cases:
            self.assertEqual(expected, ts.base_side(name), name)

    def test_normalize_matches_the_kotlin_rule(self):
        self.assertEqual("namibia u19", ts.normalize("Namibia  U19"))
        self.assertEqual("st helena", ts.normalize("St. Helena"))
        self.assertEqual("durban s super giants", ts.normalize("Durban's Super Giants"))
        self.assertEqual("a b", ts.normalize("a_b"))

    def test_flag_codes(self):
        self.assertEqual("gb-eng", ts.flag_code("England Women"))
        self.assertEqual("hk", ts.flag_code("Hong Kong U19"))
        self.assertEqual("us", ts.flag_code("United States of America U19"))
        self.assertIsNone(ts.flag_code("West Indies"))
        self.assertIsNone(ts.flag_code("Mumbai Indians"))

    def test_every_flag_code_is_a_plausible_flag_icons_stem(self):
        for country, code in ts.COUNTRY_ISO.items():
            self.assertRegex(code, r"^[a-z]{2}(-[a-z]{3})?$", country)


class SportsDbTest(unittest.TestCase):
    RESPONSE = {
        "teams": [
            {"strTeam": "Rajasthan Royals FC", "strSport": "Soccer", "strBadge": "x", "strColour1": "#000000"},
            {
                "strTeam": "Rajasthan Royals",
                "strAlternate": "RR, Royals",
                "strSport": "Cricket",
                "strBadge": "https://r2.thesportsdb.com/images/media/team/badge/rr.png",
                "strColour1": "#EA1A85",
                "strColour2": "254AA5",
                "strColour3": "",
            },
        ]
    }

    def test_picks_the_cricket_team_with_the_same_name(self):
        hit = ts.sportsdb_pick(self.RESPONSE, "Rajasthan Royals")
        self.assertEqual("https://r2.thesportsdb.com/images/media/team/badge/rr.png", hit["logo"])
        self.assertEqual(["#EA1A85", "#254AA5"], hit["colours"])

    def test_alternate_names_match(self):
        self.assertIsNotNone(ts.sportsdb_pick(self.RESPONSE, "Royals"))

    def test_other_sports_and_other_names_do_not(self):
        self.assertIsNone(ts.sportsdb_pick(self.RESPONSE, "Rajasthan Royals FC"))
        self.assertIsNone(ts.sportsdb_pick(self.RESPONSE, "Rajasthan"))
        self.assertIsNone(ts.sportsdb_pick({"teams": None}, "Royals"))
        self.assertIsNone(ts.sportsdb_pick(None, "Royals"))


class WikidataTest(unittest.TestCase):
    SEARCH = {
        "search": [
            {"id": "Q1", "label": "Mumbai Indians", "description": "film"},
            {"id": "Q2", "label": "Mumbai Indians", "description": "Indian cricketer"},
            {"id": "Q3", "label": "Mumbai Indians", "description": "Indian Premier League cricket franchise"},
        ]
    }

    def test_entity_must_be_a_cricket_team(self):
        self.assertEqual("Q3", ts.wikidata_pick_entity(self.SEARCH, "Mumbai Indians"))
        self.assertIsNone(ts.wikidata_pick_entity(self.SEARCH, "Mumbai"))

    def test_national_team_label_suffix_is_ignored(self):
        search = {"search": [{"id": "Q9", "label": "Nepal national cricket team", "description": "national cricket team"}]}
        self.assertEqual("Q9", ts.wikidata_pick_entity(search, "Nepal"))

    def test_logo_and_colours(self):
        entity = {
            "claims": {
                "P154": [{"mainsnak": {"datavalue": {"value": "Mumbai Indians Logo.svg"}}}],
                "P6364": [{"mainsnak": {"datavalue": {"value": {"id": "Q100"}}}}],
            }
        }
        self.assertEqual(
            "https://commons.wikimedia.org/wiki/Special:FilePath/Mumbai_Indians_Logo.svg?width=256",
            ts.wikidata_logo(entity),
        )
        self.assertEqual(["Q100"], ts.wikidata_colour_ids(entity))
        colour = {"claims": {"P465": [{"mainsnak": {"datavalue": {"value": "004BA0"}}}]}}
        self.assertEqual("#004BA0", ts.wikidata_colour_hex(colour))
        self.assertIsNone(ts.wikidata_logo({}))


class ChoiceTest(unittest.TestCase):
    def test_first_tier_with_a_colour_wins(self):
        self.assertEqual(["#111111", "#222222"], ts.choose_colours(None, [], ["#111111", "#222222"], ["#333333"]))

    def test_single_colour_doubles(self):
        self.assertEqual(["#111111", "#111111"], ts.choose_colours(["#111111"]))

    def test_nothing(self):
        self.assertIsNone(ts.choose_colours(None, []))


class LookupScheduleTest(unittest.TestCase):
    TODAY = datetime.date(2026, 10, 18)

    def test_no_logo_first_then_no_colour_and_fresh_skipped(self):
        universe = {"A Side", "B Side", "C Side", "D Side"}
        logos = {"B Side": "x", "C Side": "x", "D Side": "x"}
        colours = {"C Side": ["#000000", "#000000"]}
        lookups = {"D Side": {"checked": "2026-10-01"}}
        self.assertEqual(["A Side", "B Side"], extra.candidates(universe, logos, colours, lookups, self.TODAY))

    def test_stale_entries_are_due_again(self):
        lookups = {"A Side": {"checked": "2026-07-01"}}
        self.assertEqual(["A Side"], extra.candidates({"A Side"}, {}, {}, lookups, self.TODAY))

    def test_short_codes_are_not_names(self):
        self.assertTrue(extra.is_short_code("IND"))
        self.assertFalse(extra.is_short_code("Oman"))

    def test_variant_logo_uses_the_parent(self):
        index = {"india": "india.jpg"}
        self.assertEqual("india.jpg", extra.variant_logo("India A Women", index))
        self.assertIsNone(extra.variant_logo("India", index))


if __name__ == "__main__":
    unittest.main()
