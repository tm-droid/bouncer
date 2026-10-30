package com.tanush.bouncer.network

/**
 * Cricbuzz CDN image URLs, built from the image ids its own payloads carry.
 *
 * The app used to hold team crests only as pre-baked URLs in `assets/TeamLogos.json`, regenerated
 * by a daily job. A bundled map cannot know a team Cricbuzz added after it was generated, and it
 * keeps pointing at a stale image for any team whose id Cricbuzz has since changed — which is why
 * the crest is now read from the match payload first and the map is only the fallback.
 */

/** The size the bundled map already uses, so a payload URL and a mapped URL are interchangeable. */
private const val TEAM_LOGO_WIDTH = 720
private const val TEAM_LOGO_HEIGHT = 540

/**
 * The team crest for [imageId].
 *
 * The trailing filename is decorative: Cricbuzz serves byte-identical responses for
 * `/c776162/india.jpg` and `/c776162/zzzz.jpg`, so it is a constant rather than a slugified team
 * name — one less thing to keep in step with a name that arrives as a short code half the time.
 * The size lives in the path, not a query parameter, which is why [TeamLogoManager] can rewrite it.
 */
internal fun cricbuzzTeamLogoUrl(
    imageId: String,
    width: Int = TEAM_LOGO_WIDTH,
    height: Int = TEAM_LOGO_HEIGHT,
): String = "https://static.cricbuzz.com/a/img/v1/${width}x${height}/i1/c$imageId/i.jpg"

/**
 * Cricbuzz's grey placeholder, which a team with no crest of its own carries (49 sides in October
 * 2026, Jamaica and Guyana among them). Drawn, it is a grey box where the team's monogram belongs.
 * `tools/update_logos.py` drops it from `TeamLogos.json` by the same id.
 */
internal const val CRICBUZZ_PLACEHOLDER_IMAGE_ID = "174284"

/** True for a Cricbuzz crest URL that is the placeholder rather than a crest. */
internal fun isCricbuzzPlaceholderLogo(url: String): Boolean =
    url.contains("static.cricbuzz.com") && url.contains("/c$CRICBUZZ_PLACEHOLDER_IMAGE_ID/")
