import React from "react";
import { Easing, interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { FONT_FAMILY } from "../theme/fonts";
import { colors } from "../theme/tokens";

export const AD_BG = colors.background;
export const AD_INK = colors.onSurface;
export const AD_MUTED = colors.textSecondary;
export const AD_GOLD = colors.primaryText;

const ease = Easing.bezier(0.16, 1, 0.3, 1);

/**
 * The right-hand column copy for one beat: a small eyebrow (the listing's section name), an
 * 84px headline with one gold word, and a 44px supporting line. Enters from below, leaves upward.
 */
export const Headline: React.FC<{
  eyebrow: string;
  title: React.ReactNode;
  sub: string;
  from: number;
  until: number;
  style?: React.CSSProperties;
}> = ({ eyebrow, title, sub, from, until, style }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const inDur = 0.6 * fps;
  const outDur = 0.35 * fps;
  const opacity = interpolate(frame, [from, from + inDur, until - outDur, until], [0, 1, 1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: ease,
  });
  const y = interpolate(frame, [from, from + inDur, until - outDur, until], [40, 0, 0, -24], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: ease,
  });
  if (frame < from || frame > until) return null;
  return (
    <div style={{ position: "absolute", left: 800, top: 0, bottom: 0, width: 980, display: "flex", flexDirection: "column", justifyContent: "center", opacity, translate: `0 ${y}px`, ...style }}>
      <span style={{ fontFamily: FONT_FAMILY, fontSize: 26, fontWeight: 500, letterSpacing: 4, color: AD_MUTED, marginBottom: 20 }}>{eyebrow}</span>
      <span style={{ fontFamily: FONT_FAMILY, fontSize: 92, lineHeight: "100px", fontWeight: 700, letterSpacing: -2, color: AD_INK }}>{title}</span>
      <span style={{ fontFamily: FONT_FAMILY, fontSize: 40, lineHeight: "52px", fontWeight: 400, color: AD_MUTED, marginTop: 24, maxWidth: 900 }}>{sub}</span>
    </div>
  );
};

export const Gold: React.FC<React.PropsWithChildren> = ({ children }) => <span style={{ color: AD_GOLD }}>{children}</span>;
