import React from "react";
import { Img, staticFile } from "remotion";
import { colors, dp, withAlpha } from "../theme/tokens";
import { type } from "../theme/type";

export const Divider: React.FC<{ color?: string; style?: React.CSSProperties }> = ({ color = colors.divider, style }) => (
  <div style={{ height: dp(1), background: color, width: "100%", flexShrink: 0, ...style }} />
);

export const Row: React.FC<React.PropsWithChildren<{ style?: React.CSSProperties; gap?: number }>> = ({
  children,
  style,
  gap,
}) => (
  <div style={{ display: "flex", flexDirection: "row", alignItems: "center", gap: gap ? dp(gap) : undefined, ...style }}>
    {children}
  </div>
);

export const Col: React.FC<React.PropsWithChildren<{ style?: React.CSSProperties }>> = ({ children, style }) => (
  <div style={{ display: "flex", flexDirection: "column", ...style }}>{children}</div>
);

export const Spacer: React.FC<{ h?: number; w?: number }> = ({ h, w }) => (
  <div style={{ height: h ? dp(h) : undefined, width: w ? dp(w) : undefined, flexShrink: 0 }} />
);

/** Coil AsyncImage with ContentScale.Crop: a 720×540 crest cropped to a square (or a circle). */
export const Crest: React.FC<{ src: string; size: number; circle?: boolean }> = ({ src, size, circle }) => (
  <Img
    src={staticFile(src)}
    style={{
      width: dp(size),
      height: dp(size),
      objectFit: "cover",
      borderRadius: circle ? "50%" : 0,
      flexShrink: 0,
      display: "block",
    }}
  />
);

export type StatusType = "Live" | "Upcoming" | "Result";

/** StatusBadgeV2: 6dp dot + 5dp + labelSmall SemiBold. Red for live, grey otherwise. No pill, no pulse. */
export const StatusBadgeV2: React.FC<{ status: StatusType }> = ({ status }) => {
  const color = status === "Live" ? colors.live : colors.textSecondary;
  return (
    <Row gap={5}>
      <div style={{ width: dp(6), height: dp(6), borderRadius: "50%", background: color }} />
      <span style={{ ...type.labelSmall, fontWeight: 600, color }}>{status.toUpperCase()}</span>
    </Row>
  );
};

type GlyphKind = "WICKET" | "FOUR" | "SIX" | "EXTRA" | "DOT" | "RUNS";

/** components/v2/BallGlyph.kt */
export const ballGlyphKindOf = (ball: string): GlyphKind => {
  const g = ball.trim().toUpperCase();
  if (/^W\d?$/.test(g)) return "WICKET";
  if (g === "4") return "FOUR";
  if (g === "6") return "SIX";
  if (/^(?:WD\d{0,2}|NB|N\d{1,2}|LB|L\d{1,2}|B\d{0,2})$/.test(g)) return "EXTRA";
  if (g === "0" || g === "." || g === "–" || g === "•") return "DOT";
  return "RUNS";
};

const ballStyle = (ball: string): { bg: string; fg: string } => {
  switch (ballGlyphKindOf(ball)) {
    case "WICKET":
      return { bg: withAlpha(colors.cricket.wicket, 0.85), fg: colors.cricket.onEvent };
    case "FOUR":
      return { bg: withAlpha(colors.cricket.four, 0.85), fg: colors.cricket.onEvent };
    case "SIX":
      return { bg: withAlpha(colors.cricket.six, 0.85), fg: colors.cricket.onEvent };
    case "EXTRA":
      return { bg: withAlpha(colors.cricket.wide, 0.85), fg: colors.cricket.onEvent };
    case "DOT":
      return { bg: withAlpha(colors.textSecondary, 0.3), fg: colors.onSurface };
    default:
      return { bg: withAlpha(colors.primary, 0.2), fg: colors.onSurface };
  }
};

/** RecentBallsStripV2 pill: 22dp tall, min 22dp wide, radius 11, padding 5h, labelSmall. */
export const BallPill: React.FC<{ ball: string; style?: React.CSSProperties }> = ({ ball, style }) => {
  const { bg, fg } = ballStyle(ball);
  return (
    <div
      style={{
        height: dp(22),
        minWidth: dp(22),
        borderRadius: dp(11),
        background: bg,
        padding: `0 ${dp(5)}px`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxSizing: "border-box",
        flexShrink: 0,
        ...style,
      }}
    >
      <span style={{ ...type.labelSmall, color: fg }}>{ball.trim()}</span>
    </div>
  );
};

export const RecentBallsStripV2: React.FC<{ balls: string[]; ballStyle?: (i: number, n: number) => React.CSSProperties }> = ({
  balls,
  ballStyle: perBall,
}) => {
  const shown = balls.slice(-12);
  return (
    <Row gap={4}>
      {shown.map((b, i) => (
        <BallPill key={`${i}-${b}`} ball={b} style={perBall?.(i, shown.length)} />
      ))}
    </Row>
  );
};

/** The Android status bar drawn in-frame on the app's own top-bar colour. */
export const StatusBar: React.FC<{ background?: string; ink?: string }> = ({ background = colors.topBar, ink = colors.onSurface }) => (
  <div
    style={{
      height: dp(40),
      background,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: `0 ${dp(24)}px 0 ${dp(24)}px`,
      boxSizing: "border-box",
      flexShrink: 0,
    }}
  >
    <span style={{ ...type.labelMedium, fontWeight: 500, color: ink, fontVariantNumeric: "tabular-nums" }}>19:42</span>
    <Row gap={6}>
      {/* signal */}
      <svg width={dp(14)} height={dp(14)} viewBox="0 0 24 24">
        <path d="M2 22h20V2z" fill={ink} />
      </svg>
      {/* wifi */}
      <svg width={dp(14)} height={dp(14)} viewBox="0 0 24 24">
        <path d="M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z" fill={ink} />
      </svg>
      {/* battery */}
      <svg width={dp(14)} height={dp(14)} viewBox="0 0 24 24">
        <path d="M15.67 4H14V2h-4v2H8.33C7.6 4 7 4.6 7 5.33v15.33C7 21.4 7.6 22 8.33 22h7.33c.74 0 1.34-.6 1.34-1.33V5.33C17 4.6 16.4 4 15.67 4z" fill={ink} />
      </svg>
    </Row>
  </div>
);

/** The gesture navigation pill at the bottom of the screen. */
export const NavPill: React.FC<{ background: string; ink?: string }> = ({ background, ink = colors.onSurface }) => (
  <div style={{ height: dp(20), background, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
    <div style={{ width: dp(108), height: dp(4), borderRadius: dp(2), background: withAlpha(ink, 0.6) }} />
  </div>
);
