import SwiftUI
import WidgetKit

struct BouncerMatchWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "BouncerMatchWidget", provider: MatchProvider()) { entry in
            HomeWidgetView(entry: entry)
                .widgetURL(entry.match.map { WidgetBackend.matchURL($0.id) })
                .containerBackground(BouncerColor.bg, for: .widget)
        }
        .configurationDisplayName("Bouncer")
        .description("Your team's live match, or the next one.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct BouncerLockScreenWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "BouncerLockScreenWidget", provider: MatchProvider()) { entry in
            LockWidgetView(entry: entry)
                .widgetURL(entry.match.map { WidgetBackend.matchURL($0.id) })
                .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Bouncer score")
        .description("Live score on the lock screen.")
        .supportedFamilies([.accessoryRectangular, .accessoryInline])
    }
}

private extension WidgetMatch {
    /// What the status area shows: the kick-off time for a match that has not started, else the status text.
    var statusText: String {
        if phase == .upcoming, let startsAt {
            return startsAt.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute())
        }
        return status
    }
    var subtitle: String { [format, series].filter { !$0.isEmpty }.joined(separator: " · ") }
}

private struct HomeWidgetView: View {
    let entry: MatchEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if let match = entry.match {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                header(match)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    TeamLine(team: match.team1, compact: family == .systemSmall)
                    TeamLine(team: match.team2, compact: family == .systemSmall)
                }
                Spacer(minLength: 0)
                Text(match.statusText)
                    .font(.bouncer(12, .regular, relativeTo: .caption))
                    .foregroundStyle(BouncerColor.inkSecondary)
                    .lineLimit(family == .systemSmall ? 2 : 1)
            }
        } else {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("BOUNCER").font(.overline).tracking(1.2).foregroundStyle(BouncerColor.goldInk)
                Spacer(minLength: 0)
                Text("No matches right now").font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary)
                Text("Check back soon").font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func header(_ match: WidgetMatch) -> some View {
        HStack(spacing: Spacing.xs) {
            if match.phase == .live {
                Circle().fill(BouncerColor.live).frame(width: 6, height: 6)
                Text("LIVE").font(.overline).foregroundStyle(BouncerColor.live)
            }
            Text(family == .systemSmall ? match.format : match.subtitle)
                .font(.overline).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
        }
    }
}

private struct TeamLine: View {
    let team: WidgetTeam?
    let compact: Bool

    var body: some View {
        if let team {
            HStack(spacing: Spacing.xs) {
                Text(compact ? team.acronym : team.name)
                    .font(.bouncer(14, team.isBatting ? .semibold : .regular, relativeTo: .subheadline))
                    .foregroundStyle(BouncerColor.inkPrimary).lineLimit(1)
                if team.isBatting { Circle().fill(BouncerColor.inkPrimary).frame(width: 5, height: 5) }
                Spacer(minLength: Spacing.xs)
                VStack(alignment: .trailing, spacing: 0) {
                    Text(team.score).font(.bouncer(compact ? 14 : 16, .bold, relativeTo: .subheadline)).tabularFigures()
                        .foregroundStyle(BouncerColor.inkPrimary).lineLimit(1).minimumScaleFactor(0.7)
                    if !compact, !team.overs.isEmpty {
                        Text("\(team.overs) ov").font(.bouncer(11, .regular, relativeTo: .caption2)).tabularFigures()
                            .foregroundStyle(BouncerColor.inkSecondary)
                    }
                }
            }
        }
    }
}

private struct LockWidgetView: View {
    let entry: MatchEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch (family, entry.match) {
        case (.accessoryInline, let match?):
            Text(inline(match))
        case (.accessoryInline, nil):
            Text("Bouncer: no matches")
        case (_, let match?):
            VStack(alignment: .leading, spacing: 1) {
                Text(line(match.team1)).font(.bouncer(13, .semibold, relativeTo: .subheadline)).tabularFigures()
                Text(line(match.team2)).font(.bouncer(13, .semibold, relativeTo: .subheadline)).tabularFigures()
                Text(match.statusText).font(.bouncer(11, .regular, relativeTo: .caption2)).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        default:
            Text("No matches right now").font(.bouncer(13, .semibold, relativeTo: .subheadline))
        }
    }

    private func line(_ team: WidgetTeam?) -> String {
        guard let team else { return "" }
        return [team.acronym, team.score].filter { !$0.isEmpty }.joined(separator: " ")
    }

    private func inline(_ match: WidgetMatch) -> String {
        guard match.phase == .live, let one = match.team1, let two = match.team2 else {
            return [match.team1?.acronym, "v", match.team2?.acronym].compactMap { $0 }.joined(separator: " ")
        }
        return "\(line(one)) v \(line(two))"
    }
}
