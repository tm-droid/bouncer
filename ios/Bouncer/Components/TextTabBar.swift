import SwiftUI

/// Scrollable text tabs with a 2pt underline under the selected one and a hairline below.
struct TextTabBar: View {
    let tabs: [String]
    @Binding var selection: Int
    var accent: Color = BouncerColor.goldInk
    @Namespace private var underline

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(tabs.indices, id: \.self) { i in
                        let selected = i == selection
                        Button { withAnimation(.snappy(duration: 0.2)) { selection = i } } label: {
                            Text(tabs[i]).font(.labelLarge)
                                .foregroundStyle(selected ? accent : BouncerColor.inkSecondary)
                                .padding(.horizontal, Spacing.lg).frame(minHeight: 48)
                                .overlay(alignment: .bottom) {
                                    if selected {
                                        Rectangle().fill(accent).frame(height: 2)
                                            .padding(.horizontal, Spacing.lg).matchedGeometryEffect(id: "underline", in: underline)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
                .padding(.horizontal, Spacing.xs)
            }
            Divider().overlay(BouncerColor.divider)
        }
        .background(BouncerColor.topBar)
    }
}

#Preview("Light") {
    TextTabBar(tabs: ["Info", "Scorecard", "Commentary", "Squads", "Table"], selection: .constant(1))
}

#Preview("Dark") {
    TextTabBar(tabs: ["Info", "Scorecard", "Commentary", "Squads", "Table"], selection: .constant(0))
        .preferredColorScheme(.dark)
}
