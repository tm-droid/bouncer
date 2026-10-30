"""The pure half of the team branding tools: names, variants, countries, flags, and reading what the
logo and colour sources return. No network here, so `tools/tests` can pin all of it offline.

The network half is `update_logos_extra.py` (lookups) and `extract_team_colors.py` (colours). Both
run in `.github/workflows/daily_logo_update.yml`, because a GitHub runner reaches TheSportsDB,
Wikidata and Commons, and a development sandbox usually reaches none of them.
"""

import json
import os
import re
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
VARIANTS_PATH = os.path.join(HERE, "team_variants.json")

_NON_ALPHANUMERIC = re.compile(r"[^\w]+|_+", re.UNICODE)

with open(VARIANTS_PATH, encoding="utf-8") as _handle:
    VARIANT_SUFFIXES = json.load(_handle)["suffixes"]


def normalize(name):
    """`normalizeTeamName` in TeamNameMatching.kt: punctuation to spaces, lowercase, single spaces."""
    return " ".join(_NON_ALPHANUMERIC.sub(" ", name).lower().split())


def base_side(name):
    """The parent side of a variant ('India A Women' -> 'india'), normalized. Strips variant words
    from the end until none is left, but never down to nothing: a side called 'Women' stays."""
    words = normalize(name).split()
    stripped = True
    while stripped:
        stripped = False
        for suffix in sorted(VARIANT_SUFFIXES, key=len, reverse=True):
            tail = suffix.split()
            if len(words) > len(tail) and words[-len(tail):] == tail:
                words = words[: -len(tail)]
                stripped = True
                break
    return " ".join(words)


# Normalized country (or cricket nation) -> flag-icons file stem. ICC members plus the home nations
# and Crown dependencies that field their own sides. Hand-checked: a wrong code paints a side in
# another country's flag. West Indies has no flag and is absent on purpose; its colours are curated.
COUNTRY_ISO = {
    "afghanistan": "af", "argentina": "ar", "australia": "au", "austria": "at", "bahamas": "bs",
    "bahrain": "bh", "bangladesh": "bd", "belgium": "be", "belize": "bz", "bermuda": "bm",
    "bhutan": "bt", "botswana": "bw", "brazil": "br", "bulgaria": "bg", "cambodia": "kh",
    "cameroon": "cm", "canada": "ca", "cayman islands": "ky", "chile": "cl", "china": "cn",
    "cook islands": "ck", "costa rica": "cr", "croatia": "hr", "cyprus": "cy",
    "czech republic": "cz", "czechia": "cz", "denmark": "dk", "england": "gb-eng",
    "estonia": "ee", "eswatini": "sz", "falkland islands": "fk", "fiji": "fj", "finland": "fi",
    "france": "fr", "gambia": "gm", "germany": "de", "ghana": "gh", "gibraltar": "gi",
    "greece": "gr", "guernsey": "gg", "hong kong": "hk", "hungary": "hu", "india": "in",
    "indonesia": "id", "iran": "ir", "ireland": "ie", "isle of man": "im", "israel": "il",
    "italy": "it", "japan": "jp", "jersey": "je", "kenya": "ke", "kuwait": "kw", "lesotho": "ls",
    "luxembourg": "lu", "malawi": "mw", "malaysia": "my", "maldives": "mv", "mali": "ml",
    "malta": "mt", "mexico": "mx", "mongolia": "mn", "mozambique": "mz", "myanmar": "mm",
    "namibia": "na", "nepal": "np", "netherlands": "nl", "new zealand": "nz", "nigeria": "ng",
    "north macedonia": "mk", "norway": "no", "oman": "om", "pakistan": "pk", "panama": "pa",
    "papua new guinea": "pg", "peru": "pe", "philippines": "ph", "portugal": "pt", "qatar": "qa",
    "romania": "ro", "russia": "ru", "rwanda": "rw", "saint helena": "sh", "st helena": "sh",
    "samoa": "ws", "saudi arabia": "sa", "scotland": "gb-sct", "serbia": "rs",
    "seychelles": "sc", "sierra leone": "sl", "singapore": "sg", "slovenia": "si",
    "south africa": "za", "south korea": "kr", "korea": "kr", "spain": "es", "sri lanka": "lk",
    "suriname": "sr", "sweden": "se", "switzerland": "ch", "tanzania": "tz", "thailand": "th",
    "timor leste": "tl", "tonga": "to", "turkey": "tr", "turks and caicos island": "tc",
    "turks and caicos islands": "tc", "uganda": "ug", "united arab emirates": "ae", "uae": "ae",
    "united states of america": "us", "usa": "us", "united states": "us", "vanuatu": "vu",
    "wales": "gb-wls", "zambia": "zm", "zimbabwe": "zw",
}


def flag_code(name):
    """The flag-icons stem for a national side or any of its variants, or None for a club."""
    return COUNTRY_ISO.get(base_side(name))


