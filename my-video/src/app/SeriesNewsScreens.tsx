import React from "react";
import { Img, staticFile } from "remotion";
import { colors, dp, radius, spacing } from "../theme/tokens";
import { type } from "../theme/type";
import { Icon } from "./Icons";
import { Col, Divider, Row, Spacer, StatusBar } from "./primitives";
import { BottomNavV2 } from "./HomeScreen";

/** BouncerTopBarV2 with a plain title (Series / News tabs), divider on. */
const TitleBar: React.FC<{ title: string }> = ({ title }) => (
  <Col style={{ flexShrink: 0 }}>
    <div style={{ height: dp(64), background: colors.topBar, display: "flex", alignItems: "center", padding: `0 ${dp(16)}px`, boxSizing: "border-box" }}>
      <span style={{ ...type.titleLarge, fontSize: dp(22), lineHeight: `${dp(28)}px`, fontWeight: 700, letterSpacing: dp(-0.2), color: colors.onTopBar }}>{title}</span>
    </div>
    <Divider />
  </Col>
);

/** FilterChipRowV2 — M3 FilterChip on surfaceVariant; selected = gold label + gold border. */
const FilterChips: React.FC<{ items: string[]; selected?: number }> = ({ items, selected = 0 }) => (
  <Row gap={8} style={{ padding: `${dp(8)}px ${dp(16)}px`, overflow: "hidden", flexShrink: 0, height: dp(48), boxSizing: "border-box" }}>
    {items.map((f, i) => {
      const on = i === selected;
      return (
        <div key={f} style={{ height: dp(32), padding: `0 ${dp(16)}px`, borderRadius: dp(8), background: colors.surfaceVariant, border: `${dp(1)}px solid ${on ? colors.primaryText : colors.border}`, display: "flex", alignItems: "center", boxSizing: "border-box", flexShrink: 0 }}>
          <span style={{ ...type.labelLarge, color: on ? colors.primaryText : colors.onSurface, whiteSpace: "nowrap" }}>{f}</span>
        </div>
      );
    })}
  </Row>
);

export const seriesList = [
  { name: "Australia tour of India, 2026", dates: "12 Sep 2026 – 26 Sep 2026", label: "International" },
  { name: "England tour of Pakistan, 2026", dates: "4 Sep 2026 – 3 Oct 2026", label: "International" },
  { name: "ICC Women's T20 World Cup 2026", dates: "1 Sep 2026 – 18 Sep 2026", label: "International · Women" },
  { name: "County Championship Division One 2026", dates: "3 Apr 2026 – 26 Sep 2026", label: "Domestic" },
  { name: "Caribbean Premier League 2026", dates: "20 Aug 2026 – 21 Sep 2026", label: "League" },
  { name: "New Zealand tour of South Africa, 2026", dates: "19 Sep 2026 – 8 Oct 2026", label: "International" },
  { name: "CSA Four-Day Series Division One 2026-27", dates: "15 Sep 2026 – 22 Feb 2027", label: "Domestic" },
];

