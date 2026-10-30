import type { CSSProperties } from "react";
import { FONT_FAMILY } from "./fonts";
import { dp } from "./tokens";

/**
 * Mirrors ui/theme/v2/TypographyV2.kt. Sizes in sp × DP; line heights likewise.
 * `tnum` styles set tabular numerals so a ticking score does not reflow the row.
 */
const style = (
  size: number,
  lineHeight: number,
  weight: 400 | 500 | 600 | 700,
  letterSpacing = 0,
  tabular = false,
): CSSProperties => ({
  fontFamily: FONT_FAMILY,
  fontSize: dp(size),
  lineHeight: `${dp(lineHeight)}px`,
  fontWeight: weight,
  letterSpacing: dp(letterSpacing),
  ...(tabular ? { fontVariantNumeric: "tabular-nums", fontFeatureSettings: '"tnum"' } : {}),
});

export const type = {
  displayLarge: style(32, 40, 700, -0.5),
  displayMedium: style(28, 36, 700, -0.25),
  headlineLarge: style(24, 32, 700),
  headlineMedium: style(20, 28, 600),
  titleLarge: style(18, 26, 600),
  titleMedium: style(17, 24, 500, 0.15),
  titleSmall: style(14, 20, 500, 0.1),
  bodyLarge: style(16, 24, 400, 0.5),
  bodyMedium: style(14, 20, 400, 0.25),
  bodySmall: style(12, 16, 400, 0.4),
  labelLarge: style(14, 20, 500, 0.1),
  labelMedium: style(12, 16, 500, 0.5),
  labelSmall: style(11, 16, 500, 0.5),
  overline: style(11, 16, 500, 0.8),
  scoreLarge: style(24, 30, 700, 0, true),
  scoreMedium: style(18, 24, 700, 0, true),
  numeric: style(14, 20, 500, 0, true),
  numericSmall: style(12, 16, 400, 0.4, true),
} as const;

export const tnum: CSSProperties = {
  fontVariantNumeric: "tabular-nums",
  fontFeatureSettings: '"tnum"',
};
