import SwiftUI
import UIKit

/// Bouncer v2 palette (mirrors ColorTokensV2.kt). Gold is only for interactive elements and the wordmark;
/// data (scores, names, statuses) is always neutral ink.
enum BouncerColor {
    static let bg = Color(light: 0xFAFAFA, dark: 0x0C1015)
    static let surface = Color(light: 0xFFFFFF, dark: 0x161C26)
    static let surfaceVariant = Color(light: 0xF5F5F5, dark: 0x202834)
    static let inkPrimary = Color(light: 0x1A1A1A, dark: 0xECEEF1)
    static let inkSecondary = Color(light: 0x4B5563, dark: 0xA0A7B2)
    static let border = Color(light: 0x9CA3AF, dark: 0x2B3442)
    static let divider = Color(light: 0xD1D5DB, dark: 0x202834)
    /// Gold as a fill (buttons, indicators). Pair with `onGold` text.
    static let goldFill = Color(light: 0xE0B74F, dark: 0xF3DB93)
    /// Gold as ink (accent text, icons, underline). Darker in light mode for contrast.
    static let goldInk = Color(light: 0x8A6516, dark: 0xF3DB93)
    static let onGold = Color(light: 0x0C1015, dark: 0x1A1A1A)
    static let live = Color(light: 0xDC2626, dark: 0xEF4444)
    static let win = Color(light: 0x15803D, dark: 0x22C55E)
    static let scheduled = Color(hex: 0x9CA3AF)
    static let shimmer = Color(light: 0xE5E7EB, dark: 0x202834)
    static let shimmerHighlight = Color(light: 0xF5F5F5, dark: 0x2B3442)
    /// Nav/tab bar background: the page itself.
    static let topBar = Color(light: 0xFFFFFF, dark: 0x0C1015)

    // Ball / cricket event fills (white or black ink is chosen per fill for contrast).
    static let wicket = Color(light: 0xDC2626, dark: 0xEF4444)
    static let four = Color(light: 0x15803D, dark: 0x22C55E)
    static let six = Color(light: 0x6D28D9, dark: 0x8B5CF6)
    static let wide = Color(light: 0xB45309, dark: 0xFBBF24)
    static let noBall = Color(light: 0xC2410C, dark: 0xFB923C)
    static let maiden = Color(light: 0x0F766E, dark: 0x2DD4BF)
    static let dot = Color(hex: 0x6B7280)
}

extension Color {
    init(hex: UInt32) {
        self.init(uiColor: UIColor(hex: hex))
    }

    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }

    /// Black or white, whichever contrasts better with this color in the current trait collection.
    func contrastingInk(_ scheme: ColorScheme) -> Color {
        let ui = UIColor(self).resolvedColor(with: UITraitCollection(userInterfaceStyle: scheme == .dark ? .dark : .light))
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        func lin(_ c: CGFloat) -> CGFloat { c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        let l = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        return 1.05 / (l + 0.05) >= (l + 0.05) / 0.05 ? .white : .black
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}
