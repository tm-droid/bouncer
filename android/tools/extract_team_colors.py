"""Each team's colours, from the best source that has them, into `app/src/main/assets/TeamColors.json`.

Output: {"Team name": ["#RRGGBB", "#RRGGBB"]}, primary then secondary. `TeamColorsLoader` reads it at
startup; the hand-picked kit colours in `ui/theme/TeamPalettes.kt` always win over it, because a flag
is often not what a side plays in (Australia's flag is blue, its shirt gold).

Sources, highest first; a lower one is used only when every higher one has nothing:

1. TheSportsDB's hand-entered kit colours (`strColour1..3`).
2. Wikidata's official colours (P6364). Both come from `tools/team_lookups.json`, which
   `update_logos_extra.py` fills; nothing is fetched for them here.
3. The crest, quantised (below).
4. The national flag, for a national side or any variant of one ("Nepal U19"), rasterised from the
   MIT-licensed flag-icons SVGs (`FLAG_ICONS_DIR`, the package's `flags/4x3`). Here white counts as a
   secondary colour, which England and Japan need.

How a crest's colour is picked:

* The crest is fetched small (160x120 is plenty to count colours) and quantised to 4 bits a channel.
* Pixels that carry no identity are skipped: transparent, near-white (flag fields, letterboxing),
  near-black (outlines), and greys.
* Primary is the most common remaining bucket, averaged. Secondary is the most common bucket at least
  MIN_HUE_GAP degrees of hue away; failing that, the primary again.
* A crest whose primary covers less than MIN_COVERAGE of its pixels is a photo or a monochrome mark
  and gets no entry: neutral is better than a colour that identifies nobody.

Merge, never shrink — the rule `update_logos.py` follows. A crest that fails to download keeps last
run's colours. Run by `.github/workflows/daily_logo_update.yml` after the logo lookups.
"""

import colorsys
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import team_sources as ts  # noqa: E402

LOGOS_PATH = "app/src/main/assets/TeamLogos.json"
COLORS_PATH = "app/src/main/assets/TeamColors.json"
LOOKUPS_PATH = "tools/team_lookups.json"
FLAG_ICONS_DIR = os.environ.get("FLAG_ICONS_DIR", "")

USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
)

SAMPLE_SIZE = "160x120"
MIN_COVERAGE = 0.04
MIN_SECONDARY_COVERAGE = 0.03
MIN_HUE_GAP = 40
MIN_SATURATION = 0.28
NEAR_WHITE = 0.92
NEAR_BLACK = 0.14
FLAG_SIZE = 120
# A flag's white is a colour (England, Japan) once it covers this much of the field.
MIN_FLAG_WHITE = 0.10
WORKERS = 16
# A run where most crest downloads fail has hit a block page, and must not rewrite the asset.
MIN_FETCH_SHARE = 0.5
MIN_ATTEMPTS_FOR_GUARD = 50


