import React from "react";
import { AbsoluteFill, Audio, Easing, Img, interpolate, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { FONT_FAMILY } from "../theme/fonts";
import { AD_BG, AD_GOLD, AD_INK, AD_MUTED } from "../ad/Headline";
import { colors } from "../theme/tokens";

const ease = Easing.bezier(0.16, 1, 0.3, 1);

/** Scene 7 — icon + wordmark, the listing's closing line, and a Get-it-on-Google-Play pill. */
export const Cta: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const fade = (from: number, dur = 0.5 * fps) => interpolate(frame, [from, from + dur], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease });
  const rise = (from: number, dur = 0.5 * fps) => interpolate(frame, [from, from + dur], [24, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease });
  return (
    <AbsoluteFill style={{ background: AD_BG, alignItems: "center", justifyContent: "center" }}>
      <Audio src={staticFile("sfx/ding.wav")} volume={0.35} startFrom={0} />
      <div style={{ display: "flex", alignItems: "center", gap: 28, opacity: fade(0), translate: `0 ${rise(0)}px` }}>
        <Img src={staticFile("icon.webp")} style={{ width: 132, height: 132, mixBlendMode: "screen" }} />
        <span style={{ fontFamily: FONT_FAMILY, fontSize: 112, fontWeight: 700, letterSpacing: -2, color: AD_GOLD }}>Bouncer</span>
      </div>
      <span style={{ fontFamily: FONT_FAMILY, fontSize: 56, fontWeight: 400, color: AD_INK, marginTop: 28, opacity: fade(0.4 * fps), translate: `0 ${rise(0.4 * fps)}px` }}>
        No sign-up. No clutter. Just the match.
      </span>
      <div
        style={{
          marginTop: 64,
          display: "flex",
          alignItems: "center",
          gap: 18,
          padding: "22px 44px",
          borderRadius: 999,
          background: colors.primary,
          opacity: fade(1 * fps),
          scale: String(interpolate(frame, [1 * fps, 1.6 * fps], [0.9, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.spring({ damping: 200 }), output: "perceptual-scale" })),
        }}
      >
        <svg width="40" height="44" viewBox="0 0 24 26">
          <path d="M1.5 1.2v23.6c0 .6.6 1 1.1.7l12.9-11.8L2.6.5c-.5-.3-1.1.1-1.1.7z" fill="#1A1A1A" />
          <path d="M16.9 14.6l-3.4-3.3L2.6 25.5l14.3-8.2c.9-.5.9-1.9 0-2.7z" fill="#1A1A1A" opacity=".75" />
          <path d="M16.9 9.4L2.6.5l10.9 10.8 3.4-1.9z" fill="#1A1A1A" opacity=".55" />
          <path d="M21.6 11.5l-4.7-2.7-3.6 3.5 3.6 3.6 4.7-2.7c1-.5 1-1.9 0-1.7z" fill="#1A1A1A" opacity=".9" />
        </svg>
        <span style={{ fontFamily: FONT_FAMILY, fontSize: 40, fontWeight: 600, color: colors.onPrimary }}>Get it on Google Play</span>
      </div>
      <span style={{ fontFamily: FONT_FAMILY, fontSize: 28, color: AD_MUTED, marginTop: 40, opacity: fade(1.4 * fps) }}>Free · Light and dark themes · No account needed</span>
    </AbsoluteFill>
  );
};
