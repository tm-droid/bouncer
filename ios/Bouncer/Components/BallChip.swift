import SwiftUI

/// Pill for one delivery ("4", "6", "W", "1wd", "."), filled by event type.
struct BallChip: View {
    let label: String
    var size: CGFloat = 22
    @Environment(\.colorScheme) private var scheme

    private enum Kind { case wicket, six, four, extra, dot, runs }

    private var kind: Kind {
        let g = label.trimmingCharacters(in: .whitespaces).uppercased()
        if g.range(of: #"^W\d?$"#, options: .regularExpression) != nil { return .wicket }
        if g == "6" { return .six }
        if g == "4" { return .four }
        if g.range(of: #"^(?:WD\d{0,2}|NB|N\d{1,2}|LB|L\d{1,2}|B\d{0,2})$"#, options: .regularExpression) != nil { return .extra }
        if ["0", ".", "–"].contains(g) { return .dot }
        return .runs
    }

    private var colors: (fill: Color, ink: Color) {
        let fill: Color
        switch kind {
        case .wicket: fill = BouncerColor.wicket
        case .six: fill = BouncerColor.six
        case .four: fill = BouncerColor.four
        case .extra: fill = BouncerColor.wide
        case .dot: return (BouncerColor.inkSecondary.opacity(0.3), BouncerColor.inkPrimary)
        case .runs: return (BouncerColor.surfaceVariant, BouncerColor.inkPrimary)
        }
        return (fill, fill.contrastingInk(scheme))
    }

    var body: some View {
        Text(label.trimmingCharacters(in: .whitespaces))
            .font(.bouncer(12, .semibold, relativeTo: .caption)).tabularFigures()
            .foregroundStyle(colors.ink)
            .padding(.horizontal, 5)
            .frame(minWidth: size, minHeight: size)
            .background(colors.fill, in: Capsule())
    }
}

#Preview("Light") {
    HStack(spacing: 4) { ForEach(["1", ".", "4", "6", "W", "1wd", "NB", "2"], id: \.self) { BallChip(label: $0) } }
        .padding().background(BouncerColor.bg)
}

#Preview("Dark") {
    HStack(spacing: 4) { ForEach(["1", ".", "4", "6", "W", "1wd", "NB", "2"], id: \.self) { BallChip(label: $0, size: 24) } }
        .padding().background(BouncerColor.bg).preferredColorScheme(.dark)
}
