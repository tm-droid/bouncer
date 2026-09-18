import React from "react";
import { colors, dp, spacing, teamColors, withAlpha } from "../theme/tokens";
import { type } from "../theme/type";
import { Icon } from "./Icons";
import { Col, Crest, Divider, NavPill, RecentBallsStripV2, Row, Spacer, StatusBadgeV2, StatusBar } from "./primitives";
import { match, otherMatches, type ChaseState } from "../data/match";

/** BouncerTopBarV2 — left-aligned wordmark on the page colour, actions in muted ink. Home hides the divider. */
export const TopBarV2: React.FC<{ badge?: number }> = ({ badge = 2 }) => (
  <div
    style={{
      height: dp(64),
      background: colors.topBar,
      display: "flex",
      alignItems: "center",
      padding: `0 ${dp(4)}px 0 ${dp(16)}px`,
      boxSizing: "border-box",
      flexShrink: 0,
    }}
  >
    <span style={{ ...type.titleLarge, fontSize: dp(22), lineHeight: `${dp(28)}px`, fontWeight: 700, letterSpacing: dp(-0.2), color: colors.onTopBar, flex: 1 }}>
      Bouncer
    </span>
    <div style={{ width: dp(48), height: dp(48), display: "flex", alignItems: "center", justifyContent: "center", position: "relative" }}>
      <Icon name="notificationsOutlined" color={colors.onTopBarMuted} />
      {badge > 0 ? (
        <div
          style={{
            position: "absolute",
            top: dp(8),
            right: dp(8),
            minWidth: dp(16),
            height: dp(16),
            borderRadius: dp(8),
            background: colors.live,
            color: "#FFFFFF",
            ...type.labelSmall,
            fontSize: dp(11),
            lineHeight: `${dp(16)}px`,
            padding: `0 ${dp(4)}px`,
            boxSizing: "border-box",
            textAlign: "center",
          }}
        >
          {badge}
        </div>
      ) : null}
    </div>
    <div style={{ width: dp(48), height: dp(48), display: "flex", alignItems: "center", justifyContent: "center" }}>
      <Icon name="search" color={colors.onTopBarMuted} />
    </div>
  </div>
);

const TABS = ["All", "Live", "Upcoming", "Recent"] as const;

/** HomeFilterBar — text tabs with a content-width gold indicator, trailing FilterList, then a hairline. */
export const HomeFilterBar: React.FC<{ selected?: number }> = ({ selected = 1 }) => (
  <Col style={{ background: colors.topBar, flexShrink: 0 }}>
    <Row style={{ height: dp(48), paddingLeft: dp(4) }}>
      <Row style={{ flex: 1 }}>
        {TABS.map((t, i) => (
          <div key={t} style={{ position: "relative", height: dp(48), display: "flex", alignItems: "center", padding: `0 ${dp(16)}px` }}>
            <span style={{ ...type.labelLarge, color: i === selected ? colors.primaryText : colors.textSecondary }}>{t}</span>
            {i === selected ? (
              <div
                style={{
                  position: "absolute",
                  left: dp(16),
                  right: dp(16),
                  bottom: 0,
                  height: dp(3),
                  borderTopLeftRadius: dp(3),
                  borderTopRightRadius: dp(3),
                  background: colors.primaryText,
                }}
              />
            ) : null}
          </div>
        ))}
      </Row>
      <div style={{ width: dp(48), height: dp(48), display: "flex", alignItems: "center", justifyContent: "center" }}>
        <Icon name="filterList" color={colors.textSecondary} />
      </div>
    </Row>
    <Divider />
  </Col>
);

type TeamRowProps = {
  crest: string;
  acronym: string;
  score: string;
  overs: string;
  batting: boolean;
  yetToBat?: boolean;
  live?: boolean;
};

