import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";
import { FONT_FAMILY } from "../theme/fonts";
import { colors, withAlpha } from "../theme/tokens";
import { Phone } from "../app/Phone";
import { HomeScreen } from "../app/HomeScreen";
import { states } from "../data/match";
import { AD_BG, AD_GOLD, AD_INK, AD_MUTED } from "./Headline";

/**
 * YouTube thumbnail, 1280×720. Phone on the Home hero (live score, ball chips, win bar) at left;
 * icon, wordmark and the listing's one-line pitch at right. Same tokens and components as the ad.
 */
export const Thumbnail: React.FC = () => (
  <AbsoluteFill style={{ background: AD_BG, overflow: "hidden" }}>
    <div style={{ position: "absolute", left: -120, top: -160, width: 900, height: 1000, background: `radial-gradient(closest-side, ${withAlpha(colors.primaryText, 0.08)}, transparent)` }} />
    <div style={{ position: "absolute", left: 96, top: 44 }}>
      <Phone scale={0.31}>
        <HomeScreen state={states.s2} winProb={states.s2.winProb} />
      </Phone>
    </div>
    <div style={{ position: "absolute", left: 520, top: 0, bottom: 0, width: 720, display: "flex", flexDirection: "column", justifyContent: "center" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
        <Img src={staticFile("icon.webp")} style={{ width: 96, height: 96, mixBlendMode: "screen" }} />
        <span style={{ fontFamily: FONT_FAMILY, fontSize: 88, fontWeight: 700, letterSpacing: -2, color: AD_GOLD, lineHeight: "96px" }}>Bouncer</span>
      </div>
      <span style={{ fontFamily: FONT_FAMILY, fontSize: 60, lineHeight: "68px", fontWeight: 700, letterSpacing: -1, color: AD_INK, marginTop: 28 }}>
        Live cricket scores.
        <br />
        Nothing else.
      </span>
      <span style={{ fontFamily: FONT_FAMILY, fontSize: 30, lineHeight: "40px", fontWeight: 400, color: AD_MUTED, marginTop: 22 }}>
        Ball-by-ball commentary · win probability · full scorecards
      </span>
    </div>
  </AbsoluteFill>
);
