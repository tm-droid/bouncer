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
                "strTeamAlternate": "RR, Royals",
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
    @staticmethod
    def row(team, label, logo=None, hex_value=None):
        row = {"team": {"value": team}, "label": {"value": label}}
        if logo:
            row["logo"] = {"value": logo}
        if hex_value:
            row["hex"] = {"value": hex_value}
        return row

    def test_labels_become_team_names(self):
        self.assertEqual("nepal", ts.wikidata_key("Nepal national cricket team"))
        self.assertEqual("lancashire", ts.wikidata_key("Lancashire County Cricket Club"))
        self.assertEqual("ireland", ts.wikidata_key("Ireland women's national cricket team"))
        self.assertEqual("mumbai indians", ts.wikidata_key("Mumbai Indians"))

    def test_index_keeps_logo_and_colours(self):
        index = ts.wikidata_index([
            self.row("Q1", "Mumbai Indians", "http://commons.wikimedia.org/wiki/Special:FilePath/Mumbai%20Indians%20Logo.svg", "004BA0"),
            self.row("Q1", "Mumbai Indians", None, "D1AB3E"),
            self.row("Q2", "Kent County Cricket Club"),
        ])
        self.assertEqual(
            {"mumbai indians": {
                "logo": "https://commons.wikimedia.org/wiki/Special:FilePath/Mumbai_Indians_Logo.svg?width=256",
                "colours": ["#004BA0", "#D1AB3E"],
            }},
            index,
        )

    def test_a_name_two_sides_share_is_dropped(self):
        index = ts.wikidata_index([
            self.row("Q1", "Western Province", hex_value="FF0000"),
            self.row("Q2", "Western Province cricket team", hex_value="0000FF"),
        ])
        self.assertEqual({}, index)


class ChoiceTest(unittest.TestCase):
    def test_first_tier_with_a_colour_wins(self):
        self.assertEqual(["#111111", "#222222"], ts.choose_colours(None, [], ["#111111", "#222222"], ["#333333"]))

    def test_single_colour_doubles(self):
        self.assertEqual(["#111111", "#111111"], ts.choose_colours(["#111111"]))

    def test_nothing(self):
        self.assertIsNone(ts.choose_colours(None, []))


class LookupScheduleTest(unittest.TestCase):
    TODAY = datetime.date(2026, 10, 18)

    def test_national_sides_first_then_crestless_ones(self):
        universe = {"Nepal", "Nepal Women", "Kent", "New Side", "Old Side", "Found Side"}
        logos = {"Nepal": "x", "Nepal Women": "x", "Kent": "x"}
        lookups = {"Old Side": {"checked": "2026-10-01"}, "Found Side": {"sportsdb": {"logo": "y"}}}
        # Nepal Women follows Nepal; Kent has a crest; Old Side is fresh; Found Side was listed.
        self.assertEqual(["Nepal", "New Side"], extra.candidates(universe, logos, lookups, self.TODAY))

    def test_stale_entries_are_due_again(self):
        lookups = {"A Side": {"checked": "2026-07-01", "sportsdb": None}}
        self.assertEqual(["A Side"], extra.candidates({"A Side"}, {}, lookups, self.TODAY))

    def test_short_codes_are_not_names(self):
        self.assertTrue(extra.is_short_code("IND"))
        self.assertFalse(extra.is_short_code("Oman"))

    def test_variant_logo_uses_the_parent(self):
        index = {"india": "india.jpg"}
        self.assertEqual("india.jpg", extra.variant_logo("India A Women", index))
        self.assertIsNone(extra.variant_logo("India", index))


class SportsDbIndexTest(unittest.TestCase):
    def test_listings_are_indexed_by_name_alternate_and_national_short_name(self):
        index = ts.sportsdb_index([
            {"strTeam": "India Cricket", "strSport": "Cricket", "strBadge": "bcci.png"},
            {"strTeam": "Mumbai Indians", "strTeamAlternate": "MI", "strSport": "Cricket", "strBadge": "mi.png"},
            {"strTeam": "Mumbai City", "strSport": "Soccer", "strBadge": "fc.png"},
        ])
        self.assertEqual("bcci.png", index["india"]["logo"])
        self.assertEqual("bcci.png", index["india cricket"]["logo"])
        self.assertEqual("mi.png", index["mi"]["logo"])
        self.assertNotIn("mumbai city", index)

    def test_a_name_two_teams_share_is_dropped(self):
        index = ts.sportsdb_index([
            {"strTeam": "Royals", "strSport": "Cricket", "strBadge": "a.png"},
            {"strTeam": "Royals", "strSport": "Cricket", "strBadge": "b.png"},
        ])
        self.assertNotIn("royals", index)


