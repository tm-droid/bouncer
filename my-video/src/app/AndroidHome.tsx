import React from "react";
import { Img, staticFile } from "remotion";
import { dp, notif, teamSurface, withAlpha } from "../theme/tokens";
import { type } from "../theme/type";
import { Col, Row, Spacer, Crest, Divider } from "./primitives";
import { match, type ChaseState } from "../data/match";

const F = type.bodyMedium.fontFamily;
const t = (size: number, weight: 400 | 500 | 700 = 400, color: string, extra: React.CSSProperties = {}): React.CSSProperties => ({
  fontFamily: F,
  fontSize: dp(size),
  lineHeight: `${dp(Math.round(size * 1.3))}px`,
  fontWeight: weight,
  color,
  ...extra,
});

/**
 * The Live Update card: notification_live_score.xml (expanded RemoteViews) inside the system shade's
 * notification frame, filled with the batting team's TeamColorRegistry surface.
 */
export const LiveUpdateCard: React.FC<{ state: ChaseState }> = ({ state }) => {
  const fill = teamSurface(match.home.acronym);
  const striker = state.striker;
  const non = state.nonStriker;
  return (
    <Col style={{ width: "100%", background: fill, borderRadius: dp(28), padding: `${dp(12)}px ${dp(16)}px ${dp(12)}px`, boxSizing: "border-box" }}>
      <Row style={{ width: "100%" }}>
        <Img src={staticFile("icon.webp")} style={{ width: dp(20), height: dp(20), borderRadius: "50%", background: "#000" }} />
        <Spacer w={8} />
        <span style={t(12, 400, notif.textSecondary)}>Bouncer · {match.seriesName} · {match.matchType}</span>
        <div style={{ flex: 1 }} />
        <span style={t(12, 400, notif.textSecondary)}>now</span>
      </Row>
      <Spacer h={10} />
      <Col style={{ padding: `${dp(2)}px ${dp(4)}px ${dp(4)}px` }}>
        <Row style={{ width: "100%" }}>
          <Crest src={match.home.crest} size={34} />
          <Col style={{ flex: 1, marginLeft: dp(10) }}>
            <span style={t(11, 700, notif.textSecondary, { letterSpacing: "0.08em" })}>{match.home.acronym}</span>
            <span style={{ ...t(24, 700, notif.textPrimary), fontVariantNumeric: "tabular-nums" }}>{state.score}</span>
            <span style={{ ...t(11, 400, notif.textSecondary), fontVariantNumeric: "tabular-nums" }}>{state.overs} ov</span>
          </Col>
          <Col style={{ alignItems: "flex-end" }}>
            <span style={t(11, 700, notif.textSecondary, { letterSpacing: "0.08em" })}>{match.away.acronym}</span>
            <span style={{ ...t(16, 400, notif.textSecondary), fontVariantNumeric: "tabular-nums" }}>{match.awayScore}</span>
          </Col>
          <div style={{ marginLeft: dp(8) }}>
            <Crest src={match.away.crest} size={24} />
          </div>
        </Row>
        <Divider color={notif.divider} style={{ marginTop: dp(10), marginBottom: dp(8) }} />
        <Row style={{ width: "100%" }}>
          <span style={{ ...t(14, 400, notif.textPrimary), flex: 1 }}>• {striker.name}</span>
          <span style={{ ...t(14, 700, notif.textPrimary), fontVariantNumeric: "tabular-nums", marginLeft: dp(8) }}>
            {striker.runs} ({striker.balls})
          </span>
        </Row>
        <Row style={{ width: "100%", marginTop: dp(2) }}>
          <span style={{ ...t(14, 400, notif.textSecondary), flex: 1 }}>{non.name}</span>
          <span style={{ ...t(14, 400, notif.textSecondary), fontVariantNumeric: "tabular-nums", marginLeft: dp(8) }}>
            {non.runs} ({non.balls})
          </span>
        </Row>
        <Row style={{ width: "100%", marginTop: dp(6) }}>
          <span style={{ ...t(13, 400, notif.textSecondary), flex: 1 }}>{state.bowler.name}</span>
          <span style={{ ...t(13, 400, notif.textSecondary), fontVariantNumeric: "tabular-nums", marginLeft: dp(8) }}>
            {state.bowler.wickets}/{state.bowler.runs} ({state.bowler.overs})
          </span>
        </Row>
        <span style={{ ...t(13, 400, notif.textSecondary), letterSpacing: "0.14em", marginTop: dp(8), fontVariantNumeric: "tabular-nums", whiteSpace: "pre" }}>
          {state.recentBalls.slice(-6).join("  ")}
        </span>
        <span style={{ ...t(13, 700, notif.textPrimary), marginTop: dp(6) }}>
          Need {state.need} runs from {Math.floor(state.ballsLeft / 6)}.{state.ballsLeft % 6} overs · RRR {state.rrr}
        </span>
      </Col>
      <Spacer h={10} />
      <Row gap={8}>
        {["Scorecard", "Stop"].map((a) => (
          <div key={a} style={{ borderRadius: dp(18), background: withAlpha("#FFFFFF", 0.1), padding: `${dp(7)}px ${dp(16)}px` }}>
            <span style={t(13, 500, notif.textPrimary)}>{a}</span>
          </div>
        ))}
      </Row>
    </Col>
  );
};

/** A plain Android launcher home: dark wallpaper and the clock. The Live Update card drops in over it. */
export const AndroidHome: React.FC<{ children?: React.ReactNode }> = ({ children }) => (
  <div style={{ width: "100%", height: "100%", position: "relative", background: "radial-gradient(120% 80% at 50% 0%, #1B2230 0%, #0B0F14 60%, #06080B 100%)" }}>
    <Col style={{ position: "absolute", inset: 0, padding: `${dp(28)}px ${dp(20)}px 0`, boxSizing: "border-box" }}>
      <Spacer h={20} />
      <span style={{ ...t(56, 400, "#F1F3F6"), letterSpacing: dp(-1), fontVariantNumeric: "tabular-nums" }}>19:42</span>
      <span style={t(16, 400, "#B7BDC6")}>Fri, 18 Sep</span>
      <div style={{ flex: 1 }} />
      <div style={{ alignSelf: "center", width: dp(108), height: dp(4), borderRadius: dp(2), background: withAlpha("#FFFFFF", 0.6), marginBottom: dp(8) }} />
    </Col>
    {children}
  </div>
);
