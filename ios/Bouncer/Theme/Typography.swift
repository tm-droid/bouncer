import SwiftUI
import CoreText

enum BouncerFonts {
    static let regular = "IBMPlexSans"
    static let medium = "IBMPlexSans-Medm"
    static let semibold = "IBMPlexSans-SmBld"
    static let bold = "IBMPlexSans-Bold"

    private static let registered: Void = {
        for name in ["ibm_plex_sans_regular", "ibm_plex_sans_medium", "ibm_plex_sans_semibold", "ibm_plex_sans_bold"] {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    /// Registers the bundled IBM Plex Sans files. Idempotent; call once at app start.
    static func register() { _ = registered }

    static func postScriptName(for weight: Font.Weight) -> String {
        switch weight {
        case .bold, .heavy, .black: bold
        case .semibold: semibold
        case .medium: medium
        default: regular
        }
    }
}

extension Font {
    /// IBM Plex Sans scaled with Dynamic Type relative to `style`. ExtraBold maps to Bold (no ExtraBold file, as on Android).
    static func bouncer(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom(BouncerFonts.postScriptName(for: weight), size: size, relativeTo: style)
    }

    // Mirrors TypographyV2.kt (letter spacing/line height omitted; apply `.tracking` at call sites if needed).
    static let displayLarge = bouncer(32, .bold, relativeTo: .largeTitle)
    static let displayMedium = bouncer(28, .bold, relativeTo: .title)
    static let headlineLarge = bouncer(24, .bold, relativeTo: .title2)
    static let headlineMedium = bouncer(20, .semibold, relativeTo: .title3)
    static let titleLarge = bouncer(18, .semibold, relativeTo: .headline)
    static let titleMedium = bouncer(17, .medium, relativeTo: .headline)
    static let titleSmall = bouncer(14, .medium, relativeTo: .subheadline)
    static let bodyLarge = bouncer(16, .regular, relativeTo: .body)
    static let bodyMedium = bouncer(14, .regular, relativeTo: .subheadline)
    static let bodySmall = bouncer(12, .regular, relativeTo: .caption)
    static let labelLarge = bouncer(14, .medium, relativeTo: .subheadline)
    static let labelMedium = bouncer(12, .medium, relativeTo: .caption)
    static let labelSmall = bouncer(11, .medium, relativeTo: .caption2)
    static let overline = bouncer(11, .medium, relativeTo: .caption2)
    static let scoreHero = bouncer(36, .bold, relativeTo: .largeTitle)
    static let scoreLarge = bouncer(24, .bold, relativeTo: .title2)
    static let scoreMedium = bouncer(18, .bold, relativeTo: .title3)
    static let numeric = bouncer(14, .medium, relativeTo: .subheadline)
    static let numericSmall = bouncer(12, .regular, relativeTo: .caption)
}

extension View {
    /// Tabular figures: digits keep one width so live-updating numbers don't reflow. Use on every score/overs/stat.
    func tabularFigures() -> some View { monospacedDigit() }
}
