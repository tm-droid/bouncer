import SwiftUI

/// Strip under the nav bar when showing cached data, with a gold Retry.
struct StaleBanner: View {
    var message = "Showing cached data"
    var onRetry: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: Spacing.sm) {
                Text(message).font(.bodySmall).foregroundStyle(BouncerColor.inkPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button(action: onRetry) {
                    Text("Retry").font(.bouncer(14, .semibold, relativeTo: .subheadline)).foregroundStyle(BouncerColor.goldInk)
                        .padding(.horizontal, Spacing.md).frame(minHeight: 44)
                }
            }
            .padding(.leading, Spacing.lg).padding(.trailing, Spacing.xs).frame(minHeight: Spacing.xxl)
            Divider().overlay(BouncerColor.divider)
        }
        .background(BouncerColor.surfaceVariant)
    }
}

#Preview("Light") { StaleBanner(message: "Offline · saved 4:12 PM") {} }
#Preview("Dark") { StaleBanner {}.preferredColorScheme(.dark) }
