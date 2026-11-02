import SwiftUI

/// Gold "Bouncer" title for the leading edge of the nav bar.
struct BouncerWordmark: View {
    var body: some View {
        Text("Bouncer").font(.bouncer(22, .bold, relativeTo: .title2)).tracking(-0.2)
            .foregroundStyle(BouncerColor.goldInk)
            .accessibilityAddTraits(.isHeader)
    }
}

#Preview("Light") { BouncerWordmark().padding().background(BouncerColor.topBar) }
#Preview("Dark") { BouncerWordmark().padding().background(BouncerColor.topBar).preferredColorScheme(.dark) }
