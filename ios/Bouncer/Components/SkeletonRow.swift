import SwiftUI

/// Rounded placeholder bar with a sweeping highlight; static when Reduce Motion is on.
struct ShimmerBar: View {
    var width: CGFloat?
    var height: CGFloat = 16
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    var body: some View {
        RoundedRectangle(cornerRadius: Radius.sm)
            .fill(BouncerColor.shimmer)
            .overlay {
                if !reduceMotion {
                    GeometryReader { geo in
                        LinearGradient(colors: [.clear, BouncerColor.shimmerHighlight, .clear], startPoint: .leading, endPoint: .trailing)
                            .frame(width: geo.size.width * 0.6)
                            .offset(x: phase * geo.size.width)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                }
            }
            .frame(width: width, height: height)
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) { phase = 1.2 }
            }
            .accessibilityHidden(true)
    }
}

/// Placeholder shaped like a team row: crest + two lines.
struct SkeletonRow: View {
    var body: some View {
        HStack(spacing: 10) {
            ShimmerBar(width: 28, height: 28).clipShape(Circle())
            VStack(alignment: .leading, spacing: Spacing.sm) {
                ShimmerBar(width: 140, height: 14)
                ShimmerBar(width: 80, height: 12)
            }
            Spacer()
            ShimmerBar(width: 56, height: 20)
        }
        .padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.rowV)
        .accessibilityElement().accessibilityLabel("Loading")
    }
}

#Preview("Light") {
    VStack(spacing: 0) { SkeletonRow(); Divider(); SkeletonRow() }.background(BouncerColor.surface)
}

#Preview("Dark") {
    VStack(spacing: 0) { SkeletonRow(); Divider(); SkeletonRow() }.background(BouncerColor.surface).preferredColorScheme(.dark)
}