/** TeamRowV2 — 20dp crest (Crop, no clip) + 8dp, acronym titleMedium, score right-aligned. */
export const TeamRowV2: React.FC<TeamRowProps> = ({ crest, acronym, score, overs, batting, yetToBat }) => (
  <Row style={{ width: "100%" }}>
    <Crest src={crest} size={20} />
    <Spacer w={8} />
    <span style={{ ...type.titleMedium, color: colors.onSurface, flex: 1, whiteSpace: "nowrap" }}>{acronym}</span>
    {score ? (
      <Row gap={4}>
        <span style={{ ...(batting ? type.scoreLarge : type.scoreMedium), fontWeight: batting ? 700 : 600, color: colors.onSurface }}>{score}</span>
        {overs && overs !== "0.0" ? <span style={{ ...type.numericSmall, color: colors.textSecondary }}>({overs})</span> : null}
      </Row>
    ) : yetToBat ? (
      <span style={{ ...type.bodyMedium, color: colors.textSecondary }}>Yet to bat</span>
    ) : null}
  </Row>
);

/** rememberSegmentColors — brand colour only when both sides are known; here IND and AUS both are. */
const segmentColors = (home: string, away: string) => ({
  home: teamColors[home]?.primary ?? withAlpha(colors.onSurface, 0.8),
  away: teamColors[away]?.primary ?? withAlpha(colors.textSecondary, 0.25),
});

/**
 * HeroMatchCard — the featured live match: full-bleed on `surface`, no radius, no shadow, same row
 * grammar as the list plus balls, run rates and the 4dp win-probability bar.
 */
