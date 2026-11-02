import SwiftUI

/// Label (secondary, fixed 112pt) and right-aligned value; figures use tabular digits.
struct StatRow: View {
    let label: String
    let value: String
    var showDivider = true

    private var isFigure: Bool {
        value.trimmingCharacters(in: .whitespaces)
            .range(of: #"^[-+]?\d[\d,]*(\.\d+)?\s?(%|°C|km/h|\*)?$"#, options: .regularExpression) != nil
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
                Text(label).font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                    .frame(width: 112, alignment: .leading)
                Text(value).font(isFigure ? .numeric : .titleSmall).foregroundStyle(BouncerColor.inkPrimary)
                    .tabularFigures().multilineTextAlignment(.trailing)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
            if showDivider { Divider().overlay(BouncerColor.divider) }
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview("Light") {
    VStack(spacing: 0) {
        StatRow(label: "Run rate", value: "6.42"); StatRow(label: "Venue", value: "Wankhede Stadium", showDivider: false)
    }.background(BouncerColor.surface)
}

#Preview("Dark") {
    VStack(spacing: 0) {
        StatRow(label: "Run rate", value: "6.42"); StatRow(label: "Venue", value: "Wankhede Stadium", showDivider: false)
    }.background(BouncerColor.surface).preferredColorScheme(.dark)
}