# --- TheSportsDB -------------------------------------------------------------------------------

_HEX = re.compile(r"^#?([0-9A-Fa-f]{6})$")


def hex_or_none(value):
    match = _HEX.match((value or "").strip())
    return f"#{match.group(1).upper()}" if match else None


def sportsdb_names(team):
    """A TheSportsDB team's name and alternates, normalized."""
    alternates = team.get("strTeamAlternate") or team.get("strAlternate") or ""
    return {normalize(n) for n in [team.get("strTeam") or ""] + alternates.split(",") if n.strip()}


def sportsdb_entry(team):
    colours = [c for c in (hex_or_none(team.get(f"strColour{i}")) for i in (1, 2, 3)) if c]
    return {"logo": team.get("strBadge") or team.get("strLogo") or None, "colours": colours}


def is_cricket(team):
    return (team.get("strSport") or "").lower() == "cricket"


def sportsdb_pick(response, name, also=()):
    """The cricket team in a `searchteams.php` response whose name (or alternate) matches `name` or
    one of `also`, as {"logo": url|None, "colours": [hex, ...]}; None when nothing matches. The search
    is fuzzy and returns football clubs called "Royals" for a cricket side called "Royals"; neither
    is accepted."""
    wanted_names = {normalize(name)} | {normalize(n) for n in also}
    for team in (response or {}).get("teams") or []:
        if is_cricket(team) and wanted_names & sportsdb_names(team):
            return sportsdb_entry(team)
    return None


_NATIONAL_TAIL = re.compile(r" cricket$")


def sportsdb_index(teams):
    """Teams from the bulk league listings -> {normalized name: entry}. A national side's "India
    Cricket" is also indexed as "india". A name two teams share with different data is dropped."""
    index = {}
    ambiguous = set()
    for team in teams or []:
        if not is_cricket(team):
            continue
        entry = sportsdb_entry(team)
        names = sportsdb_names(team)
        names |= {_NATIONAL_TAIL.sub("", n) for n in names}
        for key in names:
            if key in index and index[key] != entry:
                ambiguous.add(key)
            index.setdefault(key, entry)
    for key in ambiguous:
        index.pop(key, None)
    return index


# --- Wikidata ----------------------------------------------------------------------------------

COMMONS_FILE = "https://commons.wikimedia.org/wiki/Special:FilePath/{file}?width=256"

# Every cricket side on Wikidata (sport P641 = cricket Q5375, not a person) with its logo (P154)
# and official colours (P6364 -> hex P465), in one query. Measured October 2026: ~12,000 entities,
# 59 with a logo and 5 with a colour, because most club logos are non-free and so not on Commons.
# Thin, but one request a week; per-name API lookups cost three requests each and hit 429s.
WIKIDATA_SPARQL = """
SELECT ?team ?label ?logo ?hex WHERE {
  ?team wdt:P641 wd:Q5375 .
  FILTER NOT EXISTS { ?team wdt:P31 wd:Q5 }
  ?team rdfs:label ?label . FILTER(LANG(?label) = "en")
  OPTIONAL { ?team wdt:P154 ?logo }
  OPTIONAL { ?team wdt:P6364 ?colour . ?colour wdt:P465 ?hex }
}
"""

_WIKIDATA_LABEL_TAIL = re.compile(
    r" (((men s|women s) )?national )?(cricket team|county cricket club|cricket club|cricket)$"
)


def wikidata_key(label):
    """A Wikidata label as a team name: "Nepal national cricket team" -> "nepal"."""
    return _WIKIDATA_LABEL_TAIL.sub("", normalize(label))


def commons_thumbnail(file_url_or_name):
    """A Commons PNG thumbnail for a P154 value (a file name, or the SPARQL FilePath URL). Commons
    serves SVG logos as PNG at `width`, which Coil shows without an SVG decoder."""
    name = file_url_or_name.rsplit("/Special:FilePath/", 1)[-1]
    name = urllib.parse.unquote(name).strip().replace(" ", "_")
    return COMMONS_FILE.format(file=urllib.parse.quote(name, safe="_.-()"))


def wikidata_index(bindings):
    """SPARQL result rows -> {team name key: {"logo": url|None, "colours": [hex]}}.

    Only entities with a logo or a colour are kept. A key two entities share with different data
    (two clubs called "Western Province") is dropped: neither is known to be the side we mean.
    """
    entities = {}
    for row in bindings or []:
        entity = entities.setdefault(
            row["team"]["value"], {"label": row["label"]["value"], "logo": None, "colours": []}
        )
        if "logo" in row and not entity["logo"]:
            entity["logo"] = commons_thumbnail(row["logo"]["value"])
        hex_value = hex_or_none(row.get("hex", {}).get("value"))
        if hex_value and hex_value not in entity["colours"]:
            entity["colours"].append(hex_value)
    index = {}
    ambiguous = set()
    for entity in entities.values():
        if not entity["logo"] and not entity["colours"]:
            continue
        key = wikidata_key(entity["label"])
        found = {"logo": entity["logo"], "colours": entity["colours"]}
        if key in index and index[key] != found:
            ambiguous.add(key)
        index.setdefault(key, found)
    for key in ambiguous:
        index.pop(key, None)
    return index


