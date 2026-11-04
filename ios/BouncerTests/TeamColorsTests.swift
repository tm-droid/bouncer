import SwiftUI
import Testing
@testable import Bouncer

struct TeamColorRegistryTests {
    private func primary(_ name: String) -> RGB? { TeamColorRegistry.resolve(name)?.primary }

    @Test func exactNamesTakeCuratedColoursOverCrests() {
        #expect(primary("India") == RGB(hex: 0x0057B8))
        // The crest table has New Zealand navy; the curated kit colour is black.
        #expect(primary("New Zealand") == RGB(hex: 0x111111))
        #expect(primary("India Women") == RGB(hex: 0x0057B8))
    }

    @Test func crestTableLoadsFromTheBundle() {
        // 557 names in the file; women's sides fold into their men's key.
        #expect(TeamColorRegistry.extracted.entries.count == 416)
        #expect(primary("AWS World") == RGB(hex: 0xF63505))
    }

    @Test func duplicatesAfterNormalizingKeepTheLaterColour() {
        let table = TeamColorRegistry.Table([("Strikers", (RGB(hex: 0x111111), RGB(hex: 0x111111))),
                                             ("Strikers Women", (RGB(hex: 0x222222), RGB(hex: 0x222222)))])
        #expect(table.entries.count == 1)
        #expect(table["strikers"]?.primary == RGB(hex: 0x222222))
    }

    @Test func aliasesResolveCodesAndFormerNames() {
        #expect(primary("IND") == RGB(hex: 0x0057B8))
        #expect(primary("nz") == RGB(hex: 0x111111))
        #expect(primary("Royal Challengers Bangalore") == RGB(hex: 0xD71920))
        #expect(primary("Kings XI Punjab") == RGB(hex: 0xED1B24))
    }

    @Test func fuzzyMatchIsWholeWordWithAMinimumKey() {
        #expect(primary("India Emerging") == RGB(hex: 0x0057B8))
        #expect(primary("Sri Lanka Development") == RGB(hex: 0x1F3A93))
        let omanOnly = TeamColorRegistry.Table([("Oman", (RGB(hex: 0xDB161B), RGB(hex: 0x008000)))])
        #expect(TeamColorRegistry.fuzzy(omanOnly, "romania") == nil)
        #expect(TeamColorRegistry.fuzzy(omanOnly, "oman a") != nil)
        #expect(primary("Romania") != RGB(hex: 0xDB161B))
        #expect(!TeamColorRegistry.isKnown("LL"))
        #expect(!TeamColorRegistry.isKnown("Nowhere Nomads"))
        #expect(!TeamColorRegistry.isKnown(""))
        #expect(!TeamColorRegistry.isKnown(nil))
    }

    @Test func parseDropsBadHexAndRepeatsAMissingSecondary() {
        let table = TeamColorRegistry.parse(Data(##"{"A Side": ["#112233"], "B Side": ["nope", "#000000"]}"##.utf8))
        #expect(table.entries.count == 1)
        #expect(table["a side"]?.secondary == RGB(hex: 0x112233))
    }

    @Test func segmentColorsNeedBothSidesKnown() {
        #expect(TeamColorRegistry.segmentColors("India", "Nowhere Nomads", scheme: .light, minContrast: 3) == nil)
        #expect(TeamColorRegistry.segmentColors(nil, "India", scheme: .dark, minContrast: 3) == nil)
    }

    @Test(arguments: [ColorScheme.light, .dark])
    func indiaAndNewZealandAreDistinctAndClearTheSurface(scheme: ColorScheme) throws {
        let (india, nz) = try #require(TeamColorRegistry.segmentColors("India", "New Zealand", scheme: scheme, minContrast: 3))
        let surface = RGB(BouncerColor.surface, scheme: scheme)
        #expect(india.contrast(surface) >= 3)
        #expect(nz.contrast(surface) >= 3)
        let distance = ((india.r - nz.r) * (india.r - nz.r) + (india.g - nz.g) * (india.g - nz.g) + (india.b - nz.b) * (india.b - nz.b)).squareRoot()
        #expect(distance > 0.25)
    }
}

struct ContrastTests {
    private let white = RGB(hex: 0xFFFFFF)
    private let black = RGB(hex: 0x000000)
    private let darkSurface = RGB(hex: 0x161C26)

    @Test func luminanceAndRatio() {
        #expect(black.luminance == 0)
        #expect(abs(white.luminance - 1) < 1e-9)
        #expect(abs(white.contrast(black) - 21) < 1e-9)
        #expect(white.contrast(black) == black.contrast(white))
    }

    @Test func ensureContrastStepsAwayFromTheBackground() {
        // New Zealand's near-black is a hair lighter than the dark page, so it must lighten.
        let page = RGB(hex: 0x0C1015)
        let nz = RGB(hex: 0x111111).ensureContrast(page, ratio: 3)
        #expect(nz.contrast(page) >= 3)
        #expect(nz.luminance > RGB(hex: 0x111111).luminance)
        let gold = RGB(hex: 0xFFC72C).ensureContrast(white, ratio: 3)
        #expect(gold.contrast(white) >= 3)
        #expect(gold.luminance < RGB(hex: 0xFFC72C).luminance)
    }

    @Test func liftKeepsHueAndGoesAwayFromTheGround() {
        let bangladesh = RGB(hex: 0x006A4E)
        let lifted = bangladesh.liftForContrast(darkSurface, ratio: 3)
        #expect(lifted.contrast(darkSurface) >= 3)
        #expect(abs(hue(lifted) - hue(bangladesh)) < 0.01)
        #expect(lifted.luminance > bangladesh.luminance)

        let australia = RGB(hex: 0xFFC72C)
        let lowered = australia.liftForContrast(white, ratio: 3)
        #expect(lowered.contrast(white) >= 3)
        #expect(lowered.luminance < australia.luminance)
    }

    @Test func liftLeavesAColourThatAlreadyClears() {
        let india = RGB(hex: 0x0057B8)
        #expect(india.liftForContrast(white, ratio: 3) == india)
    }

    private func hue(_ c: RGB) -> CGFloat {
        var h: CGFloat = 0, s: CGFloat = 0, v: CGFloat = 0, a: CGFloat = 0
        UIColor(red: c.r, green: c.g, blue: c.b, alpha: 1).getHue(&h, saturation: &s, brightness: &v, alpha: &a)
        return h
    }
}
