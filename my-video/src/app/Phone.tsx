import React from "react";
import { colors } from "../theme/tokens";

export const SCREEN_W = 1080;
export const SCREEN_H = 2400;
const BEZEL = 34;
const CORNER = 132;

/**
 * A device frame around a 1080×2400 logical screen. Children lay out at full logical size and the
 * whole frame is scaled with `scale`, so every dp value inside is used ×3 as-is.
 */
export const Phone: React.FC<
  React.PropsWithChildren<{ scale: number; style?: React.CSSProperties; screenBackground?: string }>
> = ({ children, scale, style, screenBackground = colors.background }) => {
  const outerW = SCREEN_W + BEZEL * 2;
  const outerH = SCREEN_H + BEZEL * 2;
  return (
    <div style={{ width: outerW * scale, height: outerH * scale, position: "relative", ...style }}>
      <div
        style={{
          width: outerW,
          height: outerH,
          scale: String(scale),
          transformOrigin: "top left",
          position: "absolute",
          left: 0,
          top: 0,
          borderRadius: CORNER + BEZEL,
          background: "#101214",
          boxShadow: "0 0 0 3px #2A2E34, 0 60px 120px rgba(0,0,0,0.55)",
        }}
      >
        <div
          style={{
            position: "absolute",
            left: BEZEL,
            top: BEZEL,
            width: SCREEN_W,
            height: SCREEN_H,
            borderRadius: CORNER,
            overflow: "hidden",
            background: screenBackground,
          }}
        >
          {children}
          {/* punch-hole camera */}
          <div
            style={{
              position: "absolute",
              top: 24,
              left: SCREEN_W / 2 - 18,
              width: 36,
              height: 36,
              borderRadius: "50%",
              background: "#05070A",
              boxShadow: "inset 0 0 0 4px #15181C",
            }}
          />
        </div>
      </div>
    </div>
  );
};