def fetch(url):
    small = re.sub(r"/a/img/v1/\d+x\d+/", f"/a/img/v1/{SAMPLE_SIZE}/", url)
    request = urllib.request.Request(small, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return Image.open(io.BytesIO(response.read())).convert("RGBA")
    except (urllib.error.URLError, OSError, ValueError):
        return None


def usable(r, g, b, a):
    if a < 128:
        return False
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    if l > NEAR_WHITE or l < NEAR_BLACK:
        return False
    return s >= MIN_SATURATION


def hue_of(rgb):
    return colorsys.rgb_to_hls(*(c / 255 for c in rgb))[0] * 360


def hue_gap(a, b):
    d = abs(hue_of(a) - hue_of(b)) % 360
    return min(d, 360 - d)


def to_hex(rgb):
    return "#{:02X}{:02X}{:02X}".format(*rgb)


def colours_of(image):
    pixels = list(image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata())
    buckets = Counter()
    sums = {}
    for r, g, b, a in pixels:
        if not usable(r, g, b, a):
            continue
        key = (r >> 4, g >> 4, b >> 4)
        buckets[key] += 1
        acc = sums.setdefault(key, [0, 0, 0])
        acc[0] += r
        acc[1] += g
        acc[2] += b
    if not buckets:
        return None
    total = len(pixels)

    def mean(key):
        n = buckets[key]
        return tuple(round(c / n) for c in sums[key])

    ranked = buckets.most_common()
    first_key, first_count = ranked[0]
    if first_count / total < MIN_COVERAGE:
        return None
    primary = mean(first_key)
    secondary = primary
    for key, count in ranked[1:]:
        if count / total < MIN_SECONDARY_COVERAGE:
            break
        candidate = mean(key)
        if hue_gap(primary, candidate) >= MIN_HUE_GAP:
            secondary = candidate
            break
    return [to_hex(primary), to_hex(secondary)]


def flag_colours(code):
    """A national flag's [primary, secondary], or None when the flag set is missing or has no file."""
    if not FLAG_ICONS_DIR:
        return None
    path = os.path.join(FLAG_ICONS_DIR, f"{code}.svg")
    if not os.path.isfile(path):
        return None
    import cairosvg  # only the flag tier needs it

    png = cairosvg.svg2png(url=path, output_width=FLAG_SIZE)
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    pair = colours_of(image)
    if pair is None:
        return None
    if pair[0] == pair[1]:
        pixels = list(image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata())
        white = sum(1 for r, g, b, a in pixels if a >= 128 and min(r, g, b) >= 235)
        if white / len(pixels) >= MIN_FLAG_WHITE:
            pair = [pair[0], "#FFFFFF"]
    return pair


def source_colours(entry, source):
    found = (entry or {}).get(source) or {}
    return [c for c in (ts.hex_or_none(c) for c in found.get("colours") or []) if c]


def main():
    with open(LOGOS_PATH, encoding="utf-8") as f:
        logos = json.load(f)
    try:
        with open(COLORS_PATH, encoding="utf-8") as f:
            colours = json.load(f)
    except FileNotFoundError:
        colours = {}
    try:
        with open(LOOKUPS_PATH, encoding="utf-8") as f:
            lookups = json.load(f)
    except FileNotFoundError:
        lookups = {}
    if not FLAG_ICONS_DIR:
        print("FLAG_ICONS_DIR is not set; the flag tier is skipped")

    names = sorted(set(logos) | set(lookups))
    chosen = {}
    for name in names:
        pair = ts.choose_colours(
            source_colours(lookups.get(name), "sportsdb"),
            source_colours(lookups.get(name), "wikidata"),
        )
        if pair:
            chosen[name] = ("source", pair)

    crest_names = [n for n in names if n not in chosen and n in logos]
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        images = list(pool.map(lambda name: fetch(logos[name]), crest_names))
    fetched = sum(1 for image in images if image is not None)
    if len(crest_names) >= MIN_ATTEMPTS_FOR_GUARD and fetched < len(crest_names) * MIN_FETCH_SHARE:
        print(f"Only {fetched} of {len(crest_names)} crests downloaded; not rewriting {COLORS_PATH}", file=sys.stderr)
        return 1

    unreachable = set()
    for name, image in zip(crest_names, images):
        if image is None:
            unreachable.add(name)
            continue
        pair = colours_of(image)
        if pair:
            chosen[name] = ("crest", pair)

    flag_cache = {}
    for name in names:
        if name in chosen:
            continue
        code = ts.flag_code(name)
        if code is None:
            continue
        if code not in flag_cache:
            flag_cache[code] = flag_colours(code)
        if flag_cache[code]:
            chosen[name] = ("flag", flag_cache[code])

    counts = Counter(source for source, _ in chosen.values())
    for name in names:
        if name in chosen:
            colours[name] = chosen[name][1]
        elif name not in unreachable and not (not FLAG_ICONS_DIR and ts.flag_code(name)):
            colours.pop(name, None)  # every source answered and none had a usable colour

    with open(COLORS_PATH, "w", encoding="utf-8") as f:
        json.dump(dict(sorted(colours.items())), f, indent=1, ensure_ascii=False)
        f.write("\n")
    print(
        f"{len(names)} names: {counts['source']} from TheSportsDB/Wikidata, {counts['crest']} from crests "
        f"({fetched} of {len(crest_names)} fetched), {counts['flag']} from flags; {len(colours)} entries written"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
