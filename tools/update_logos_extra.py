"""Logos (and source colours) for the sides Cricbuzz's team pages don't give one.

Runs after `update_logos.py` in `.github/workflows/daily_logo_update.yml`. It fills gaps, and for a
club or franchise (not a national side) it replaces the Cricbuzz crest with TheSportsDB's badge, which
is the real logo where Cricbuzz's is a simplified mark on a coloured square.

Which names: every key of `TeamLogos.json`, plus both teams of every match the public archive holds
(`idx/m/YYYY-MM.json` on tm-droid.github.io/bouncer-archive). The archive half is every side Bouncer
has actually shown, which includes sides Cricbuzz's team pages never list.

Sources, per name, first hit wins for the logo:

1. TheSportsDB (free test key 123, ~30 requests a minute): `strBadge`, and the hand-entered kit
   colours `strColour1..3`, the one real team colour database that has cricket. Fetched in bulk:
   each cricket country's leagues (`search_all_leagues.php?c=X&s=Cricket`, which is the cricket
   filter), then each league's teams (`search_all_teams.php`), about 60 requests in all. The free
   key caps those at 5 leagues and 10 teams a call, so a name the listings miss is then searched
   one by one (`searchteams.php`), but only where it matters: a national side (listed as "India
   Cricket") and a side with no crest at all.
2. Wikidata, in one SPARQL query a run: a cricket side's logo (P154) as a Commons PNG thumbnail,
   and its official colours (P6364 -> P465). Thin for cricket, but one request.
3. A variant's parent side: "India Women", "Hong Kong U19" take the Cricbuzz crest of "India" and
   "Hong Kong", which for a national side is its flag. No network.

What each source found is kept in `tools/team_lookups.json` (committed, saved as the run goes), which
`extract_team_colors.py` reads for the source colours. A one-by-one search is repeated only after
RECHECK_DAYS, and at most MAX_LOOKUPS run, so a big backlog clears over a few weeks.

Merge, never shrink; fail loudly — the rules `update_logos.py` follows. A run whose lookups mostly
fail to connect writes nothing.
"""

import datetime
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import team_sources as ts  # noqa: E402

LOGOS_PATH = "app/src/main/assets/TeamLogos.json"
COLORS_PATH = "app/src/main/assets/TeamColors.json"
LOOKUPS_PATH = "tools/team_lookups.json"
INK_PATH = "app/src/main/assets/TeamLogoInk.json"

ARCHIVE = "https://tm-droid.github.io/bouncer-archive/"
SPORTSDB_API = "https://www.thesportsdb.com/api/v1/json/123/"
SPORTSDB = SPORTSDB_API + "searchteams.php?t={name}"
WIKIDATA_SPARQL = "https://query.wikidata.org/sparql?{query}"

# Wikimedia's policy asks for a User-Agent that names the tool and how to reach its owner.
USER_AGENT = "BouncerTeamLogos/1.0 (https://github.com/tm-droid/bouncer; tools/update_logos_extra.py)"

# TheSportsDB country names to list cricket leagues for. A country with none costs one empty call.
SPORTSDB_COUNTRIES = [
    "India", "England", "Australia", "Pakistan", "Bangladesh", "Sri Lanka", "South Africa",
    "New Zealand", "West Indies", "Trinidad and Tobago", "Jamaica", "Barbados", "Guyana",
    "United Arab Emirates", "United States", "Canada", "Afghanistan", "Zimbabwe", "Ireland",
    "Scotland", "Netherlands", "Nepal", "Wales", "World",
]
# Listings the country search does not reach (international sides are under these).
SPORTSDB_LEAGUES = ["One Day International Series", "Twenty20 International", "Test Series"]
RECHECK_DAYS = 56
SAVE_EVERY = 20
MAX_LOOKUPS = int(os.environ.get("MAX_LOOKUPS", "150"))
SPORTSDB_GAP_S = 2.1
# National sides (and their variants) take the board's crest from TheSportsDB (BCCI for India) over
# Cricbuzz's crest, which is their flag. False puts the flags back on the next run.
NATIONAL_BOARD_CRESTS = True
# Crests reversed by hand, checked by eye in the preview, where crest_ink's rule cannot reach without
# also catching crests it must not. Cricket Scotland's is two inks (black text, purple thistle) with
# ~60% vanishing on the dark plate, under both bars, but it is line art (0.41 of its bounding box) so
# it reverses cleanly. Loosening the rule to catch it would also reverse Kenya's black, red and green
# lion into a white silhouette.
REVERSE_OVERRIDES = {"Scotland": "dark"}
# Most lookups failing to connect is a block or an outage, not a fact about the teams.
MAX_ERROR_SHARE = 0.5

