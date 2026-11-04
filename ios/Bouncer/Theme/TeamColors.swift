import SwiftUI
import UIKit

/// An opaque sRGB colour as plain numbers, so contrast can be measured and tested (ColorUtils.kt).
struct RGB: Equatable {
    var r: Double
    var g: Double
    var b: Double

    init(r: Double, g: Double, b: Double) {
        self.r = r
        self.g = g
        self.b = b
    }

    init(hex: UInt32) {
        self.init(r: Double((hex >> 16) & 0xFF) / 255, g: Double((hex >> 8) & 0xFF) / 255, b: Double(hex & 0xFF) / 255)
    }

    /// `#RRGGBB`, as `TeamColors.json` carries it.
    init?(hex: String) {
        let digits = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard digits.count == 6, let value = UInt32(digits, radix: 16) else { return nil }
        self.init(hex: value)
    }

    init(_ ui: UIColor) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        self.init(r: Double(r), g: Double(g), b: Double(b))
    }

    /// A theme token as `scheme` draws it.
    init(_ color: Color, scheme: ColorScheme) {
        self.init(UIColor(color).resolvedColor(with: UITraitCollection(userInterfaceStyle: scheme == .dark ? .dark : .light)))
    }

    var color: Color { Color(red: r, green: g, blue: b) }

    /// WCAG relative luminance, 0 (black) to 1 (white).
    var luminance: Double {
        func lin(_ c: Double) -> Double { c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    /// WCAG contrast ratio, 1 to 21.
    func contrast(_ other: RGB) -> Double {
        let (a, b) = (luminance, other.luminance)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    func lighten(_ f: Double) -> RGB { RGB(r: r + (1 - r) * f, g: g + (1 - g) * f, b: b + (1 - b) * f) }
    func darken(_ f: Double) -> RGB { RGB(r: r * (1 - f), g: g * (1 - f), b: b * (1 - f)) }

    /// Stepped toward white or black, away from `background`, until it clears `ratio`.
    func ensureContrast(_ background: RGB, ratio: Double, maxIterations: Int = 50) -> RGB {
        let lighter = luminance > background.luminance
        var c = self
        for _ in 0..<maxIterations where c.contrast(background) < ratio {
            c = lighter ? c.lighten(0.05) : c.darken(0.05)
        }
        return c
    }

    /// Raised (or, on a light ground, lowered) in HSV value until it separates from `background`,
    /// keeping hue and saturation: `ensureContrast` bleaches Bangladesh's #006A4E to grey. Falls back
    /// to bleaching only when the value range runs out. For filled shapes and lines, not text.
    func liftForContrast(_ background: RGB, ratio: Double) -> RGB {
        if contrast(background) >= ratio { return self }
        var h: CGFloat = 0, s: CGFloat = 0, v: CGFloat = 0, a: CGFloat = 0
        UIColor(red: r, green: g, blue: b, alpha: 1).getHue(&h, saturation: &s, brightness: &v, alpha: &a)
        let brighten = background.luminance < 0.5
        var value = Double(v)
        while brighten ? value < 1 : value > 0 {
            value = brighten ? min(value + 0.04, 1) : max(value - 0.04, 0)
            let shifted = RGB(UIColor(hue: h, saturation: s, brightness: value, alpha: 1))
            if shifted.contrast(background) >= ratio { return shifted }
        }
        return ensureContrast(background, ratio: ratio)
    }
}

/// Team brand colours (TeamColorScheme.kt): curated kit colours first, crest-derived
/// `TeamColors.json` second, nothing when neither knows the side. Colour is drawn only where it
/// identifies a team, so callers get nil rather than a made-up hue.
enum TeamColorRegistry {
    typealias Pair = (primary: RGB, secondary: RGB)

    /// Name-keyed colours in a fixed order, because the first whole-word match wins a fuzzy lookup.
    struct Table {
        private(set) var entries: [(key: String, pair: Pair)] = []
        private var index: [String: Int] = [:]

        /// Keys are normalized here; a later duplicate replaces the colour but keeps its place,
        /// as Kotlin's `associate` does.
        init(_ source: [(String, Pair)]) {
            for (name, pair) in source {
                let key = TeamColorRegistry.normalize(name)
                if let i = index[key] { entries[i].pair = pair } else {
                    index[key] = entries.count
                    entries.append((key, pair))
                }
            }
        }

        subscript(key: String) -> Pair? { index[key].map { entries[$0].pair } }
    }

    /// True when `name` has a real colour here rather than none.
    static func isKnown(_ name: String?) -> Bool {
        guard let name, !name.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        return resolve(name) != nil
    }

    /// The two sides' fills for a bar or chart on `BouncerColor.surface`, or nil unless the registry
    /// knows **both** (TeamSegmentColors.kt). Pass full names: a three-letter code is only known when
    /// the alias table lists it. Each primary clears 3:1 on the page, as Android's team primary
    /// does, then is lifted to `minContrast` on the surface it is drawn on.
    static func segmentColors(_ team1: String?, _ team2: String?, scheme: ColorScheme, minContrast: Double) -> (RGB, RGB)? {
        guard let team1, let team2, let a = resolve(team1), let b = resolve(team2) else { return nil }
        let background = RGB(BouncerColor.bg, scheme: scheme)
        let surface = RGB(BouncerColor.surface, scheme: scheme)
        func fill(_ raw: RGB) -> RGB {
            raw.ensureContrast(background, ratio: 3).liftForContrast(surface, ratio: minContrast)
        }
        return (fill(a.primary), fill(b.primary))
    }

    static func resolve(_ name: String) -> Pair? {
        let normalized = normalize(name)
        let key = aliases[normalized.uppercased()] ?? normalized
        if let pair = curated[key] ?? extracted[key] { return pair }
        guard key.count >= minFuzzyKey else { return nil }
        return fuzzy(curated, key) ?? fuzzy(extracted, key)
    }

    /// Whole words only: "India Emerging" takes India's colours, "Romania" does not take Oman's, and
    /// a two-letter code never claims a franchise.
    static func fuzzy(_ table: Table, _ key: String) -> Pair? {
        let padded = " \(key) "
        return table.entries.first { padded.contains(" \($0.key) ") || " \($0.key) ".contains(padded) }?.pair
    }

    static func normalize(_ value: String) -> String {
        value.lowercased()
            .replacingOccurrences(of: "\\bwomen\\b", with: "", options: .regularExpression)
            .replacingOccurrences(of: "\\bw\\b", with: "", options: .regularExpression)
            .replacingOccurrences(of: "[^a-z0-9 ]", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    /// `{"Team name": ["#RRGGBB", "#RRGGBB"]}`, in the file's (sorted) key order. Entries that are not
    /// parseable hex colours are dropped; a missing secondary repeats the primary.
    static func parse(_ data: Data) -> Table {
        guard let raw = try? JSONDecoder().decode([String: [String]].self, from: data) else { return Table([]) }
        return Table(raw.sorted { $0.key < $1.key }.compactMap { name, hexes -> (String, Pair)? in
            guard let primary = hexes.first.flatMap(RGB.init(hex:)) else { return nil }
            let secondary = hexes.count > 1 ? RGB(hex: hexes[1]) : primary
            return secondary.map { (name, (primary, $0)) }
        })
    }

    private static let minFuzzyKey = 4

    /// Crest-derived colours from `TeamColors.json` (generated for Android by
    /// `tools/team-colors/extract.mjs`). Empty if the file is missing, which costs colour only.
    static let extracted: Table = Bundle.main.url(forResource: "TeamColors", withExtension: "json")
        .flatMap { try? Data(contentsOf: $0) }.map(parse) ?? Table([])

    /// Hand-picked kit colours, primary then secondary (TeamPalettes.kt). These are what a team plays
    /// in, not its flag, and win over the crest table: Australia is gold, New Zealand is black.
    static let curated = Table(curatedSource.map { ($0.0, (RGB(hex: $0.1), RGB(hex: $0.2))) })

    private static let curatedSource: [(String, UInt32, UInt32)] = [
        // Full members
        ("india", 0x0057B8, 0xFF9933),
        ("australia", 0xFFC72C, 0x006F3D),
        ("england", 0x00247D, 0xCF142B),
        ("pakistan", 0x01411C, 0xFFFFFF),
        ("south africa", 0x007A4D, 0xFFB81C),
        ("new zealand", 0x111111, 0xC8102E),
        ("west indies", 0x7B0031, 0xFFC72C),
        ("sri lanka", 0x1F3A93, 0xFFBE00),
        ("bangladesh", 0x006A4E, 0xF42A41),
        ("afghanistan", 0x0057B8, 0xD32011),
        ("ireland", 0x169B62, 0x0B2545),
        ("zimbabwe", 0xD40000, 0xFFD200),
        // Associates
        ("netherlands", 0xFF6F00, 0x21468B),
        ("scotland", 0x6A1B9A, 0x005EB8),
        ("nepal", 0x003893, 0xDC143C),
        ("united arab emirates", 0x00732F, 0xE4002B),
        ("united states of america", 0x0A3161, 0xB31942),
        ("namibia", 0x003580, 0xD21034),
        ("oman", 0xDB161B, 0x008000),
        ("canada", 0xD80621, 0x1A1A1A),
        ("hong kong", 0xDE2910, 0x1A1A1A),
        ("papua new guinea", 0xCE1126, 0xFCD116),
        // IPL
        ("mumbai indians", 0x004BA0, 0xD1A23C),
        ("chennai super kings", 0xFDB913, 0x0078BC),
        ("royal challengers bengaluru", 0xD71920, 0x0B0B0B),
        ("kolkata knight riders", 0x3A225D, 0xB4975A),
        ("rajasthan royals", 0xE91E63, 0x003594),
        ("punjab kings", 0xED1B24, 0xFFFFFF),
        ("sunrisers hyderabad", 0xFF822A, 0x1A1A1A),
        ("delhi capitals", 0x0078BC, 0xEF1C25),
        ("gujarat titans", 0x1C4E9D, 0x47C1BF),
        ("lucknow super giants", 0x004C93, 0xA5D9F7),
        // BBL
        ("adelaide strikers", 0x0084D6, 0xFFD700),
        ("brisbane heat", 0x00A6CE, 0xE4002B),
        ("hobart hurricanes", 0x6A2C91, 0xF5821F),
        ("melbourne renegades", 0xE4002B, 0x1A1A1A),
        ("melbourne stars", 0x00A651, 0x1A1A1A),
        ("perth scorchers", 0xF26522, 0x1A1A1A),
        ("sydney sixers", 0xE6007E, 0x1A1A1A),
        ("sydney thunder", 0x8CC63F, 0x1A1A1A),
        // PSL
        ("karachi kings", 0x0B4EA2, 0xE31E24),
        ("lahore qalandars", 0x00A651, 0xE31E24),
        ("islamabad united", 0xE31E24, 0x1A1A1A),
        ("peshawar zalmi", 0xFFD100, 0x1A1A1A),
        ("quetta gladiators", 0x5B2C83, 0xFFD100),
        ("multan sultans", 0x00804A, 0xFFD100),
        // SA20
        ("mi cape town", 0x004BA0, 0xD1A23C),
        ("joburg super kings", 0xFDB913, 0x0078BC),
        ("durban super giants", 0x0077C8, 0xA5D9F7),
        ("pretoria capitals", 0x0078BC, 0xEF1C25),
        ("paarl royals", 0xE91E63, 0x003594),
        ("sunrisers eastern cape", 0xFF822A, 0x1A1A1A),
        // CPL
        ("trinbago knight riders", 0xE31E24, 0x1A1A1A),
        ("guyana amazon warriors", 0x00A651, 0xFFD100),
        ("barbados royals", 0xE91E63, 0x003594),
        ("jamaica tallawahs", 0x006B3F, 0xFFD100),
        ("saint lucia kings", 0x0078BC, 0xFFD100),
        ("st kitts and nevis patriots", 0xE31E24, 0x00A651),
        ("antigua and barbuda falcons", 0x1A1A1A, 0xE31E24),
        // The Hundred
        ("oval invincibles", 0x009A44, 0xD4AF37),
        ("london spirit", 0x1E2E5B, 0xD4AF37),
        ("southern brave", 0x00A9CE, 0x1A1A1A),
        ("welsh fire", 0xE31E24, 0x1A1A1A),
        ("manchester originals", 0x1A1A1A, 0xFF6F00),
        ("northern superchargers", 0x5B2C83, 0xFFD100),
        ("trent rockets", 0xFFD100, 0x1A1A1A),
        ("birmingham phoenix", 0xFF6F00, 0x1A1A1A),
        // MLC
        ("texas super kings", 0xFDB913, 0x0078BC),
        ("mi new york", 0x004BA0, 0xD1A23C),
        ("los angeles knight riders", 0x3A225D, 0xB4975A),
        ("san francisco unicorns", 0xD50032, 0x1A1A1A),
        ("seattle orcas", 0x003B5C, 0x00A3AD),
        ("washington freedom", 0x0A3161, 0xB31942),
        // ILT20
        ("mi emirates", 0x004BA0, 0xD1A23C),
        ("abu dhabi knight riders", 0x3A225D, 0xB4975A),
        ("desert vipers", 0xE31E24, 0x1A1A1A),
        ("dubai capitals", 0x0078BC, 0xEF1C25),
        ("gulf giants", 0xFF822A, 0x1A1A1A),
        ("sharjah warriorz", 0x0B4EA2, 0xE31E24),
    ]

    /// Codes and former names, matched upper-cased and whole, that resolve to a curated key.
    private static let aliases: [String: String] = [
        "IND": "india",
        "AUS": "australia",
        "ENG": "england",
        "PAK": "pakistan",
        "SA": "south africa",
        "RSA": "south africa",
        "NZ": "new zealand",
        "WI": "west indies",
        "SL": "sri lanka",
        "BAN": "bangladesh",
        "AFG": "afghanistan",
        "IRE": "ireland",
        "ZIM": "zimbabwe",
        "NED": "netherlands",
        "SCO": "scotland",
        "NEP": "nepal",
        "UAE": "united arab emirates",
        "USA": "united states of america",
        "UNITED STATES": "united states of america",
        "NAM": "namibia",
        "OMA": "oman",
        "CAN": "canada",
        "HK": "hong kong",
        "PNG": "papua new guinea",
        "MI": "mumbai indians",
        "CSK": "chennai super kings",
        "RCB": "royal challengers bengaluru",
        "ROYAL CHALLENGERS BANGALORE": "royal challengers bengaluru",
        "KKR": "kolkata knight riders",
        "RR": "rajasthan royals",
        "PBKS": "punjab kings",
        "KXIP": "punjab kings",
        "KINGS XI PUNJAB": "punjab kings",
        "SRH": "sunrisers hyderabad",
        "DC": "delhi capitals",
        "DD": "delhi capitals",
        "DELHI DAREDEVILS": "delhi capitals",
        "GT": "gujarat titans",
        "LSG": "lucknow super giants",
        "DURBAN S SUPER GIANTS": "durban super giants",
        "ST LUCIA KINGS": "saint lucia kings",
    ]
}
