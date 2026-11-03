import SwiftUI
import WidgetKit

@main
struct BouncerWidgetBundle: WidgetBundle {
    init() { BouncerFonts.register() }

    var body: some Widget {
        BouncerMatchWidget()
        BouncerLockScreenWidget()
        MatchLiveActivityWidget()
    }
}
