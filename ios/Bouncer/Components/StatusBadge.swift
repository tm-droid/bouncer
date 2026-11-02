import SwiftUI

enum MatchStatusType { case live, upcoming, completed, unknown }

/// 6pt dot + small caption. LIVE is red; everything else secondary ink.
struct StatusBadge: View {
    let status: MatchStatusType
    var label: String?

    private var tint: Color { status == .live ? BouncerColor.live : BouncerColor.inkSecondary }
    private var text: String {
        if let label { return label }
        switch status {
        case .live: return "LIVE"
        case .upcoming: return "UPCOMING"
        case .completed: return "RESULT"
        case .unknown: return ""
        }
    }

    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(tint).frame(width: 6, height: 6)
            Text(text).font(.bouncer(11, .semibold, relativeTo: .caption2)).foregroundStyle(tint)
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview("Light") {
    VStack(alignment: .leading, spacing: 8) {
        StatusBadge(status: .live); StatusBadge(status: .upcoming); StatusBadge(status: .completed)
    }.padding().background(BouncerColor.bg)
}

#Preview("Dark") {
    VStack(alignment: .leading, spacing: 8) {
        StatusBadge(status: .live); StatusBadge(status: .upcoming, label: "STARTS 7:30 PM")
    }.padding().background(BouncerColor.bg).preferredColorScheme(.dark)
}