class LogoInkMapTest(unittest.TestCase):
    def test_measured_reversals_and_overrides_are_keyed_by_crest_url(self):
        logos = {
            "England": "https://r2.thesportsdb.com/eng.png/small",
            "England Women": "https://r2.thesportsdb.com/eng.png/small",
            "Scotland": "https://r2.thesportsdb.com/sco.png/small",
            "Scotland Women": "https://r2.thesportsdb.com/sco.png/small",
            "Kenya": "https://r2.thesportsdb.com/ken.png/small",
            "Surrey": "https://static.cricbuzz.com/a/img/v1/720x540/i1/c1/i.jpg",
        }
        inks = {
            "https://r2.thesportsdb.com/eng.png/small": "dark",
            "https://r2.thesportsdb.com/ken.png/small": None,
        }
        self.assertEqual(
            {"https://r2.thesportsdb.com/eng.png/small": "dark", "https://r2.thesportsdb.com/sco.png/small": "dark"},
            extra.build_logo_ink(logos, inks, {"Scotland": "dark", "Surrey": "dark", "Nowhere": "light"}),
        )


class LogoPrecedenceTest(unittest.TestCase):
    def test_sportsdb_badges_take_the_small_rendition(self):
        self.assertEqual("https://r2.thesportsdb.com/x.png/small", extra.sportsdb_sized("https://r2.thesportsdb.com/x.png"))
        self.assertEqual("https://r2.thesportsdb.com/x.png/small", extra.sportsdb_sized("https://r2.thesportsdb.com/x.png/small"))
        self.assertIsNone(extra.sportsdb_sized(None))

    def test_cricbuzz_refresh_keeps_a_better_crest(self):
        import update_logos

        existing = {"Mumbai Indians": "https://r2.thesportsdb.com/x.png/small", "Kent": "https://static.cricbuzz.com/old"}
        incoming = {"Mumbai Indians": {"url": "https://static.cricbuzz.com/mi"}, "Kent": {"url": "https://static.cricbuzz.com/new"}}
        merged, _, updated = update_logos.merge(
            existing, incoming, lambda t: t["url"], keep=lambda url: "static.cricbuzz.com" not in url
        )
        self.assertEqual("https://r2.thesportsdb.com/x.png/small", merged["Mumbai Indians"])
        self.assertEqual("https://static.cricbuzz.com/new", merged["Kent"])
        self.assertEqual(["Kent"], updated)


class CrestInkTest(unittest.TestCase):
    @staticmethod
    def crest(*fills):
        """A 40x40 transparent image with a 20x20 square per fill, side by side."""
        from PIL import Image, ImageDraw

        image = Image.new("RGBA", (40 * len(fills), 40), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        for i, fill in enumerate(fills):
            draw.rectangle((40 * i + 10, 10, 40 * i + 29, 29), fill=fill + (255,))
        return image

    @staticmethod
    def line_art(fill):
        """A 40x40 ring, 4px thick: line art, ~36% of its bounding box opaque."""
        from PIL import Image, ImageDraw

        image = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
        ImageDraw.Draw(image).ellipse((2, 2, 37, 37), outline=fill + (255,), width=4)
        return image

    def test_a_navy_mark_vanishes_on_dark_and_is_reversed(self):
        self.assertEqual("dark", ts.crest_ink(self.line_art((0x1B, 0x2A, 0x5C))))

    def test_a_white_mark_vanishes_on_light_and_is_reversed(self):
        self.assertEqual("light", ts.crest_ink(self.line_art((0xFF, 0xFF, 0xFF))))

    def test_a_filled_shape_is_never_reversed_into_a_blob(self):
        # A solid navy square vanishes on dark, but reversed it would be a white square.
        self.assertIsNone(ts.crest_ink(self.crest((0x1B, 0x2A, 0x5C))))

    def test_a_dark_multicolour_mark_is_unreadable_not_reversed(self):
        # Black, deep red and deep green: all dark, but reversing would lose the colours.
        self.assertEqual("unreadable", ts.crest_ink(self.crest((0, 0, 0), (0x60, 0, 0), (0, 0x40, 0))))

    def test_a_mid_tone_mark_reads_on_both(self):
        self.assertIsNone(ts.crest_ink(self.crest((0xE0, 0x30, 0x30))))

    def test_an_empty_image_is_none(self):
        from PIL import Image

        self.assertIsNone(ts.crest_ink(Image.new("RGBA", (10, 10), (0, 0, 0, 0))))


if __name__ == "__main__":
    unittest.main()
