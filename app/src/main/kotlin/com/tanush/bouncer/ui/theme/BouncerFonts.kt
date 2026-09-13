package com.tanush.bouncer.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tanush.bouncer.R

/**
 * The app typeface: IBM Plex Sans, bundled in `res/font` (SIL OFL, licence in
 * `assets/licenses/ibm-plex-sans-OFL.txt`).
 *
 * The TTFs are subset to Latin (U+0000-024F, Latin Extended Additional, combining marks, general
 * punctuation, currency, arrows, math, geometric shapes) with every OpenType feature kept and hinting
 * dropped: 806 KB down to 272 KB. Anything outside those ranges (Cyrillic, Greek, Devanagari) falls
 * back to the system font, which is what it did for scripts Plex never covered. To regenerate from
 * the upstream files, run `pyftsubset` with those `--unicodes`, `--layout-features='*' --no-hinting`.
 *
 * This used to be Inter fetched through the Google Play downloadable-font provider. Two problems: Inter
 * is the face every generated interface defaults to, and a downloadable font falls back to Roboto
 * silently whenever the provider is slow or absent, so the app never reliably rendered in the face it
 * declared. Bundled, the type is the same on every device from first frame.
 *
 * Plex's figures are tabular by default (every digit is 600 units), so scores that repoll in place do
 * not reflow even where a style forgets `tnum`. The family stops at Bold; ExtraBold requests resolve
 * to it rather than to a synthesised weight.
 *
 * Kept in a separate file to prevent circular class initialization between
 * BouncerTypographyTokens and BouncerTypographyKt top-level declarations.
 */
val BouncerFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Light),
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.ExtraBold),
)
