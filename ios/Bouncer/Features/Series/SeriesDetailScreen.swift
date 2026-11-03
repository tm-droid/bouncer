import SwiftUI

/// Series detail. Shows the series' matches until Schedule / Points table / Stats land.
struct SeriesDetailScreen: View {
    let seriesId: String

    var body: some View {
        SeriesMatchesScreen(seriesId: seriesId)
    }
}