# --- Choosing --------------------------------------------------------------------------------

def choose_colours(*tiers):
    """The first tier with a colour, as [primary, secondary]; a single colour doubles up. Tiers are
    given highest first; each is a list of hex strings or None."""
    for tier in tiers:
        colours = [c for c in (tier or []) if c]
        if colours:
            return [colours[0], colours[1] if len(colours) > 1 else colours[0]]
    return None


# --- Crest ink ---------------------------------------------------------------------------------

# TeamCrest's plate, `surfaceVariant` in ColorTokensV2.kt (Gray100 light, DarkSurfaceVariant dark).
# Pinned to the Kotlin tokens by CrestPlateParityTest.
LIGHT_PLATE = (0xF5, 0xF5, 0xF5)
DARK_PLATE = (0x20, 0x28, 0x34)
# A crest pixel under this contrast with the plate is not seen against it.
MIN_PIXEL_CONTRAST = 2.0
# A crest with at least this share of its opaque area unseen on a plate vanishes on it. Measured on
# the October 2026 board crests: England on the dark plate and New Zealand on the light lose ~100%;
# every crest that still read (Scotland's thistle, Kenya's lion, India's seal) kept 35% or more.
VANISHED_SHARE = 0.75
# One ink: this share of the crest within ONE_INK_RATIO of its median luminance, and its coloured
# pixels within one hue sector. Kenya's lion is black, red and green at one brightness, so the hue
# test is what keeps it from being reversed into a white blob.
ONE_INK_SHARE = 0.85
ONE_INK_RATIO = 2.5
# Reversing redraws every opaque pixel in one colour, which only works on line art: it turns a filled
# shape into a blob. Opaque share of the crest's bounding box, measured: NZ fern 0.21, Victoria 0.40,
# England 0.44, Warwickshire 0.52 reverse well; Middlesex's disc 0.61 and Hampshire's shield 0.79
# became a white disc and a black shield, and are left as drawn (their inner marks still show).
MAX_REVERSIBLE_FILL = 0.57


def relative_luminance(r, g, b):
    def channel(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4

    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a, b):
    high, low = max(a, b), min(a, b)
    return (high + 0.05) / (low + 0.05)


def _is_one_ink(pixels, luminances):
    import colorsys
    from collections import Counter

    median = sorted(luminances)[len(luminances) // 2]
    if sum(1 for l in luminances if contrast(l, median) < ONE_INK_RATIO) / len(luminances) < ONE_INK_SHARE:
        return False
    sectors = Counter()
    for r, g, b in pixels:
        h, light, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        if s > 0.25 and 0.08 < light < 0.92:
            sectors[int(h * 12) % 12] += 1
    coloured = sum(sectors.values())
    if coloured < 0.15 * len(pixels):
        return True
    top = sectors.most_common(1)[0][0]
    near = sum(n for k, n in sectors.items() if min((k - top) % 12, (top - k) % 12) <= 1)
    return near / coloured >= ONE_INK_SHARE


def crest_ink(image):
    """How a crest (a PIL image) fares on TeamCrest's two plates:

    * "dark": one dark ink that vanishes on the dark plate (England's navy). The app draws it
      reversed, in the theme's text colour, on that plate: the white version a logo kit carries.
    * "light": one light ink that vanishes on the light plate (New Zealand's white fern).
      Both only for line art (MAX_REVERSIBLE_FILL); a filled one-ink shape is left as drawn.
    * "unreadable": vanishes on a plate but is several colours, so reversing it would lose them.
      The caller keeps the Cricbuzz crest instead.
    * None: reads on both.
    """
    rgba = image.convert("RGBA")
    data = rgba.get_flattened_data() if hasattr(rgba, "get_flattened_data") else rgba.getdata()
    pixels = [p[:3] for p in data if p[3] >= 128]
    if not pixels:
        return None
    luminances = [relative_luminance(*p) for p in pixels]
    plates = {"dark": relative_luminance(*DARK_PLATE), "light": relative_luminance(*LIGHT_PLATE)}
    for ink, plate in plates.items():
        unseen = sum(1 for l in luminances if contrast(l, plate) < MIN_PIXEL_CONTRAST) / len(luminances)
        if unseen >= VANISHED_SHARE:
            if not _is_one_ink(pixels, luminances):
                return "unreadable"
            return ink if _fill(rgba, len(pixels)) <= MAX_REVERSIBLE_FILL else None
    return None


def _fill(rgba, opaque):
    left, top, right, bottom = rgba.getchannel("A").point(lambda a: 255 if a >= 128 else 0).getbbox()
    return opaque / ((right - left) * (bottom - top))
