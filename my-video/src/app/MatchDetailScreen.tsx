import React from "react";
import { colors, dp, legacy, radius, spacing, teamColors, withAlpha } from "../theme/tokens";
import { type } from "../theme/type";
import { Icon } from "./Icons";
import { Col, Crest, Divider, NavPill, RecentBallsStripV2, Row, Spacer, StatusBadgeV2, StatusBar, ballGlyphKindOf } from "./primitives";
import { commentary, match, scorecard, type ChaseState, type CommentaryRow } from "../data/match";

const IND = teamColors.IND.primary;
const AUS = teamColors.AUS.primary;

/** CenterAlignedTopAppBar on `surface`: back, "IND vs AUS", Share / Alarm / Notifications. */
export const DetailTopBar: React.FC = () => (
  <div style={{ height: dp(64), background: colors.surface, display: "flex", alignItems: "center", padding: `0 ${dp(4)}px`, boxSizing: "border-box", flexShrink: 0 }}>
    <div style={{ width: dp(48), height: dp(48), display: "flex", alignItems: "center", justifyContent: "center" }}>
      <Icon name="arrowBack" color={colors.onSurface} />
    </div>
    <span style={{ ...type.titleMedium, color: colors.onSurface, flex: 1, textAlign: "center" }}>
      {match.home.acronym} vs {match.away.acronym}
    </span>
    {(["share", "alarm", "notificationsOutlined"] as const).map((n) => (
      <div key={n} style={{ width: dp(48), height: dp(48), display: "flex", alignItems: "center", justifyContent: "center" }}>
        <Icon name={n} color={colors.onSurface} />
      </div>
    ))}
  </div>
);

const TeamScoreRow: React.FC<{ crest: string; acronym: string; score: string; overs: string; batting: boolean }> = ({ crest, acronym, score, overs, batting }) => (
  <Row style={{ width: "100%" }}>
    <Crest src={crest} size={24} circle />
    <Spacer w={8} />
    <span style={{ ...type.titleMedium, fontWeight: batting ? 700 : 400, color: colors.onSurface, flex: 1 }}>{acronym}</span>
    <span style={{ ...type.scoreMedium, color: colors.onSurface }}>{score}</span>
    <Spacer w={4} />
    <span style={{ ...type.numericSmall, color: colors.textSecondary }}>({overs})</span>
  </Row>
);

/** ScoreHeaderV2 — the fixed header above the pager. `flash` tints it with an event colour at 15%. */
export const ScoreHeaderV2: React.FC<{ state: ChaseState; flash?: { color: string; alpha: number }; ballStyle?: (i: number, n: number) => React.CSSProperties }> = ({
  state,
  flash,
  ballStyle,
}) => (
  <Col style={{ background: colors.surface, width: "100%", position: "relative", flexShrink: 0 }}>
    {flash ? <div style={{ position: "absolute", inset: 0, background: flash.color, opacity: flash.alpha, pointerEvents: "none" }} /> : null}
    <Col style={{ padding: `${dp(spacing.md)}px ${dp(spacing.lg)}px`, position: "relative" }}>
      <Row style={{ width: "100%" }}>
        <span style={{ ...type.bodySmall, color: colors.textSecondary, flex: 1 }}>
          {match.title} • {match.matchType}
        </span>
        <StatusBadgeV2 status="Live" />
      </Row>
      <Spacer h={12} />
      <TeamScoreRow crest={match.home.crest} acronym={match.home.acronym} score={state.score} overs={state.overs} batting />
      <Spacer h={8} />
      <TeamScoreRow crest={match.away.crest} acronym={match.away.acronym} score={match.awayScore} overs={match.awayOvers} batting={false} />
      <Spacer h={8} />
      <span style={{ ...type.bodySmall, fontWeight: 500, color: colors.textSecondary }}>{state.status}</span>
      <Spacer h={4} />
      <Row gap={16}>
        <span style={{ ...type.labelSmall, color: colors.textSecondary }}>CRR: {state.crr}</span>
        <span style={{ ...type.labelSmall, color: colors.textSecondary }}>RRR: {state.rrr}</span>
      </Row>
      <Spacer h={8} />
      <RecentBallsStripV2 balls={state.recentBalls} ballStyle={ballStyle} />
    </Col>
    <Divider />
  </Col>
);

