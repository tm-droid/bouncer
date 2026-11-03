import SwiftUI

enum FormResult: String, Equatable {
    case won = "W", lost = "L", drawn = "D", tied = "T", noResult = "NR"
}

/// Pure team-page logic: which matches are the team's, its W/L form, and its last XI (TeamForm.kt, TeamLastXi.kt).
enum TeamPage {
    static let maxResults = 10
    static let formLength = 5

    /// Case, spacing and punctuation blind: Cricbuzz spells one side "KwaZulu-Natal Inland - Tuskers" in
    /// the team row and "KwaZulu - Natal Inland - Tuskers" in the status line.
    static func sameTeamName(_ a: String, _ b: String) -> Bool {
        func key(_ s: String) -> String { s.filter { $0.isLetter || $0.isNumber }.lowercased() }
        let k = key(a)
        return !k.isEmpty && k == key(b)
    }

    /// `name` is the team, or `team` is its acronym ("IND"), which a favourite can be.
    static func isTeam(_ name: String, _ team: String) -> Bool {
        sameTeamName(name, team) || (team.count <= 4 && TeamAcronym.make(name).caseInsensitiveCompare(team) == .orderedSame)
    }

    /// The full name for an acronym "team": the shortest `known` name that abbreviates to it, else `team` unchanged.
    static func fullName(_ team: String, known: [String]) -> String {
        if known.contains(where: { sameTeamName($0, team) }) { return team }
        let hits = known.filter { isTeam($0, team) }
        return hits.min { ($0.count, $0) < ($1.count, $1) } ?? team
    }

    private static func plays(_ match: MatchSummary, _ team: String) -> Bool {
        match.teams.contains { isTeam($0.name, team) }
    }

    /// `team`'s result in a finished match, read from the status line ("India won by 5 wkts", "Match drawn").
    /// Nil when the line names a winner that is neither side as the app spells it, because a guessed W/L
    /// is worse than a missing one.
    static func formResult(_ match: MatchSummary, team: String) -> FormResult? {
        guard match.phase == .finished, match.teams.contains(where: { isTeam($0.name, team) }) else { return nil }
        let status = match.status.trimmingCharacters(in: .whitespaces)
        // "Match tied (India won the Super Over)" names a winner, but the result is a tie.
        if status.lowercased().hasPrefix("match tied") { return .tied }
        guard let m = status.firstMatch(of: #/^(.+?)\s+won\b/#.ignoresCase()) else { return undecided(status.lowercased()) }
        let winner = String(m.1).trimmingCharacters(in: .whitespaces)
        if isTeam(winner, team) { return .won }
        return match.teams.contains { sameTeamName($0.name, winner) } ? .lost : nil
    }

    private static func undecided(_ lower: String) -> FormResult? {
        if lower.contains("drawn") || lower.hasPrefix("draw") { return .drawn }
        if lower.contains("tied") { return .tied }
        if lower.contains("no result") || lower.contains("abandoned") { return .noResult }
        return nil
    }

    /// Unfinished matches of `team` (live first by start time), and its finished ones newest first, undated last.
    static func split(_ matches: [MatchSummary], team: String) -> (current: [MatchSummary], results: [MatchSummary]) {
        var seen = Set<String>()
        let mine = matches.filter { plays($0, team) && seen.insert($0.id).inserted }
        func start(_ m: MatchSummary) -> Date { m.startsAt ?? .distantFuture }
        let current = mine.filter { $0.phase != .finished }.sorted { start($0) < start($1) }
        let results = mine.filter { $0.phase == .finished }
            .sorted { ($0.startsAt ?? .distantPast) > ($1.startsAt ?? .distantPast) }
        return (current, Array(results.prefix(maxResults)))
    }

    /// The eleven `team` named for `match`, or empty. The side is picked out of the match by name, then
    /// joined to the team sheet on its Cricbuzz team id, never on names (one page says "AFG", the other "Afghanistan").
    static func lastEleven(_ match: MatchSummary, team: String, squads: Squads?) -> [SquadPlayer] {
        guard let id = match.teams.first(where: { isTeam($0.name, team) })?.teamId,
              !id.isEmpty, let squads else { return [] }
        return [squads.team1, squads.team2].first { $0.teamId == id }?.playingXI ?? []
    }
}

/// Recent results as letters, newest first. Neutral by design: a win is solid, a loss an outline, the rest soft.
struct FormStrip: View {
    let results: [FormResult]

    var body: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(Array(results.enumerated()), id: \.offset) { _, r in
                Text(r.rawValue).font(.numericSmall.weight(.semibold)).lineLimit(1)
                    .foregroundStyle(r == .won ? BouncerColor.surface : BouncerColor.inkPrimary)
                    .frame(width: 24, height: 24)
                    .background(r == .won ? BouncerColor.inkPrimary : r == .lost ? BouncerColor.surface : BouncerColor.surfaceVariant,
                                in: RoundedRectangle(cornerRadius: Radius.xs))
                    .overlay {
                        if r == .lost { RoundedRectangle(cornerRadius: Radius.xs).strokeBorder(BouncerColor.border, lineWidth: 1) }
                    }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Recent form, newest first: " + results.map(\.rawValue).joined(separator: ", "))
    }
}

#Preview("Light") {
    FormStrip(results: [.won, .lost, .drawn, .won, .noResult]).padding().background(BouncerColor.bg)
}

#Preview("Dark") {
    FormStrip(results: [.won, .lost, .drawn, .won, .noResult]).padding().background(BouncerColor.bg).preferredColorScheme(.dark)
}
