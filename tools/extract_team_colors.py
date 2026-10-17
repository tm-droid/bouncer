"""Derive each team's colours from its crest and write `app/src/main/assets/TeamColors.json`.

Output: {"Team name": ["#RRGGBB", "#RRGGBB"]}, primary then secondary, for every team in
`TeamLogos.json` whose crest has a usable colour. `TeamColorsLoader` reads it at startup; the
hand-picked kit colours in `ui/theme/TeamPalettes.kt` always win over it, because a flag is often
not what a side plays in (Australia's flag is blue, its shirt gold).

How a colour is picked:

* The crest is fetched small (160x120 is plenty to count colours) and quantised to 4 bits a channel.
* Pixels that carry no identity are skipped: transparent, near-white (flag fields, letterboxing),
  near-black (outlines), and greys.
* Primary is the most common remaining bucket, averaged. Secondary is the most common bucket at least
  MIN_HUE_GAP degrees of hue away; failing that, the primary again.
* A crest whose primary covers less than MIN_COVERAGE of its pixels is a photo or a monochrome mark
  and gets no entry: neutral is better than a colour that identifies nobody.

Merge, never shrink — the rule `update_logos.py` follows. A crest that fails to download keeps last
run's colours. Run by `.github/workflows/daily_logo_update.yml` after the logo refresh.
"""

import colorsys
import io
import json
import re
import sys
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

LOGOS_PATH = "app/src/main/assets/TeamLogos.json"
COLORS_PATH = "app/src/main/assets/TeamColors.json"

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
WORKERS = 16
# A run that colours far fewer crests than this has hit a block page, and must not rewrite the asset.
MIN_EXPECTED_FETCHES = 300


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
    pixels = list(image.getdata())
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


def main():
    with open(LOGOS_PATH, encoding="utf-8") as f:
        logos = json.load(f)
    try:
        with open(COLORS_PATH, encoding="utf-8") as f:
            colours = json.load(f)
    except FileNotFoundError:
        colours = {}

    names = sorted(logos)
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        images = list(pool.map(lambda name: fetch(logos[name]), names))

    fetched = sum(1 for image in images if image is not None)
    if fetched < min(MIN_EXPECTED_FETCHES, len(names)):
        print(f"Only {fetched} of {len(names)} crests downloaded; not rewriting {COLORS_PATH}", file=sys.stderr)
        return 1

    coloured = 0
    for name, image in zip(names, images):
        if image is None:
            continue  # keep last run's entry, if any
        pair = colours_of(image)
        if pair is None:
            colours.pop(name, None)
        else:
            colours[name] = pair
            coloured += 1

    with open(COLORS_PATH, "w", encoding="utf-8") as f:
        json.dump(dict(sorted(colours.items())), f, indent=1, ensure_ascii=False)
        f.write("\n")
    print(f"{fetched} crests fetched, {coloured} coloured, {len(colours)} entries written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
