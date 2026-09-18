# Bouncer — Play Store promo video

A [Remotion](https://remotion.dev) project that renders the Google Play listing video for Bouncer.
The app screens are rebuilt in React from the Compose sources — `ColorTokensV2.kt`,
`TypographyV2.kt`, `HomeScreenV2`, `MatchDetailViewV2`, `CommentaryTabV2`, `PremiumScorecardTab`,
`BouncerGlanceWidget`, `notification_live_score.xml` — using the bundled IBM Plex Sans files and the
app's own launcher icon, so the ad shows the app as it ships.

## Output

- `BouncerAd` — 1920×1080, 30 fps, ~39 s, H.264. Landscape because that is what the Play Console
  recommends for the listing video (it autoplays muted; the SFX are a bonus for YouTube).
- `Stills/*` — 1080×2400 screens (Home, Overview, Commentary, Scorecard, Series, News, Widgets) at the
  phone's logical size, for fidelity checks against emulator screencaps. **Not** uploadable as
  listing screenshots: 1080×2400 is 9:20 and Play accepts only 9:16 or 16:9.
- `Screenshots/*` — `Shot-01-Home` … `Shot-07-Widgets`, 1080×1920 (9:16): the listing's phone
  screenshots, each a headline over the device frame. `npm run screenshots` renders all seven into
  `out/screenshots/` in upload order and fails if any file is not 1080×1920 or is over 8 MB.

## Commands

```bash
npm i
npm run dev                                   # Remotion Studio
npm run lint                                  # eslint + tsc
npx remotion render BouncerAd out/bouncer-playstore.mp4 --codec=h264 --crf=18
npm run screenshots                           # the 7 Play Store phone screenshots → out/screenshots/
npx remotion still Still-Home out/stills/Still-Home.png   # one full-size fidelity still
```

Upload the MP4 to YouTube (unlisted is fine) and paste the link into Play Console → Store listing →
Promo video.

## Layout

```
src/
  BouncerAd.tsx        TransitionSeries: Hook → AppTour → CTA
  Root.tsx             compositions + stills
  data/match.ts        the one fixture every scene reads (IND v AUS T20I chase)
  theme/               tokens.ts (colour/spacing, mirrors ColorTokensV2), type.ts (TypographyV2), fonts.ts
  app/                 the rebuilt screens: HomeScreen, MatchDetailScreen, SeriesNewsScreens,
                       AndroidHome (Live Update card), Phone frame, primitives, Material icon paths
  scenes/              Hook, AppTour (scenes 2–6 on one continuous phone), Cta
  ad/Headline.tsx      the right-column copy per beat
  ad/Screenshot.tsx    the 1080×1920 Play Store screenshot frame (headline + Phone); Thumbnail.tsx the YouTube still
scripts/
  render-screenshots.mjs   renders Shot-01…07 and checks Play's size rules
public/
  fonts/               IBM Plex Sans (copied from app/src/main/res/font)
  crests/              team flags (Cricbuzz, the same URLs TeamLogos.json points at)
  news/                cover images of real articles from the app's news-cache (static.cricbuzz.com)
  sfx/                 whoosh / whip / switch / ding from remotion.media
  icon.webp            ic_launcher_foreground
```

## Rules the screens follow

Same as the app's visual grammar in `CLAUDE.md`: rows split by hairline dividers rather than a card
each, gold only for interactive affordances and the wordmark, red only for LIVE, tabular numerals
on anything that ticks, no pulsing dot, no shimmer, no ambient loops. The Scorecard tab and the
win-probability hero use the legacy premium dialect because that is what the tab renders today.

Optional: drop `public/music.mp3` in and add an `<Audio>` to `BouncerAd.tsx`; no music is bundled.
The "Get it on Google Play" pill is a text pill in the app's style — swap in Google's official badge
asset if you want the trademarked version.