_last_call = {}


class FetchError(Exception):
    pass


def get_json(url, gap_key, gap_s, accept="application/json", retries=3):
    """GET JSON, spaced gap_s from the last call to the same source. A 429 waits out its
    Retry-After (or 30 s) and tries again, up to `retries` times."""
    for attempt in range(retries + 1):
        wait = _last_call.get(gap_key, 0) + gap_s - time.monotonic()
        if wait > 0:
            time.sleep(wait)
        _last_call[gap_key] = time.monotonic()
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": accept})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8", "replace") or "null")
        except urllib.error.HTTPError as error:
            if error.code == 429 and attempt < retries:
                time.sleep(min(int(error.headers.get("Retry-After") or 30), 120))
                continue
            raise FetchError(f"{url}: {error}") from error
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
            raise FetchError(f"{url}: {error}") from error
    raise FetchError(f"{url}: still rate-limited")


def load_json(path, default):
    if not os.path.exists(path):
        return default
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def save_json(path, data, indent=2):
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=indent, ensure_ascii=False)
        handle.write("\n")


def is_short_code(name):
    """A partial archive row names its sides by code ("IND"); a code is not a team name."""
    return len(name) <= 4 and name.isupper()


def archive_team_names():
    """Both teams of every archived match. An unreachable archive is a smaller run, not a failure:
    the Cricbuzz names still get their lookups."""
    try:
        years = get_json(ARCHIVE + "idx/years.json", "archive", 0)
    except FetchError as error:
        print(f"Archive index unavailable ({error}); continuing with TeamLogos.json names only")
        return set()
    names = set()
    for year, months in (years or {}).items():
        for month in months or {}:
            try:
                rows = get_json(f"{ARCHIVE}idx/m/{year}-{month}.json", "archive", 0)
            except FetchError as error:
                print(f"Skipping archive month {year}-{month}: {error}")
                continue
            for row in rows or []:
                for team in row[4:6] if isinstance(row, list) and len(row) > 5 else []:
                    if isinstance(team, str) and team.strip() and not is_short_code(team.strip()):
                        names.add(" ".join(team.split()))
    return names


def is_national(name):
    """A national side itself ("India"), not a variant of one ("India Women")."""
    return ts.flag_code(name) is not None and ts.base_side(name) == ts.normalize(name)


def lookup_sportsdb(name):
    # TheSportsDB names national sides "India Cricket"; a bare "India" finds only football.
    if is_national(name):
        query = f"{name} Cricket"
        response = get_json(SPORTSDB.format(name=urllib.parse.quote(query)), "sportsdb", SPORTSDB_GAP_S)
        hit = ts.sportsdb_pick(response, name, also=(query,))
        if hit:
            return hit
    response = get_json(SPORTSDB.format(name=urllib.parse.quote(name)), "sportsdb", SPORTSDB_GAP_S)
    return ts.sportsdb_pick(response, name)


def wikidata_index():
    """Every cricket side Wikidata has a logo or colour for. Unreachable is an empty index, not a
    failure: it is the minor source."""
    url = WIKIDATA_SPARQL.format(query=urllib.parse.urlencode({"query": ts.WIKIDATA_SPARQL}))
    try:
        result = get_json(url, "wikidata", 0, accept="application/sparql-results+json")
    except FetchError as error:
        print(f"Wikidata unavailable ({error}); continuing without it")
        return {}
    return ts.wikidata_index(((result or {}).get("results") or {}).get("bindings"))


def sportsdb_bulk():
    """Every cricket team the league listings carry, indexed by name. Unreachable is an empty index:
    the one-by-one search still runs for the names that matter."""
    leagues = set(SPORTSDB_LEAGUES)
    teams = []
    try:
        for country in SPORTSDB_COUNTRIES:
            query = urllib.parse.urlencode({"c": country, "s": "Cricket"})
            found = get_json(f"{SPORTSDB_API}search_all_leagues.php?{query}", "sportsdb", SPORTSDB_GAP_S)
            leagues |= {l.get("strLeague") for l in (found or {}).get("countries") or [] if l.get("strLeague")}
        for league in sorted(leagues):
            query = urllib.parse.urlencode({"l": league})
            found = get_json(f"{SPORTSDB_API}search_all_teams.php?{query}", "sportsdb", SPORTSDB_GAP_S)
            teams += (found or {}).get("teams") or []
    except FetchError as error:
        print(f"TheSportsDB listings incomplete ({error}); continuing with what was read")
    index = ts.sportsdb_index(teams)
    print(f"TheSportsDB: {len(leagues)} cricket leagues, {len(teams)} teams listed")
    return index


