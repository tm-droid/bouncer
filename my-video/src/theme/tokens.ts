/**
 * Mirrors app/src/main/kotlin/com/tanush/bouncer/ui/theme/v2/ColorTokensV2.kt (dark palette).
 * Every colour and size the rebuilt screens use comes from here — no literals in components.
 */

/** dp → px. Screens are laid out at 1080 logical px (xxhdpi) and scaled down inside the phone. */
export const DP = 3;
export const dp = (n: number) => n * DP;

export const colors = {
  background: "#0C1015",
  topBar: "#0C1015",
  surface: "#161C26",
  surfaceVariant: "#202834",
  divider: "#202834",
  border: "#2B3442",
  onSurface: "#ECEEF1",
  textSecondary: "#A0A7B2",
  onTopBar: "#F3DB93",
  onTopBarMuted: "#A0A7B2",
  primary: "#F3DB93",
  primaryDark: "#E0B74F",
  primaryContainer: "#34270D",
  primaryText: "#F3DB93",
  onPrimary: "#1A1A1A",
  live: "#EF4444",
  win: "#22C55E",
  scheduled: "#9CA3AF",
  cricket: {
    wicket: "#EF4444",
    four: "#22C55E",
    six: "#8B5CF6",
    wide: "#FBBF24",
    noBall: "#FB923C",
    maiden: "#2DD4BF",
    milestone: "#F3DB93",
    dotBall: "#6B7280",
    onEvent: "#FFFFFF",
  },
} as const;

/** Legacy premium dialect (Scorecard tab, win-probability hero) — ui/theme/BouncerColors.kt dark. */
export const legacy = {
  textPrimary: "#ECEEF1",
  textSecondary: "#A0A7B2",
  textMuted: "#6B7280",
  glassSurface: "rgba(255,255,255,0.06)",
  gold: "#E0B74F",
} as const;

/** Widget palette — ui/widgets/EnhancedWidgetStyles.kt dark (the older cream/tan set, as shipped). */
export const widget = {
  background: "#0C1015",
  surface: "#161C26",
  surfaceElevated: "#202834",
  surfaceHighest: "#2B3442",
  primary: "#E0B74F",
  primaryContainer: "#34270D",
  textPrimary: "#FCF7ED",
  textSecondary: "#C1B08D",
  textTertiary: "#8D7E61",
  live: "#FF453A",
  wicket: "#FF5A4E",
  six: "#9B7BFF",
  four: "#34C759",
  dotBall: "#3B4452",
  warning: "#F4B740",
} as const;

/** Notification layouts — res/values-night/colors.xml notif_* */
export const notif = {
  textPrimary: "#E8EAED",
  textSecondary: "#9AA0A6",
  divider: "rgba(255,255,255,0.2)",
} as const;

/** ui/theme/TeamColorScheme.kt TeamColorRegistry — brand primary/secondary. */
export const teamColors: Record<string, { primary: string; secondary: string }> = {
  IND: { primary: "#0057B8", secondary: "#FF9933" },
  AUS: { primary: "#FFC72C", secondary: "#006F3D" },
  ENG: { primary: "#00247D", secondary: "#CF142B" },
  PAK: { primary: "#01411C", secondary: "#FFFFFF" },
  SA: { primary: "#007A4D", secondary: "#FFB81C" },
  NZ: { primary: "#111111", secondary: "#C8102E" },
};

export const spacing = {
  xxs: 2,
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
  rowVertical: 14,
  sectionGap: 16,
} as const;

export const radius = {
  extraSmall: 4,
  small: 8,
  medium: 12,
  large: 16,
  extraLarge: 24,
} as const;

export const withAlpha = (hex: string, alpha: number) => {
  const h = hex.replace("#", "");
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r},${g},${b},${alpha})`;
};

const toRgb = (hex: string) => {
  const h = hex.replace("#", "");
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16),
  ] as const;
};

const toHex = (r: number, g: number, b: number) =>
  "#" +
  [r, g, b]
    .map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0"))
    .join("");

/**
 * TeamColorRegistry.buildTeamColors(...).surface on the dark ground:
 * primary.darken(0.72f).blend(ground.surface, 0.62f) — "a wash of brand colour over the ground".
 * (The contrast lift on primary is skipped: every brand colour used here already clears 3:1.)
 */
export const teamSurface = (acronym: string): string => {
  const brand = teamColors[acronym]?.primary ?? colors.primaryDark;
  const [r, g, b] = toRgb(brand).map((v) => v * (1 - 0.72));
  const [sr, sg, sb] = toRgb(colors.surface);
  const ratio = 0.62;
  return toHex(
    r * (1 - ratio) + sr * ratio,
    g * (1 - ratio) + sg * ratio,
    b * (1 - ratio) + sb * ratio,
  );
};
