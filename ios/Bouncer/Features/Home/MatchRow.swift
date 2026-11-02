import SwiftUI

/// Pure helpers for how a match reads on a row.
enum MatchRowText {
    /// "2nd Test · England tour of Pakistan" (Android matchHeaderText).
    static func header(_ m: MatchSummary) -> String {
        let fmt = MatchClassifier.format(m)
        let desc: String
        if m.matchDesc.isEmpty { desc = fmt }
        else if fmt.isEmpty || m.matchDesc.localizedCaseInsensitiveContains(fmt) { desc = m.matchDesc }
        else { desc = "\(m.matchDesc), \(fmt)" }
        return [desc, m.seriesName].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    /// Won/lost per side for a finished match, read from the status line; undecided if it names neither.
    static func result(_ m: MatchSummary, team: TeamScore) -> RowResult {
        guard m.phase == .finished else { return .undecided }
        let status = m.status.lowercased()
        func named(_ t: TeamScore) -> Bool {
            let words = Set(status.components(separatedBy: CharacterSet.alphanumerics.inverted))
            return status.contains(t.name.lowercased()) || words.contains(t.acronym.lowercased())
        }
        let winners = m.teams.filter(named)
        guard winners.count == 1, status.contains("won") else { return .undecided }
        return winners[0].name == team.name ? .won : .lost
    }

    static func spoken(_ t: TeamScore) -> String {
        var s = t.name
        if t.hasScore { s += " " + t.score.replacingOccurrences(of: "/", with: " for ").replacingOccurrences(of: "&", with: "and") }
        if !t.overs.isEmpty && t.overs != "0.0" { s += " in \(t.overs) overs" }
        return s
    }

    static func day(_ date: Date, now: Date = .now) -> String {
        let cal = Calendar.current
        if cal.isDateInToday(date) { return "Today" }
        if cal.isDateInTomorrow(date) { return "Tomorrow" }
        if cal.isDateInYesterday(date) { return "Yesterday" }
        return date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
    }

    static func countdown(to start: Date, now: Date) -> String? {
        let ahead = start.timeIntervalSince(now)
        guard ahead < 24 * 3600 else { return nil }
        if ahead < 60 { return "Starting now" }
        let mins = Int(ahead / 60)
        return mins < 60 ? "in \(mins) min" : "in \(mins / 60)h \(mins % 60)m"
    }
}

/// Team rows + status for one match; shared by list rows and the hero card.
struct MatchContent: View {
    let match: MatchSummary
    var isFavourite = false

    private var live: Bool { match.phase == .live }
    private var unstarted: Bool { match.teams.allSatisfy { !$0.hasScore } && match.phase == .upcoming }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            header
            HStack(alignment: .center, spacing: Spacing.md) {
                VStack(spacing: Spacing.sm) {
                    ForEach(Array(match.teams.prefix(2).enumerated()), id: \.offset) { _, t in teamRow(t) }
                }
                if unstarted, let start = match.startsAt { StartBlock(start: start) }
            }
            if !match.status.isEmpty && !(unstarted && match.startsAt != nil) {
                Text(match.status)
                    .font(match.phase == .finished ? .bodyMedium : .bodySmall)
                    .foregroundStyle(BouncerColor.inkPrimary)
                    .lineLimit(2).frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spoken)
    }

    private var header: some View {
        HStack(spacing: 6) {
            if isFavourite {
                Image(systemName: "star.fill").font(.system(size: 12)).foregroundStyle(BouncerColor.goldInk)
                    .accessibilityHidden(true)
            }
            Text(MatchRowText.header(match)).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
            Spacer(minLength: Spacing.sm)
            if live { StatusBadge(status: .live) }
        }
        .frame(minHeight: 24)
    }

    private func teamRow(_ t: TeamScore) -> some View {
        let url = TeamLogoLoader.shared.getLogoUrl(for: t.name).flatMap(URL.init(string:))
        return TeamRow(name: t.name, acronym: t.acronym, score: t.score, overs: t.overs, isBatting: live && t.isBatting,
                       yetToBat: live && !t.hasScore && match.teams.contains(where: \.hasScore),
                       result: MatchRowText.result(match, team: t), imageURL: url)
    }

    private var spoken: String {
        var parts = [MatchRowText.header(match)]
        if match.teams.count >= 2 {
            parts.append(match.teams.prefix(2).map(MatchRowText.spoken).joined(separator: " versus "))
        }
        if live { parts.append("Live") }
        if !match.status.isEmpty { parts.append(match.status) }
        else if let s = match.startsAt { parts.append("Starts \(MatchRowText.day(s)) \(s.formatted(date: .omitted, time: .shortened))") }
        if isFavourite { parts.append("Favourite team") }
        return parts.joined(separator: ". ")
    }
}

/// Countdown inside 24h, else clock time over day.
private struct StartBlock: View {
    let start: Date

    var body: some View {
        TimelineView(.everyMinute) { ctx in
            let time = start.formatted(date: .omitted, time: .shortened)
            VStack(alignment: .trailing, spacing: 2) {
                if let c = MatchRowText.countdown(to: start, now: ctx.date) {
                    Text(c).font(.scoreMedium).foregroundStyle(BouncerColor.inkPrimary)
                    Text(time).font(.numericSmall).foregroundStyle(BouncerColor.inkSecondary)
                } else {
                    Text(time).font(.scoreMedium).foregroundStyle(BouncerColor.inkPrimary)
                    Text(MatchRowText.day(start)).font(.numericSmall).foregroundStyle(BouncerColor.inkSecondary)
                }
            }
            .lineLimit(1).minimumScaleFactor(0.7).fixedSize(horizontal: true, vertical: false)
        }
    }
}

/// Edge-to-edge tappable row with a trailing hairline.
struct MatchRow: View {
    let match: MatchSummary
    var isFavourite = false

    var body: some View {
        NavigationLink(value: Route.match(id: match.id)) {
            MatchContent(match: match, isFavourite: isFavourite)
                .padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.rowV)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(BouncerColor.surface)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        Divider().overlay(BouncerColor.divider)
    }
}
