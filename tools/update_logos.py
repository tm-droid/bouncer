"""Refresh the bundled team branding assets from Cricbuzz.

Writes `app/src/main/assets/TeamLogos.json` (team name -> crest URL) and
`app/src/main/assets/TeamAcronyms.json` (team name -> short code).

This script used to read four `*_teams.json` dumps committed at the repo root and never touched the
network, so the daily workflow that ran it produced "No changes to commit" on every fire for five
months while Cricbuzz added 89 teams and changed 87 image ids. The dumps were one-shot RapidAPI
`teams/v1/*` responses; Cricbuzz's own team pages serve the same objects, with the same ids against
the same CDN, for no key and no quota.

Two rules, both learned from outages elsewhere in this project:

* **Merge, never shrink.** A team is only ever added or updated. 36 sides in the current asset are
  no longer listed by Cricbuzz, and a fixture from an old season still names them.
* **Fail loudly.** A truncated corpus must never overwrite a good one, and silence is what let the
  old script idle for five months.
"""

import json
import os
import re
import sys
import urllib.error
import urllib.request

TEAM_PAGES = [
    "https://www.cricbuzz.com/cricket-team",
    "https://www.cricbuzz.com/cricket-team/domestic",
    "https://www.cricbuzz.com/cricket-team/league",
    "https://www.cricbuzz.com/cricket-team/women",
]

LOGOS_PATH = "app/src/main/assets/TeamLogos.json"
ACRONYMS_PATH = "app/src/main/assets/TeamAcronyms.json"

USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
)

# Cricbuzz listed 975 teams across the four pages when this was written. A run that finds far fewer
# has hit a block page or a markup change, and must not be allowed to rewrite the assets.
MIN_EXPECTED_TEAMS = 500

# Mirrors cricbuzzTeamLogoUrl in app/src/main/kotlin/com/tanush/bouncer/network/CricbuzzImages.kt.
# The trailing filename is decorative — Cricbuzz serves the same bytes for any slug — and the size
# lives in the path. Keep the two in step: CricbuzzTeamLogoUrlParityTest compares them.
LOGO_URL = "https://static.cricbuzz.com/a/img/v1/720x540/i1/c{image_id}/i.jpg"


def unescape_flight(html):
    """The RSC flight stream as readable JSON text.

    Cricbuzz ships its page data inside `self.__next_f.push("...")` JS string literals, so every
    quote in the JSON arrives backslash-escaped.
    """
    return html.replace('\\\\"', "\x00").replace('\\"', '"')


def _match_bracket(text, open_idx):
    """Index of the `]` closing the `[` at open_idx, or -1. String-aware, so a `]` in a team name
    cannot end the array early."""
    depth = 0
    in_string = False
    escaped = False
    for i in range(open_idx, len(text)):
        char = text[i]
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
            if depth == 0:
                return i
    return -1


# A team object as Cricbuzz serialises it. Read by shape rather than by enclosing key: the team
# pages nest the real list under `teamsList` keyed by category ("League Teams"), while the short
# navigation list sits under `teamList`, and matching on the key found the nav list and stopped.
TEAM_OBJECT = re.compile(
    r'\{"teamId":(?P<team_id>\d+),'
    r'"teamName":"(?P<name>[^"]+)",'
    r'"teamSName":"(?P<short_name>[^"]*)",'
    r'"imageId":(?P<image_id>\d+)'
)


def parse_team_list(html):
    """Every team object the page carries, keyed by team name.

    The header rows Cricbuzz puts at the top of each list ({"teamName": "League Teams"}) carry no
    id and no image, so the shape match skips them for free.
    """
    text = unescape_flight(html)
    teams = {}
    for match in TEAM_OBJECT.finditer(text):
        teams[match.group("name")] = {
            "imageId": match.group("image_id"),
            "shortName": match.group("short_name"),
        }
    return teams


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=45) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status} from {url}")
        return response.read().decode("utf-8", "replace")


def load_json(path):
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def save_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def merge(existing, incoming, value_of):
    """Apply incoming values onto existing, adding and updating but never deleting.

    Returns (merged, added, updated). Existing key order is preserved so a diff shows only what
    actually changed.
    """
    merged = dict(existing)
    added = []
    updated = []
    for name, team in sorted(incoming.items()):
        value = value_of(team)
        if not value:
            continue
        if name not in merged:
            merged[name] = value
            added.append(name)
        elif merged[name] != value:
            merged[name] = value
            updated.append(name)
    return merged, added, updated


def main():
    teams = {}
    for url in TEAM_PAGES:
        try:
            page_teams = parse_team_list(fetch(url))
        except (urllib.error.URLError, RuntimeError, TimeoutError) as error:
            print(f"Error: failed to fetch {url}: {error}")
            sys.exit(1)
        print(f"{url}: {len(page_teams)} teams")
        teams.update(page_teams)

    print(f"Union: {len(teams)} teams")
    if len(teams) < MIN_EXPECTED_TEAMS:
        print(
            f"Error: only {len(teams)} teams found, expected at least {MIN_EXPECTED_TEAMS}. "
            "Refusing to overwrite the assets with a truncated corpus."
        )
        sys.exit(1)

    logos, logos_added, logos_updated = merge(
        load_json(LOGOS_PATH), teams, lambda t: LOGO_URL.format(image_id=t["imageId"])
    )
    acronyms, acronyms_added, acronyms_updated = merge(
        load_json(ACRONYMS_PATH), teams, lambda t: t["shortName"]
    )

    save_json(LOGOS_PATH, logos)
    save_json(ACRONYMS_PATH, acronyms)

    print(f"{LOGOS_PATH}: {len(logos)} entries ({len(logos_added)} added, {len(logos_updated)} updated)")
    print(f"{ACRONYMS_PATH}: {len(acronyms)} entries ({len(acronyms_added)} added, {len(acronyms_updated)} updated)")
    if logos_added:
        print("Added: " + ", ".join(logos_added[:10]) + ("..." if len(logos_added) > 10 else ""))


if __name__ == "__main__":
    main()