/** SeriesCard — M3 Card on surface, 12dp radius, 40dp icon tile on primaryContainer, name / dates / category. */
const SeriesCard: React.FC<{ s: (typeof seriesList)[number] }> = ({ s }) => (
  <div style={{ margin: `${dp(spacing.xs)}px ${dp(spacing.lg)}px`, background: colors.surface, borderRadius: dp(radius.medium), padding: dp(spacing.lg), display: "flex", alignItems: "center", boxSizing: "border-box" }}>
    <div style={{ width: dp(40), height: dp(40), borderRadius: dp(8), background: colors.primaryContainer, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
      <Icon name="dateRange" size={22} color={colors.primaryText} />
    </div>
    <Spacer w={12} />
    <Col style={{ flex: 1, minWidth: 0 }}>
      <span style={{ ...type.bodyLarge, fontWeight: 500, color: colors.onSurface }}>{s.name}</span>
      <span style={{ ...type.bodySmall, color: colors.textSecondary }}>{s.dates}</span>
      <span style={{ ...type.labelSmall, color: colors.primaryText }}>{s.label}</span>
    </Col>
    <svg width={dp(24)} height={dp(24)} viewBox="0 0 24 24" style={{ flexShrink: 0 }}>
      <path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z" fill={colors.textSecondary} />
    </svg>
  </div>
);

/** SeriesListScreen — the Series tab. */
export const SeriesScreen: React.FC<{ rowStyle?: (i: number) => React.CSSProperties }> = ({ rowStyle }) => (
  <Col style={{ width: "100%", height: "100%", background: colors.background }}>
    <StatusBar />
    <TitleBar title="Series" />
    <Col style={{ flex: 1, overflow: "hidden" }}>
      <FilterChips items={["All", "International", "League", "Domestic", "Women"]} />
      <Spacer h={8} />
      {seriesList.map((s, i) => (
        <div key={s.name} style={rowStyle?.(i)}>
          <SeriesCard s={s} />
        </div>
      ))}
    </Col>
    <BottomNavV2 selected={1} />
  </Col>
);

/**
 * Real articles from the app's own feed — the `news-cache` file `bouncer-news-sync` writes to the
 * `cricket-stats` bucket, captured 2026-09-18 — with their Cricbuzz cover images saved under
 * public/news/{imageId}.jpg, the same `static.cricbuzz.com` assets the app loads.
 */
export const newsList = [
  { headline: "Shree Charani spins India into the semifinals", source: "Cricbuzz", time: "2h ago", image: "news/1078727.jpg" },
  { headline: "BCCI office bearers authorised to decide on selection committee amid Agarkar uncertainty", source: "Cricbuzz", time: "1h ago", image: "news/1078794.jpg" },
  { headline: "Athapaththu, Dilhari fire Sri Lanka into Asian Games semifinal", source: "Cricbuzz", time: "4h ago", image: "news/1078681.jpg" },
  { headline: "Falcons storm into CPL final after Shadab, Moqim spin web around Warriors", source: "Cricbuzz", time: "4h ago", image: "news/1078680.jpg" },
  { headline: "Gambhir outlines Hardik's return path, backs Nitish for ODI role", source: "Cricbuzz", time: "5h ago", image: "news/1078661.jpg" },
];

/** FeaturedNewsCard — BouncerCard, 180dp image, headlineMedium, "source • time". */
const FeaturedNewsCard: React.FC<{ n: (typeof newsList)[number] }> = ({ n }) => (
  <div style={{ margin: `0 ${dp(spacing.lg)}px`, background: colors.surface, borderRadius: dp(radius.medium), border: `${dp(1)}px solid ${colors.divider}`, overflow: "hidden" }}>
    <Img src={staticFile(n.image)} style={{ width: "100%", height: dp(180), objectFit: "cover", display: "block" }} />
    <Col style={{ padding: dp(spacing.lg) }}>
      <span style={{ ...type.headlineMedium, color: colors.onSurface }}>{n.headline}</span>
      <Spacer h={4} />
      <span style={{ ...type.bodySmall, color: colors.textSecondary }}>
        {n.source} • {n.time}
      </span>
    </Col>
  </div>
);

/** NewsCardV2 — BouncerCard row: 72dp image, titleSmall headline, "source • time". */
const NewsCard: React.FC<{ n: (typeof newsList)[number] }> = ({ n }) => (
  <div style={{ margin: `0 ${dp(spacing.lg)}px`, background: colors.surface, borderRadius: dp(radius.medium), border: `${dp(1)}px solid ${colors.divider}`, padding: dp(spacing.md), display: "flex", alignItems: "center", boxSizing: "border-box" }}>
    <Img src={staticFile(n.image)} style={{ width: dp(72), height: dp(72), flexShrink: 0, objectFit: "cover", display: "block" }} />
    <Spacer w={12} />
    <Col style={{ flex: 1, minWidth: 0 }}>
      <span style={{ ...type.titleSmall, color: colors.onSurface }}>{n.headline}</span>
      <Spacer h={4} />
      <span style={{ ...type.bodySmall, color: colors.textSecondary }}>
        {n.source} • {n.time}
      </span>
    </Col>
  </div>
);

/** NewsScreenV2 — the News tab: featured card, then rows, 8dp apart, 12dp vertical padding. */
export const NewsScreen: React.FC<{ rowStyle?: (i: number) => React.CSSProperties }> = ({ rowStyle }) => (
  <Col style={{ width: "100%", height: "100%", background: colors.background }}>
    <StatusBar />
    <TitleBar title="News" />
    <Col style={{ flex: 1, overflow: "hidden", padding: `${dp(12)}px 0`, gap: dp(8) }}>
      <div style={rowStyle?.(0)}>
        <FeaturedNewsCard n={newsList[0]} />
      </div>
      {newsList.slice(1).map((n, i) => (
        <div key={n.headline} style={rowStyle?.(i + 1)}>
          <NewsCard n={n} />
        </div>
      ))}
    </Col>
    <BottomNavV2 selected={2} />
  </Col>
);