export const HeroMatchCard: React.FC<{ state: ChaseState; winProb?: number; ballStyle?: (i: number, n: number) => React.CSSProperties }> = ({
  state,
  winProb,
  ballStyle,
}) => {
  const prob = winProb ?? state.winProb;
  const seg = segmentColors(match.home.acronym, match.away.acronym);
  return (
    <Col style={{ background: colors.surface, width: "100%" }}>
      <Col style={{ padding: `${dp(spacing.md)}px ${dp(spacing.lg)}px` }}>
        <Row style={{ width: "100%" }}>
          <span style={{ ...type.bodySmall, color: colors.textSecondary, flex: 1, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
            {match.headerText}
          </span>
          <Spacer w={8} />
          <StatusBadgeV2 status="Live" />
        </Row>
        <Spacer h={8} />
        <TeamRowV2 crest={match.home.crest} acronym={match.home.acronym} score={state.score} overs={state.overs} batting live />
        <Spacer h={8} />
        <TeamRowV2 crest={match.away.crest} acronym={match.away.acronym} score={match.awayScore} overs={match.awayOvers} batting={false} />
        <Spacer h={8} />
        <span style={{ ...type.bodyMedium, color: colors.onSurface }}>{state.status}</span>
        <Spacer h={8} />
        <RecentBallsStripV2 balls={state.recentBalls} ballStyle={ballStyle} />
        <Spacer h={8} />
        <Row gap={16}>
          <span style={{ ...type.numericSmall, color: colors.textSecondary }}>CRR {state.crr}</span>
          <span style={{ ...type.numericSmall, color: colors.textSecondary }}>RRR {state.rrr}</span>
        </Row>
        <Spacer h={12} />
        <Row style={{ width: "100%" }}>
          <span style={{ ...type.numericSmall, color: colors.textSecondary }}>
            {match.home.acronym} {Math.round(prob)}%
          </span>
          <div style={{ flex: 1 }} />
          <span style={{ ...type.numericSmall, color: colors.textSecondary }}>
            {Math.round(100 - prob)}% {match.away.acronym}
          </span>
        </Row>
        <Spacer h={4} />
        <div
          style={{
            width: "100%",
            height: dp(4),
            borderRadius: dp(2),
            background: `linear-gradient(to right, ${seg.home} ${prob}%, ${seg.away} ${prob}%)`,
          }}
        />
      </Col>
      <Divider />
    </Col>
  );
};

/** MatchCardV2 — not a card: padded column, full-bleed divider. */
export const MatchRowV2: React.FC<{ row: (typeof otherMatches)[number]; starred?: boolean }> = ({ row, starred }) => (
  <Col style={{ width: "100%" }}>
    <Col style={{ padding: `${dp(spacing.rowVertical)}px ${dp(spacing.lg)}px` }}>
      <Row style={{ height: dp(28), width: "100%" }}>
        {starred ? <Icon name="star" size={14} color={colors.primaryText} style={{ marginRight: dp(2) }} /> : null}
        <span style={{ ...type.bodySmall, color: colors.textSecondary, flex: 1, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
          {row.headerText}
        </span>
        {row.live ? (
          <>
            <Spacer w={8} />
            <StatusBadgeV2 status="Live" />
          </>
        ) : null}
        {row.status.startsWith("Australia Women won") ? null : (
          <div style={{ width: dp(28), height: dp(28), marginLeft: dp(4), display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Icon name="notificationsOutlined" size={16} color={colors.textSecondary} />
          </div>
        )}
      </Row>
      <Spacer h={8} />
      <TeamRowV2 crest={row.teams[0].crest} acronym={row.teams[0].acronym} score={row.teams[0].score} overs={row.teams[0].overs} batting={row.teams[0].batting} />
      <Spacer h={8} />
      <TeamRowV2 crest={row.teams[1].crest} acronym={row.teams[1].acronym} score={row.teams[1].score} overs={row.teams[1].overs} batting={row.teams[1].batting} />
      <Spacer h={8} />
      <span style={{ ...type.bodySmall, color: colors.textSecondary }}>{row.status}</span>
    </Col>
    <Divider />
  </Col>
);

const NAV = [
  { label: "Home", icon: "home", outlined: "homeOutlined" },
  { label: "Series", icon: "dateRange", outlined: "dateRange" },
  { label: "News", icon: "newspaper", outlined: "newspaper" },
  { label: "More", icon: "moreHoriz", outlined: "moreHoriz" },
] as const;

/** BottomNavBarV2 — surface fill, gold selected ink, no indicator pill. */
export const BottomNavV2: React.FC<{ selected?: number }> = ({ selected = 0 }) => (
  <Col style={{ background: colors.surface, flexShrink: 0 }}>
    <Row style={{ height: dp(80), width: "100%" }}>
      {NAV.map((n, i) => {
        const on = i === selected;
        const color = on ? colors.primaryText : colors.textSecondary;
        return (
          <Col key={n.label} style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: dp(4) }}>
            <div style={{ height: dp(32), display: "flex", alignItems: "center" }}>
              <Icon name={on ? n.icon : n.outlined} color={color} />
            </div>
            <span style={{ ...type.labelMedium, color }}>{n.label}</span>
          </Col>
        );
      })}
    </Row>
    <NavPill background={colors.surface} />
  </Col>
);

/**
 * HomeScreenV2 — status bar, top bar, filter tabs, hero, rows, "All matches", bottom nav.
 * `rowStyle` lets a scene stagger the rows in.
 */
export const HomeScreen: React.FC<{
  state: ChaseState;
  winProb?: number;
  ballStyle?: (i: number, n: number) => React.CSSProperties;
  rowStyle?: (i: number) => React.CSSProperties;
  heroStyle?: React.CSSProperties;
}> = ({ state, winProb, ballStyle, rowStyle, heroStyle }) => (
  <Col style={{ width: "100%", height: "100%", background: colors.background }}>
    <StatusBar />
    <TopBarV2 />
    <HomeFilterBar selected={1} />
    <Col style={{ flex: 1, overflow: "hidden" }}>
      <div style={heroStyle}>
        <HeroMatchCard state={state} winProb={winProb} ballStyle={ballStyle} />
      </div>
      {otherMatches.map((row, i) => (
        <div key={row.headerText} style={rowStyle?.(i)}>
          <MatchRowV2 row={row} starred={i === 0} />
        </div>
      ))}
      <Spacer h={8} />
      <div style={{ padding: `${dp(12)}px ${dp(16)}px`, ...rowStyle?.(otherMatches.length) }}>
        <span style={{ ...type.labelLarge, color: colors.primaryText }}>All matches</span>
      </div>
    </Col>
    <BottomNavV2 selected={0} />
  </Col>
);
