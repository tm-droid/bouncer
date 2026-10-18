"""Logos (and source colours) for the sides Cricbuzz's team pages don't give one.

Runs after `update_logos.py` in `.github/workflows/daily_logo_update.yml`. It fills gaps, and for a
club or franchise (not a national side) it replaces the Cricbuzz crest with TheSportsDB's badge, which
is the real logo where Cricbuzz's is a simplified mark on a coloured square.

Which names: every key of `TeamLogos.json`, plus both teams of every match the public archive holds
(`idx/m/YYYY-MM.json` on tm-droid.github.io/bouncer-archive). The archive half is every side Bouncer
has actually shown, which includes sides Cricbuzz's team pages never list.

Sources, per name, first hit wins for the logo:

1. TheSportsDB `searchteams.php` (free test key 123, ~30 requests a minute): `strBadge`, and the
   hand-entered kit colours `strColour1..3` — the one real team colour database that has cricket.
2. Wikidata, in one SPARQL query a run: a cricket side's logo (P154) as a Commons PNG thumbnail,
   and its official colours (P6364 -> P465). Thin for cricket, but one request.
3. A variant's parent side: "India Women", "Hong Kong U19" take the Cricbuzz crest of "India" and
   "Hong Kong", which for a national side is its flag. No network.

What each source found is kept in `tools/team_lookups.json` (committed), which `extract_team_colors.py`
reads for the source colours. A name is looked up on TheSportsDB again only after RECHECK_DAYS, and
at most MAX_LOOKUPS names a run, so the backlog clears over a few weeks rather than in one long job.

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

ARCHIVE = "https://tm-droid.github.io/bouncer-archive/"
SPORTSDB = "https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t={name}"
WIKIDATA_SPARQL = "https://query.wikidata.org/sparql?{query}"

# Wikimedia's policy asks for a User-Agent that names the tool and how to reach its owner.
USER_AGENT = "BouncerTeamLogos/1.0 (https://github.com/tm-droid/bouncer; tools/update_logos_extra.py)"

RECHECK_DAYS = 56
MAX_LOOKUPS = int(os.environ.get("MAX_LOOKUPS", "150"))
SPORTSDB_GAP_S = 2.1
# National sides (and their variants) take the board's crest from TheSportsDB (BCCI for India) over
# Cricbuzz's crest, which is their flag. False puts the flags back on the next run.
NATIONAL_BOARD_CRESTS = True
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


def is_fresh(entry, today):
    try:
        checked = datetime.date.fromisoformat(entry.get("checked", ""))
    except (TypeError, ValueError):
        return False
    return (today - checked).days < RECHECK_DAYS


def candidates(universe, logos, colours, lookups, today):
    """Names due a lookup: those with no logo first, then those with no colour; fresh ones skipped."""
    due = [n for n in sorted(universe) if not is_fresh(lookups.get(n, {}), today)]
    no_logo = [n for n in due if n not in logos]
    no_colour = [n for n in due if n in logos and n not in colours]
    return (no_logo + no_colour)[:MAX_LOOKUPS]


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
    due = candidates(universe, logos, colours, lookups, today)
    print(f"{len(universe)} names, {len(due)} due a lookup this run")

    errors = 0
    for name in due:
        entry = {"checked": today.isoformat()}
        try:
            entry["sportsdb"] = lookup_sportsdb(name)
        except FetchError as error:
            errors += 1
            print(f"Lookup failed for {name}: {error}")
            continue
        lookups[name] = {**lookups.get(name, {}), **entry}

    if due and errors / len(due) > MAX_ERROR_SHARE:
        print(f"Error: {errors} of {len(due)} lookups failed to connect; not writing anything")
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
    replaced = []
    for name in sorted(universe):
        badge = (lookups.get(name, {}).get("sportsdb") or {}).get("logo")
        if not badge or not is_cricbuzz(logos.get(name)):
            continue
        if ts.flag_code(name) is not None and not NATIONAL_BOARD_CRESTS:
            continue
        logos[name] = sportsdb_sized(badge)
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
            if url:
                logos[name] = url
                added[source].append(name)
                break

    save_json(LOGOS_PATH, logos)
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