def is_fresh(entry, today):
    try:
        checked = datetime.date.fromisoformat(entry.get("checked", ""))
    except (TypeError, ValueError):
        return False
    return (today - checked).days < RECHECK_DAYS


def candidates(universe, logos, lookups, today):
    """Names worth a one-by-one search: national sides, then sides with no crest. Anything with a
    TheSportsDB entry already, or searched within RECHECK_DAYS, is skipped. A variant of a national
    side follows its parent, and a club the listings missed keeps the Cricbuzz crest it has."""
    due = [
        n for n in sorted(universe)
        if not (lookups.get(n) or {}).get("sportsdb") and not is_fresh(lookups.get(n) or {}, today)
    ]
    national = [n for n in due if is_national(n)]
    no_logo = [n for n in due if n not in logos and not is_national(n) and ts.flag_code(n) is None]
    return (national + no_logo)[:MAX_LOOKUPS]


def build_logo_ink(logos, inks, overrides):
    """The crests the app draws reversed on one plate, keyed by the URL TeamCrest draws rather than
    by team: a variant that kept its own Cricbuzz crest must not inherit its parent's reversal. An
    override names a team; it applies to that team's crest URL, and so to every variant sharing it."""
    logo_ink = {
        url: inks[url] for url in sorted(set(logos.values()))
        if not is_cricbuzz(url) and inks.get(url) in ("dark", "light")
    }
    for name, ink in overrides.items():
        url = logos.get(name)
        if url and not is_cricbuzz(url):
            logo_ink[url] = ink
    return logo_ink


def classify_crests(urls):
    """{url: ts.crest_ink(...)} for each crest that downloads; one that does not is left out, so it is
    neither reversed nor refused on this run's evidence."""
    import io
    from concurrent.futures import ThreadPoolExecutor

    from PIL import Image

    def one(url):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return url, ts.crest_ink(Image.open(io.BytesIO(response.read())))
        except (urllib.error.URLError, TimeoutError, OSError, ValueError):
            return url, "failed"

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = dict(pool.map(one, sorted(urls)))
    failed = sum(1 for ink in results.values() if ink == "failed")
    print(f"Crests checked against both plates: {len(results) - failed} ({failed} failed to download)")
    return {url: ink for url, ink in results.items() if ink != "failed"}


def is_cricbuzz(url):
    return bool(url) and "static.cricbuzz.com" in url


def sportsdb_sized(url):
    """TheSportsDB's 250px rendition (`/small`, ~40 KB): the full badge is 512px and ~100 KB, and the
    largest crest the app draws is 64dp. Its URLs take no other size, unlike Cricbuzz's."""
    if not url or "thesportsdb.com" not in url or url.endswith("/small"):
        return url
    return url + "/small"


def variant_logo(name, logos_by_normalized):
    base = ts.base_side(name)
    if base == ts.normalize(name):
        return None
    return logos_by_normalized.get(base)