export const DETAIL_TABS = ["Overview", "Scorecard", "Commentary", "Highlights", "Match Info", "AI"] as const;
const TAB_PAD = 16;

/** Widths of each tab label at labelLarge, measured roughly; indicator sits under the label plus padding. */
const TAB_W: Record<string, number> = { Overview: 61, Scorecard: 66, Commentary: 82, Highlights: 68, "Match Info": 70, AI: 16 };

export const tabOffset = (index: number) => {
  let x = TAB_PAD;
  for (let i = 0; i < index; i++) x += TAB_W[DETAIL_TABS[i]] + TAB_PAD * 2;
  return { x, w: TAB_W[DETAIL_TABS[index]] + TAB_PAD * 2 };
};

/**
 * ScrollableTabRow on `surface`, edgePadding 16. Selected tab is Bold in the batting team's brand
 * primary, and the SecondaryIndicator uses the same colour. `indicator` may be a fractional index
 * so a scene can slide it between tabs; `scrollX` shifts the row for the tabs past the edge.
 */
export const TabRowV2: React.FC<{ selected: number; indicator?: number; scrollX?: number }> = ({ selected, indicator = selected, scrollX = 0 }) => {
  const lo = Math.floor(indicator);
  const hi = Math.min(DETAIL_TABS.length - 1, lo + 1);
  const t = indicator - lo;
  const a = tabOffset(lo);
  const b = tabOffset(hi);
  const x = a.x + (b.x - a.x) * t;
  const w = a.w + (b.w - a.w) * t;
  return (
    <Col style={{ background: colors.surface, flexShrink: 0, overflow: "hidden" }}>
      <div style={{ position: "relative", height: dp(48), translate: `${-dp(scrollX)}px 0` }}>
        <Row style={{ height: dp(48), paddingLeft: dp(TAB_PAD) }}>
          {DETAIL_TABS.map((label, i) => (
            <div key={label} style={{ width: dp(TAB_W[label] + TAB_PAD * 2), height: dp(48), display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
              <span style={{ ...type.labelLarge, fontWeight: i === selected ? 700 : 400, color: i === selected ? IND : colors.textSecondary, whiteSpace: "nowrap" }}>{label}</span>
            </div>
          ))}
        </Row>
        <div style={{ position: "absolute", bottom: 0, left: dp(x), width: dp(w), height: dp(2), background: IND }} />
      </div>
      <Divider />
    </Col>
  );
};

/** BouncerCard: 12dp radius, surface fill, 1dp divider border, no elevation. */
export const BouncerCard: React.FC<React.PropsWithChildren<{ padding?: number; style?: React.CSSProperties }>> = ({ children, padding = 12, style }) => (
  <div style={{ background: colors.surface, borderRadius: dp(radius.medium), border: `${dp(1)}px solid ${colors.divider}`, padding: dp(padding), boxSizing: "border-box", ...style }}>
    {children}
  </div>
);

const SectionHeaderV2: React.FC<{ title: string }> = ({ title }) => (
  <div style={{ padding: `${dp(8)}px ${dp(16)}px`, marginLeft: dp(-16), marginRight: dp(-16) }}>
    <span style={{ ...type.titleSmall, fontWeight: 600, color: colors.onSurface }}>{title}</span>
  </div>
);

const CreaseRow: React.FC<{ name: string; runs: number; balls: number; fours: number; sixes: number; striker: boolean }> = ({ name, runs, balls, fours, sixes, striker }) => {
  const sr = balls === 0 ? "0.00" : ((runs / balls) * 100).toFixed(2);
  return (
    <Col style={{ padding: dp(12) }}>
      <Row style={{ width: "100%" }}>
        <span style={{ ...type.titleSmall, fontWeight: striker ? 700 : 500, color: colors.onSurface, flex: 1 }}>
          {name}
          {striker ? " *" : ""}
        </span>
        <span style={{ ...type.scoreMedium, color: colors.onSurface }}>
          {runs} ({balls})
        </span>
      </Row>
      <Spacer h={4} />
      <span style={{ ...type.numericSmall, color: colors.textSecondary }}>
        SR {sr} • 4s {fours} • 6s {sixes}
      </span>
    </Col>
  );
};

const Black = 900;

/** PremiumWinProbabilityHero — label row, three side labels, 14dp segmented bar, footer. */
export const WinProbabilityHero: React.FC<{ homeProb: number; swing?: number; crr: string; rrr: string }> = ({ homeProb, swing, crr, rrr }) => {
  const home = Math.max(6, Math.min(94, homeProb));
  const away = 100 - home;
  return (
    <div style={{ background: colors.surface, borderRadius: dp(12), border: `${dp(1)}px solid ${colors.divider}`, padding: dp(16), boxSizing: "border-box" }}>
      <Row style={{ width: "100%", justifyContent: "space-between" }}>
        <span style={{ fontFamily: type.labelSmall.fontFamily, fontSize: dp(11), fontWeight: Black, letterSpacing: dp(2), color: legacy.textSecondary }}>WIN PROBABILITY</span>
        <Row gap={6}>
          <div style={{ width: dp(6), height: dp(6), borderRadius: "50%", background: colors.live }} />
          <span style={{ fontFamily: type.labelSmall.fontFamily, fontSize: dp(10), fontWeight: Black, letterSpacing: dp(1.5), color: colors.live }}>LIVE</span>
        </Row>
      </Row>
      <Spacer h={16} />
      <Row style={{ width: "100%", alignItems: "flex-end" }}>
        <Col style={{ flex: 1, alignItems: "flex-start" }}>
          <span style={{ fontFamily: type.labelSmall.fontFamily, fontSize: dp(12), fontWeight: Black, letterSpacing: dp(1), color: IND }}>{match.home.name.toUpperCase()}</span>
          <span style={{ fontFamily: type.labelSmall.fontFamily, fontSize: dp(26), lineHeight: `${dp(32)}px`, fontWeight: Black, letterSpacing: dp(-0.5), color: legacy.textPrimary, fontVariantNumeric: "tabular-nums" }}>
            {Math.round(homeProb)}%
          </span>
        </Col>
        <Col style={{ flex: 1, alignItems: "flex-end" }}>
          <span style={{ fontFamily: type.labelSmall.fontFamily, fontSize: dp(12), fontWeight: Black, letterSpacing: dp(1), color: AUS }}>{match.away.name.toUpperCase()}</span>
          <span style={{ fontFamily: type.labelSmall.fontFamily, fontSize: dp(26), lineHeight: `${dp(32)}px`, fontWeight: Black, letterSpacing: dp(-0.5), color: legacy.textPrimary, fontVariantNumeric: "tabular-nums" }}>
            {Math.round(100 - homeProb)}%
          </span>
        </Col>
      </Row>
      <Spacer h={10} />
      <div style={{ display: "flex", width: "100%", height: dp(14), borderRadius: dp(7), overflow: "hidden" }}>
        <div style={{ flex: home, background: IND }} />
        <div style={{ flex: away, background: AUS }} />
      </div>
      <Spacer h={12} />
      <span style={{ fontFamily: type.labelSmall.fontFamily, fontSize: dp(11), fontWeight: 500, color: legacy.textMuted, whiteSpace: "pre" }}>
        CRR {crr}  ·  RRR {rrr}
        {swing !== undefined ? `  ·  ${match.home.acronym} ${swing >= 0 ? "+" : ""}${Math.round(swing)}%` : ""}
      </span>
    </div>
  );
};

/** OverviewTabV2 body — Last Wicket, At the Crease, Current Bowler, win probability. `scrollY` in dp. */
export const OverviewTab: React.FC<{ state: ChaseState; homeProb: number; swing?: number; scrollY?: number }> = ({ state, homeProb, swing, scrollY = 0 }) => (
  <Col style={{ padding: `${dp(12)}px ${dp(16)}px`, gap: dp(12), translate: `0 ${-dp(scrollY)}px`, background: colors.background }}>
    <BouncerCard>
      <span style={{ ...type.labelMedium, color: colors.textSecondary }}>Last Wicket</span>
      <Spacer h={4} />
      <span style={{ ...type.bodyMedium, fontWeight: 500, color: colors.onSurface }}>{match.lastWicket}</span>
    </BouncerCard>
    <Col>
      <SectionHeaderV2 title="At the Crease" />
      <BouncerCard padding={0}>
        <CreaseRow {...state.striker} striker />
        <Divider />
        <CreaseRow {...state.nonStriker} striker={false} />
      </BouncerCard>
    </Col>
    <Col>
      <SectionHeaderV2 title="Current Bowler" />
      <BouncerCard>
        <Row style={{ width: "100%" }}>
          <span style={{ ...type.titleSmall, color: colors.onSurface, flex: 1 }}>{state.bowler.name}</span>
          <span style={{ ...type.scoreMedium, color: colors.onSurface }}>
            {state.bowler.overs}-{state.bowler.maidens}-{state.bowler.runs}-{state.bowler.wickets}
          </span>
        </Row>
        <Spacer h={4} />
        <span style={{ ...type.bodySmall, color: colors.textSecondary }}>Econ: {econ(state.bowler.overs, state.bowler.runs)}</span>
      </BouncerCard>
    </Col>
    <WinProbabilityHero homeProb={homeProb} swing={swing} crr={state.crr} rrr={state.rrr} />
  </Col>
);

const econ = (overs: string, runs: number) => {
  const [o, b = "0"] = overs.split(".");
  const balls = Number(o) * 6 + Number(b);
  return balls === 0 ? "0.00" : ((runs / balls) * 6).toFixed(2);
};

/* ---------- Commentary ---------- */

/** BallPill.kt ResultPill: 24×24, radius 4, labelSmall Bold; coloured text on a 20% tint. */
const ResultPill: React.FC<{ result: string }> = ({ result }) => {
  const kind = ballGlyphKindOf(result);
  const fg =
    kind === "WICKET" ? colors.cricket.wicket : kind === "SIX" ? colors.cricket.six : kind === "FOUR" ? colors.cricket.four : kind === "EXTRA" ? colors.cricket.wide : kind === "DOT" ? colors.textSecondary : colors.onSurface;
  const bg = kind === "WICKET" || kind === "SIX" || kind === "FOUR" ? withAlpha(fg, 0.2) : kind === "EXTRA" ? withAlpha(fg, 0.15) : colors.surfaceVariant;
  const text = kind === "DOT" ? "·" : result;
  return (
    <div style={{ width: dp(24), height: dp(24), borderRadius: dp(4), background: bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
      <span style={{ ...type.labelSmall, fontWeight: 700, color: fg }}>{text}</span>
    </div>
  );
};

/** Over-header BallBadge: 22dp, radius 4, 20% tint with coloured text; dot/runs on surfaceVariant. */
const BallBadge: React.FC<{ ball: string }> = ({ ball }) => {
  const kind = ballGlyphKindOf(ball);
  const fg =
    kind === "WICKET" ? colors.cricket.wicket : kind === "SIX" ? colors.cricket.six : kind === "FOUR" ? colors.cricket.four : kind === "EXTRA" ? colors.cricket.wide : kind === "DOT" ? colors.textSecondary : colors.onSurface;
  const bg = kind === "DOT" || kind === "RUNS" ? colors.surfaceVariant : withAlpha(fg, 0.2);
  return (
    <div style={{ height: dp(22), minWidth: dp(22), padding: `0 ${dp(4)}px`, borderRadius: dp(4), background: bg, display: "flex", alignItems: "center", justifyContent: "center", boxSizing: "border-box" }}>
      <span style={{ ...type.labelSmall, fontWeight: 700, color: fg }}>{kind === "DOT" ? "·" : ball}</span>
    </div>
  );
};

const boldSpans = (text: string) =>
  text.split(/(<b>.*?<\/b>)/g).map((part, i) =>
    part.startsWith("<b>") ? (
      <span key={i} style={{ fontWeight: 700 }}>
        {part.slice(3, -4)}
      </span>
    ) : (
      <React.Fragment key={i}>{part}</React.Fragment>
    ),
  );

const CommentaryBlock: React.FC<{ row: CommentaryRow }> = ({ row }) => {
  if (row.kind === "over") {
    return (
      <Col style={{ padding: `${dp(8)}px ${dp(16)}px` }}>
        <Row style={{ width: "100%", justifyContent: "space-between" }}>
          <div style={{ borderRadius: dp(6), background: withAlpha(colors.primary, 0.15), padding: `${dp(4)}px ${dp(8)}px` }}>
            <span style={{ ...type.labelSmall, fontWeight: 700, color: colors.primaryText }}>Over {row.over}</span>
          </div>
          <span style={{ ...type.labelSmall, color: colors.textSecondary }}>
            {row.runs} run{row.runs === 1 ? "" : "s"}
          </span>
        </Row>
        <Spacer h={4} />
        <Row gap={4}>
          {row.balls.map((b, i) => (
            <BallBadge key={i} ball={b} />
          ))}
        </Row>
        <Divider color={withAlpha(colors.primary, 0.12)} style={{ marginTop: dp(8) }} />
      </Col>
    );
  }
  const kind = ballGlyphKindOf(row.result);
  const tint = kind === "WICKET" ? withAlpha(colors.live, 0.08) : kind === "FOUR" || kind === "SIX" ? withAlpha(colors.win, 0.08) : "transparent";
  return (
    <Col style={{ padding: `${dp(8)}px ${dp(16)}px`, background: tint }}>
      <Row style={{ alignItems: "flex-start", width: "100%" }}>
        <ResultPill result={row.result} />
        <Spacer w={4} />
        <div style={{ width: dp(36), height: dp(24), borderRadius: dp(4), background: colors.surfaceVariant, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
          <span style={{ ...type.labelSmall, fontWeight: 700, color: colors.onSurface }}>{row.label}</span>
        </div>
        <Spacer w={8} />
        <span style={{ ...type.bodySmall, color: colors.onSurface, flex: 1 }}>{boldSpans(row.text)}</span>
      </Row>
      <Divider style={{ marginTop: dp(8) }} />
    </Col>
  );
};

const FILTERS = ["All", "Wickets", "Boundaries", "Milestones"];

/**
 * CommentaryTabV2 — filter chips, then the feed newest-first. `from` selects how many of the
 * newest rows to hide (so a scene can insert them), `rowStyle` animates the visible ones.
 */
export const CommentaryTab: React.FC<{ hideNewest?: number; rowStyle?: (indexFromTop: number) => React.CSSProperties }> = ({ hideNewest = 0, rowStyle }) => {
  const rows = commentary.slice(hideNewest);
  return (
    <Col style={{ background: colors.background, width: "100%" }}>
      <Row gap={8} style={{ padding: `${dp(8)}px ${dp(16)}px` }}>
        {FILTERS.map((f, i) => {
          const on = i === 0;
          return (
            <div key={f} style={{ height: dp(32), padding: `0 ${dp(16)}px`, borderRadius: dp(8), background: colors.surfaceVariant, border: `${dp(1)}px solid ${on ? colors.primaryText : colors.border}`, display: "flex", alignItems: "center", boxSizing: "border-box" }}>
              <span style={{ ...type.labelLarge, color: on ? colors.primaryText : colors.onSurface }}>{f}</span>
            </div>
          );
        })}
      </Row>
      <Col style={{ paddingTop: dp(12) }}>
        <Divider />
        <div style={{ padding: `${dp(8)}px ${dp(16)}px` }}>
          <span style={{ ...type.labelMedium, color: colors.textSecondary }}>Innings 2</span>
        </div>
        <Divider />
        {rows.map((row, i) => (
          <div key={row.kind === "over" ? `o${row.over}` : row.label} style={rowStyle?.(i)}>
            <CommentaryBlock row={row} />
          </div>
        ))}
      </Col>
    </Col>
  );
};

/* ---------- Scorecard (legacy premium dialect) ---------- */

const GOLD = legacy.gold;
const cell = (w: number, align: "left" | "center" | "right" = "center"): React.CSSProperties => ({ flex: w, textAlign: align });
const F = type.labelSmall.fontFamily;

export const ScorecardTab: React.FC<{ rowStyle?: (i: number) => React.CSSProperties }> = ({ rowStyle }) => (
  <Col style={{ background: colors.background, width: "100%" }}>
    <Col style={{ padding: `${dp(16)}px 0`, alignItems: "center" }}>
      <span style={{ fontFamily: F, fontSize: dp(28), lineHeight: `${dp(34)}px`, fontWeight: 700, letterSpacing: dp(-0.5), color: legacy.textPrimary, fontVariantNumeric: "tabular-nums" }}>{scorecard.summary}</span>
      <Spacer h={4} />
      <span style={{ fontFamily: F, fontSize: dp(12), fontWeight: 500, letterSpacing: dp(1), color: GOLD }}>{scorecard.rates}</span>
    </Col>
    <div style={{ margin: `${dp(8)}px ${dp(16)}px`, borderRadius: dp(12), background: colors.surfaceVariant, border: `${dp(0.5)}px solid ${legacy.glassSurface}`, padding: dp(4), display: "flex", gap: dp(4) }}>
      {["Australia Inning", "India Inning"].map((label, i) => (
        <div key={label} style={{ flex: 1, height: dp(36), borderRadius: dp(8), background: i === 1 ? GOLD : "transparent", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <span style={{ fontFamily: F, fontSize: dp(13), fontWeight: i === 1 ? 700 : 400, color: i === 1 ? colors.onPrimary : legacy.textSecondary }}>{label}</span>
        </div>
      ))}
    </div>
    <Row style={{ padding: `${dp(12)}px ${dp(16)}px`, width: "100%", boxSizing: "border-box" }}>
      <div style={{ width: dp(2), height: dp(16), background: GOLD }} />
      <Spacer w={8} />
      <span style={{ fontFamily: F, fontSize: dp(18), fontWeight: 700, color: legacy.textPrimary, flex: 1 }}>Batting</span>
      <div style={{ background: colors.surfaceVariant, borderRadius: dp(4), border: `${dp(1)}px solid ${legacy.glassSurface}`, padding: `${dp(4)}px ${dp(8)}px` }}>
        <span style={{ fontFamily: F, fontSize: dp(10), fontWeight: 500, color: legacy.textMuted }}>Live</span>
      </div>
    </Row>
    <div style={{ margin: `0 ${dp(16)}px`, borderTopLeftRadius: dp(8), borderTopRightRadius: dp(8), background: legacy.glassSurface, padding: `${dp(8)}px ${dp(12)}px`, display: "flex" }}>
      {(["BATTER", "R", "B", "4s", "6s", "SR"] as const).map((h, i) => (
        <span key={h} style={{ fontFamily: F, fontSize: dp(10), fontWeight: 700, color: i === 5 ? GOLD : legacy.textMuted, ...cell(i === 0 ? 3 : i === 5 ? 1.2 : 1, i === 0 ? "left" : i === 5 ? "right" : "center") }}>
          {h}
        </span>
      ))}
    </div>
    {scorecard.batting.map((b, i) => (
      <div key={b.name} style={{ margin: `0 ${dp(16)}px`, position: "relative", ...rowStyle?.(i) }}>
        {!b.out ? <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: dp(2), background: GOLD }} /> : null}
        <div style={{ background: b.out ? colors.surfaceVariant : legacy.glassSurface, border: `${dp(0.5)}px solid ${legacy.glassSurface}`, padding: dp(12), display: "flex", alignItems: "center" }}>
          <Col style={{ flex: 3 }}>
            <span style={{ fontFamily: F, fontSize: dp(14), fontWeight: 700, color: legacy.textPrimary }}>
              {b.name}
              {b.out ? "" : " *"}
            </span>
            {b.out ? (
              <span style={{ fontFamily: F, fontSize: dp(10), fontStyle: "italic", color: legacy.textMuted }}>{b.how}</span>
            ) : (
              <span style={{ fontFamily: F, fontSize: dp(10), color: colors.win }}>Batting</span>
            )}
          </Col>
          <span style={{ fontFamily: F, fontSize: dp(14), fontWeight: 700, color: legacy.textPrimary, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.r}</span>
          <span style={{ fontFamily: F, fontSize: dp(12), color: legacy.textMuted, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.b}</span>
          <span style={{ fontFamily: F, fontSize: dp(12), color: legacy.textMuted, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.fours}</span>
          <span style={{ fontFamily: F, fontSize: dp(12), color: legacy.textMuted, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.sixes}</span>
          <span style={{ fontFamily: F, fontSize: dp(13), fontWeight: 700, color: GOLD, fontVariantNumeric: "tabular-nums", ...cell(1.2, "right") }}>{b.sr}</span>
        </div>
      </div>
    ))}
    <div style={{ margin: dp(16), borderRadius: dp(8), background: legacy.glassSurface, padding: dp(12), display: "flex", justifyContent: "space-between" }}>
      <span style={{ fontFamily: F, fontSize: dp(12), color: legacy.textMuted }}>{scorecard.extras}</span>
      <span style={{ fontFamily: F, fontSize: dp(12), fontWeight: 700, color: GOLD }}>{scorecard.crr}</span>
    </div>
    <Row style={{ padding: `${dp(12)}px ${dp(16)}px` }}>
      <div style={{ width: dp(2), height: dp(16), background: GOLD }} />
      <Spacer w={8} />
      <span style={{ fontFamily: F, fontSize: dp(18), fontWeight: 700, color: legacy.textPrimary }}>Fall of Wickets</span>
    </Row>
    <Row gap={8} style={{ padding: `0 ${dp(16)}px`, alignItems: "stretch" }}>
      {scorecard.fow.map((w) => (
        <div key={w.score} style={{ width: dp(140), flexShrink: 0, borderRadius: dp(12), background: colors.surfaceVariant, border: `${dp(0.5)}px solid ${legacy.glassSurface}`, padding: dp(12), boxSizing: "border-box" }}>
          <span style={{ fontFamily: F, fontSize: dp(10), color: legacy.textMuted, display: "block" }}>Wicket</span>
          <span style={{ fontFamily: F, fontSize: dp(18), fontWeight: 700, color: GOLD, display: "block", fontVariantNumeric: "tabular-nums" }}>{w.score}</span>
          <span style={{ fontFamily: F, fontSize: dp(10), color: legacy.textSecondary, display: "block", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{w.who}</span>
        </div>
      ))}
    </Row>
    <Row style={{ padding: `${dp(12)}px ${dp(16)}px` }}>
      <div style={{ width: dp(2), height: dp(16), background: GOLD }} />
      <Spacer w={8} />
      <span style={{ fontFamily: F, fontSize: dp(18), fontWeight: 700, color: legacy.textPrimary }}>Bowling</span>
    </Row>
    <div style={{ margin: `0 ${dp(16)}px`, borderTopLeftRadius: dp(8), borderTopRightRadius: dp(8), background: legacy.glassSurface, padding: `${dp(8)}px ${dp(12)}px`, display: "flex" }}>
      {(["BOWLER", "O", "M", "R", "W", "ECON"] as const).map((h, i) => (
        <span key={h} style={{ fontFamily: F, fontSize: dp(10), fontWeight: 700, color: legacy.textMuted, ...cell(i === 0 ? 3.5 : i === 5 ? 1.2 : 1, i === 0 ? "left" : i === 5 ? "right" : "center") }}>
          {h}
        </span>
      ))}
    </div>
    {scorecard.bowling.map((b, i) => (
      <div key={b.name} style={{ margin: `0 ${dp(16)}px`, background: colors.surfaceVariant, border: `${dp(0.5)}px solid ${legacy.glassSurface}`, padding: dp(12), display: "flex", alignItems: "center", ...rowStyle?.(scorecard.batting.length + i) }}>
        <span style={{ fontFamily: F, fontSize: dp(14), fontWeight: 700, color: legacy.textPrimary, ...cell(3.5, "left") }}>{b.name}</span>
        <span style={{ fontFamily: F, fontSize: dp(13), color: legacy.textPrimary, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.o}</span>
        <span style={{ fontFamily: F, fontSize: dp(12), color: legacy.textMuted, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.m}</span>
        <span style={{ fontFamily: F, fontSize: dp(12), color: legacy.textMuted, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.r}</span>
        <span style={{ fontFamily: F, fontSize: dp(14), fontWeight: 700, color: legacy.textPrimary, fontVariantNumeric: "tabular-nums", ...cell(1) }}>{b.w}</span>
        <span style={{ fontFamily: F, fontSize: dp(12), fontWeight: 700, color: GOLD, fontVariantNumeric: "tabular-nums", ...cell(1.2, "right") }}>{b.econ}</span>
      </div>
    ))}
  </Col>
);

/* ---------- Screen ---------- */

export const MatchDetailScreen: React.FC<{
  state: ChaseState;
  tab: number;
  indicator?: number;
  tabScrollX?: number;
  flash?: { color: string; alpha: number };
  ballStyle?: (i: number, n: number) => React.CSSProperties;
  children: React.ReactNode;
}> = ({ state, tab, indicator, tabScrollX, flash, ballStyle, children }) => (
  <Col style={{ width: "100%", height: "100%", background: colors.background }}>
    <StatusBar background={colors.surface} />
    <DetailTopBar />
    <ScoreHeaderV2 state={state} flash={flash} ballStyle={ballStyle} />
    <TabRowV2 selected={tab} indicator={indicator} scrollX={tabScrollX} />
    <div style={{ flex: 1, overflow: "hidden", position: "relative" }}>{children}</div>
    <NavPill background={colors.background} />
  </Col>
);
