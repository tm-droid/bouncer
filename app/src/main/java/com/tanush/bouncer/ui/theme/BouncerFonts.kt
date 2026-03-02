package com.tanush.bouncer.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.tanush.bouncer.R

/**
 * Font definitions for the Bouncer app.
 * Kept in a separate file to prevent circular class initialization between
 * BouncerTypographyTokens and BouncerTypographyKt top-level declarations.
 */

internal val BouncerFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val InterFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = BouncerFontProvider, weight = FontWeight.Light),
    Font(googleFont = GoogleFont("Inter"), fontProvider = BouncerFontProvider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Inter"), fontProvider = BouncerFontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Inter"), fontProvider = BouncerFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Inter"), fontProvider = BouncerFontProvider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Inter"), fontProvider = BouncerFontProvider, weight = FontWeight.ExtraBold),
)
