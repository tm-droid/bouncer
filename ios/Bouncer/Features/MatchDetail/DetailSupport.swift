import SwiftUI

enum DetailTab: String, Hashable {
    case live, summary, scorecard, commentary, graphs, squads, info, highlights

    var title: String {
        switch self {
        case .live: "Live"
        case .summary: "Summary"
        case .scorecard: "Scorecard"
        case .commentary: "Commentary"
        case .graphs: "Graphs"
        case .squads: "Squads"
        case .info: "Info"
        case .highlights: "Highlights"
        }
    }

    /// The tabs for a match in `phase`, in order (MatchTabs.kt). The first is the one the screen opens on.
    /// Graphs and Highlights are offered only once they have something to show.
    static func tabs(for phase: MatchPhase, graphs: Bool = false, highlights: Bool = false) -> [DetailTab] {
        switch phase {
        case .upcoming: [.info, .squads, .commentary]
        case .live: [.live, .scorecard, .commentary] + (graphs ? [.graphs] : []) + [.squads, .info] + (highlights ? [.highlights] : [])
        case .finished: [.summary, .scorecard, .commentary] + (graphs ? [.graphs] : []) + (highlights ? [.highlights] : []) + [.squads, .info]
        }
    }
}

/// Section title above a card; the card's content is laid out edge to edge.
struct DetailSection<Content: View>: View {
    let title: String
    var action: String?
    var onAction: (() -> Void)?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader(title: title, action: action, onAction: onAction)
            BouncerCard { content }.padding(.horizontal, Spacing.lg)
        }
        .padding(.bottom, Spacing.sm)
    }
}

/// Fixed-width numeric column that grows with Dynamic Type.
struct StatCell: View {
    let text: String
    var width: CGFloat
    var emphasised = false
    @ScaledMetric(relativeTo: .caption) private var scale: CGFloat = 1

    var body: some View {
        Text(text.isEmpty ? "–" : text)
            .font(emphasised ? .bouncer(14, .semibold, relativeTo: .subheadline) : .numeric)
            .tabularFigures().foregroundStyle(emphasised ? BouncerColor.inkPrimary : BouncerColor.inkSecondary)
            .lineLimit(1).minimumScaleFactor(0.7)
            .frame(width: width * scale, alignment: .trailing)
    }
}

struct DetailMessage: View {
    let title: String
    let message: String
    var systemImage = "tray"
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        EmptyState(title: title, message: message, systemImage: systemImage, actionTitle: actionTitle, action: action)
    }
}

struct DetailSkeleton: View {
    var rows = 6
    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<rows, id: \.self) { _ in SkeletonRow(); Divider().overlay(BouncerColor.divider) }
        }
        .background(BouncerColor.surface)
    }
}

enum CommentaryText {
    /// Strips Cricbuzz markup and bulletin markers from a commentary line.
    static func clean(_ raw: String) -> String {
        var s = raw.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: #"B\d\$"#, with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: #"^\d{1,3}\.\d:\s*"#, with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "&amp;", with: "&").replacingOccurrences(of: "&nbsp;", with: " ")
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Chip label for a delivery, nil when the row has no result data.
    static func chipLabel(_ e: CommentaryEntry) -> String? {
        if e.isWicket { return "W" }
        if let extra = e.extra?.uppercased() {
            let n = e.runs ?? 0
            switch extra {
            case "WIDE": return n > 1 ? "WD\(n - 1)" : "WD"
            case "NOBALL": return "NB"
            case "BYE": return "B\(n)"
            case "LEGBYE": return "L\(n)"
            default: break
            }
        }
        guard let r = e.runs else { return nil }
        return r == 0 ? "." : String(r)
    }

    static func overNumber(_ e: CommentaryEntry) -> Int? {
        guard let ball = e.ball, let dot = ball.firstIndex(of: "."), let over = Int(ball[ball.startIndex..<dot]) else { return nil }
        return over + 1
    }

    static func isMilestone(_ e: CommentaryEntry) -> Bool {
        let t = e.text.lowercased()
        return t.range(of: #"\b(fifty|half-century|half century|century|hundred|five-wicket|five wicket|5-wicket|five-for|fifer|hat-trick|hattrick|\b50\b|\b100\b|\b150\b|\b200\b)"#,
                       options: .regularExpression) != nil
    }
}

/// One delivery: ball number, result chip, text.
struct CommentaryLine: View {
    let entry: CommentaryEntry
    var showBall = true

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            if showBall {
                Text(entry.ball ?? "").font(.numericSmall).tabularFigures().foregroundStyle(BouncerColor.inkSecondary)
                    .frame(width: 38, alignment: .leading)
            }
            if let label = CommentaryText.chipLabel(entry) { BallChip(label: label, size: 24) }
            Text(CommentaryText.clean(entry.text)).font(.bodyMedium).foregroundStyle(BouncerColor.inkPrimary)
                .frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .accessibilityElement(children: .combine)
    }
}

extension Route {
    /// Profile route for a name from match data. Carries the Cricbuzz id once a squad taught it, so
    /// the profile opens directly instead of searching the name (a billed call that can pick a namesake).
    @MainActor static func player(named raw: String) -> Route {
        let name = playerNameWithoutRoleTags(raw)
        return .player(id: PlayerImages.shared.playerId(forName: name), name: name)
    }
}
