import React from "react";
import { AbsoluteFill, Audio, Easing, Img, interpolate, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { FONT_FAMILY } from "../theme/fonts";
import { AD_BG, AD_GOLD, AD_INK } from "../ad/Headline";

const ease = Easing.bezier(0.16, 1, 0.3, 1);

/** Scene 1 — black, the launcher batsman springs in, the wordmark and tagline follow. */
export const Hook: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const word = "Bouncer";
  return (
    <AbsoluteFill style={{ background: AD_BG, alignItems: "center", justifyContent: "center" }}>
      <Audio src={staticFile("sfx/whoosh.wav")} volume={0.5} />
      <Img
        src={staticFile("icon.webp")}
        style={{
          width: 300,
          height: 300,
          mixBlendMode: "screen",
          scale: String(
            interpolate(frame, [0, 0.9 * fps], [0.4, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.spring({ damping: 200 }), output: "perceptual-scale" }),
          ),
          opacity: interpolate(frame, [0, 0.4 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease }),
        }}
      />
      <div style={{ display: "flex", marginTop: 8 }}>
        {word.split("").map((ch, i) => (
          <span
            key={i}
            style={{
              fontFamily: FONT_FAMILY,
              fontSize: 132,
              lineHeight: "150px",
              fontWeight: 700,
              letterSpacing: -2,
              color: AD_GOLD,
              opacity: interpolate(frame, [0.5 * fps + i * 2, 0.5 * fps + i * 2 + 10], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease }),
              translate: `0 ${interpolate(frame, [0.5 * fps + i * 2, 0.5 * fps + i * 2 + 12], [18, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease })}px`,
            }}
          >
            {ch}
          </span>
        ))}
      </div>
      <span
        style={{
          fontFamily: FONT_FAMILY,
          fontSize: 52,
          fontWeight: 400,
          color: AD_INK,
          marginTop: 12,
          opacity: interpolate(frame, [1.5 * fps, 2.1 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease }),
          translate: `0 ${interpolate(frame, [1.5 * fps, 2.1 * fps], [16, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease })}px`,
        }}
      >
        It hits different.
      </span>
    </AbsoluteFill>
  );
};
