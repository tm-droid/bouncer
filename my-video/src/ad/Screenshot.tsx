import React from "react";
import { AbsoluteFill } from "remotion";
import { FONT_FAMILY } from "../theme/fonts";
import { colors, withAlpha } from "../theme/tokens";
import { Phone } from "../app/Phone";
import { AD_BG, AD_INK, AD_MUTED } from "./Headline";

export const SHOT_W = 1080;
export const SHOT_H = 1920;
const PHONE_SCALE = 0.8;
const PHONE_TOP = 470;

/**
 * One Play Store phone screenshot, 1080×1920 (9:16 — the only portrait ratio Play accepts; the
 * 1080×2400 `Stills/*` are 9:20 and are refused). Headline copy on top, the device frame below,
 * bleeding off the bottom edge so the screen reads large. Same tokens and `Phone` as the ad, and
 * deliberately not `Headline`, which animates on the frame counter and is laid out for 1920 wide.
 */
export const Screenshot: React.FC<
  React.PropsWithChildren<{ eyebrow: string; title: React.ReactNode; sub: string; screenBackground?: string }>
> = ({ eyebrow, title, sub, screenBackground, children }) => {
  const phoneW = (1080 + 34 * 2) * PHONE_SCALE;
  return (
    <AbsoluteFill style={{ background: AD_BG, overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          left: -200,
          top: -260,
          width: 1480,
          height: 1100,
          background: `radial-gradient(closest-side, ${withAlpha(colors.primaryText, 0.08)}, transparent)`,
        }}
      />
      <div
        style={{
          position: "absolute",
          left: 80,
          right: 80,
          top: 110,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          textAlign: "center",
        }}
      >
        <span style={{ fontFamily: FONT_FAMILY, fontSize: 26, fontWeight: 500, letterSpacing: 4, color: AD_MUTED }}>{eyebrow}</span>
        <span style={{ fontFamily: FONT_FAMILY, fontSize: 74, lineHeight: "84px", fontWeight: 700, letterSpacing: -1.5, color: AD_INK, marginTop: 22, textWrap: "balance" }}>
          {title}
        </span>
        <span style={{ fontFamily: FONT_FAMILY, fontSize: 34, lineHeight: "44px", fontWeight: 400, color: AD_MUTED, marginTop: 20, maxWidth: 860 }}>{sub}</span>
      </div>
      <div style={{ position: "absolute", left: (SHOT_W - phoneW) / 2, top: PHONE_TOP }}>
        <Phone scale={PHONE_SCALE} screenBackground={screenBackground}>
          {children}
        </Phone>
      </div>
    </AbsoluteFill>
  );
};