def main():
    today = datetime.date.today()
    logos = load_json(LOGOS_PATH, {})
    colours = load_json(COLORS_PATH, {})
    lookups = load_json(LOOKUPS_PATH, {})
    if len(logos) < 500:
        print(f"Error: {LOGOS_PATH} has only {len(logos)} entries; run update_logos.py first")
        return 1

    universe = set(logos) | archive_team_names()
    bulk = sportsdb_bulk()
    matched = 0
    for name in universe:
        hit = bulk.get(ts.normalize(name))
        if hit:
            lookups[name] = {**lookups.get(name, {}), "checked": today.isoformat(), "sportsdb": hit}
            matched += 1
    print(f"{len(universe)} names, {matched} found in the listings")

    due = candidates(universe, logos, lookups, today)
    print(f"{len(due)} due a one-by-one search")
    errors = 0
    for count, name in enumerate(due, 1):
        try:
            hit = lookup_sportsdb(name)
        except FetchError as error:
            errors += 1
            print(f"Lookup failed for {name}: {error}")
            continue
        lookups[name] = {**lookups.get(name, {}), "checked": today.isoformat(), "sportsdb": hit}
        if count % SAVE_EVERY == 0:
            save_json(LOOKUPS_PATH, dict(sorted(lookups.items())), indent=1)
            print(f"  {count}/{len(due)} searched")

    if due and errors / len(due) > MAX_ERROR_SHARE:
        save_json(LOOKUPS_PATH, dict(sorted(lookups.items())), indent=1)
        print(f"Error: {errors} of {len(due)} lookups failed to connect; not writing {LOGOS_PATH}")
        return 1

    wikidata = wikidata_index()
    for name in universe:
        found = wikidata.get(ts.normalize(name))
        if found:
            lookups[name] = {**lookups.get(name, {}), "wikidata": found}
        elif name in lookups:
            lookups[name].pop("wikidata", None)
    print(f"Wikidata: {len(wikidata)} sides with a logo or colour, {sum(1 for n in universe if ts.normalize(n) in wikidata)} of ours")

    # A TheSportsDB badge beats a Cricbuzz crest. For a club or franchise Cricbuzz serves a simplified
    # mark on a coloured square (Mumbai Indians is the chakra alone) where TheSportsDB has the real
    # logo as a transparent PNG. For a national side Cricbuzz serves the flag and TheSportsDB the
    # board's crest (BCCI for India); NATIONAL_BOARD_CRESTS = False puts the flags back.
    # Each candidate crest is checked against TeamCrest's two plates first: one that vanishes on a
    # plate and cannot be drawn reversed (several colours) is not used at all.
    candidates_urls = set()
    for name in universe:
        entry = lookups.get(name) or {}
        candidates_urls.add(sportsdb_sized((entry.get("sportsdb") or {}).get("logo")))
        candidates_urls.add((entry.get("wikidata") or {}).get("logo"))
    inks = classify_crests(url for url in candidates_urls if url)
    unreadable = {url for url, ink in inks.items() if ink == "unreadable"}

    replaced = []
    for name in sorted(universe):
        badge = sportsdb_sized((lookups.get(name, {}).get("sportsdb") or {}).get("logo"))
        if not badge or badge in unreadable or not is_cricbuzz(logos.get(name)):
            continue
        if ts.flag_code(name) is not None and not NATIONAL_BOARD_CRESTS:
            continue
        logos[name] = badge
        replaced.append(name)

    # A national variant shows its parent's crest when the parent has a board crest, so India Women
    # and India U19 carry the BCCI crest with India rather than a flag beside it.
    logos_by_normalized = {}
    for logo_name, url in logos.items():
        logos_by_normalized.setdefault(ts.normalize(logo_name), url)
    if NATIONAL_BOARD_CRESTS:
        for name in sorted(universe):
            if ts.flag_code(name) is None or is_national(name) or not is_cricbuzz(logos.get(name)):
                continue
            parent = logos_by_normalized.get(ts.base_side(name))
            if parent and not is_cricbuzz(parent):
                logos[name] = parent
                replaced.append(name)

    added = {"sportsdb": [], "wikidata": [], "variant": []}
    for name in sorted(universe):
        if name in logos:
            continue
        entry = lookups.get(name) or {}
        for source, url in (
            ("sportsdb", sportsdb_sized((entry.get("sportsdb") or {}).get("logo"))),
            ("wikidata", (entry.get("wikidata") or {}).get("logo")),
            ("variant", variant_logo(name, logos_by_normalized)),
        ):
            if url and url not in unreadable:
                logos[name] = url
                added[source].append(name)
                break

    logo_ink = build_logo_ink(logos, inks, REVERSE_OVERRIDES)

    save_json(LOGOS_PATH, logos)
    save_json(INK_PATH, logo_ink)
    save_json(LOOKUPS_PATH, dict(sorted(lookups.items())), indent=1)

    hits = {
        "sportsdb": sum(1 for e in lookups.values() if e.get("sportsdb")),
        "sportsdb colours": sum(1 for e in lookups.values() if (e.get("sportsdb") or {}).get("colours")),
        "wikidata": sum(1 for e in lookups.values() if e.get("wikidata")),
    }
    print(f"{len(due) - errors} looked up, {errors} failed; cached hits: {hits}")
    print(f"Cricbuzz crests replaced by TheSportsDB badges: {len(replaced)}" + (
        f" ({', '.join(replaced[:10])}{'...' if len(replaced) > 10 else ''})" if replaced else ""))
    for source, names in added.items():
        shown = ", ".join(names[:10]) + ("..." if len(names) > 10 else "")
        print(f"Logos added from {source}: {len(names)}" + (f" ({shown})" if names else ""))
    reversed_teams = sorted(name for name, url in logos.items() if url in logo_ink)
    print(f"Crests drawn reversed: {len(logo_ink)}, for {', '.join(reversed_teams)}")
    print(f"Reversed by hand: {REVERSE_OVERRIDES}")
    print(f"Crests not used, unreadable on a plate: {len(unreadable)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
