import Foundation

/// One side as the header shows it.
struct HeroTeam: Identifiable, Equatable {
    var id: Int
    var name: String
    var acronym: String
    var score: String
    var overs: String
    var isBatting: Bool
    var logoURL: URL?

    var hasScore: Bool { !score.isEmpty }
    /// Each innings, oldest first.
    var innings: [String] { ScoreFormat.splitInnings(score) }
    var latestInnings: String { innings.last ?? "" }
    var earlierInnings: String { innings.count > 1 ? innings.dropLast().joined(separator: " & ") + " &" : "" }
}

struct WinProbability: Equatable {
    var team1: Double
    var team2: Double
    var draw: Double?
}

/// Everything the header needs, merged from the list summary, the live row and the mc- context.
struct HeroModel: Equatable {
    var teams: [HeroTeam] = []
    var phase: MatchPhase = .upcoming
    var status = ""
    var startsAt: Date?
    var crr = ""
    var rrr = ""
    var chase: String?
    var winProbability: WinProbability?
    var recentBalls: [String] = []

    var navTitle: String {
        teams.count >= 2 ? "\(teams[0].acronym) vs \(teams[1].acronym)" : "Match"
    }

    @MainActor
    init(store: MatchDetailStore) {
        let summary = store.summary
        let live = store.live
        phase = summary?.phase ?? live?.phase ?? .upcoming
        status = [summary?.status, live?.status].compactMap { $0 }.first(where: { !$0.isEmpty }) ?? ""
        startsAt = summary?.startsAt ?? store.context?.startMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) }
        crr = live?.currentRunRate ?? ""
        rrr = live?.requiredRunRate ?? ""
        recentBalls = phase == .live ? (live?.recentBalls ?? []) : []

        teams = Self.buildTeams(summary: summary, live: live, context: store.context)
        chase = Self.chaseLine(status: status, live: live, teams: teams, multiDay: summary?.isMultiDay ?? false)
        if phase == .live, let p1 = live?.winProbabilityTeam1, let p2 = live?.winProbabilityTeam2, p1 + p2 > 0 {
            winProbability = WinProbability(team1: p1, team2: p2, draw: live?.winProbabilityDraw.flatMap { $0 > 0 ? $0 : nil })
        }
    }

    private static func buildTeams(summary: MatchSummary?, live: LiveDetailPayload?, context: MatchContext?) -> [HeroTeam] {
        func logo(_ name: String) -> URL? { TeamLogoLoader.shared.getLogoUrl(for: name).flatMap(URL.init(string:)) }
        let battingIsTeam1 = live?.situation?.battingTeamIsTeam1
        func liveScore(_ idx: Int, id: String) -> (String, String)? {
            guard let live else { return nil }
            let useFirst: Bool
            if !id.isEmpty, live.team1Id == id { useFirst = true }
            else if !id.isEmpty, live.team2Id == id { useFirst = false }
            else { useFirst = idx == 0 }
            let s = useFirst ? live.team1Score : live.team2Score
            let o = useFirst ? live.team1Overs : live.team2Overs
            guard let s, !s.isEmpty else { return nil }
            return (s, o ?? "")
        }
        if let summary, summary.teams.count >= 2 {
            return summary.teams.prefix(2).enumerated().map { i, t in
                let l = liveScore(i, id: t.teamId)
                let batting = battingIsTeam1.map { $0 == (i == 0) } ?? t.isBatting
                return HeroTeam(id: i, name: t.name, acronym: t.acronym, score: l?.0 ?? t.score, overs: l?.1 ?? t.overs,
                                isBatting: batting && summary.phase == .live, logoURL: logo(t.name))
            }
        }
        if let ctx = context, ctx.teams.count >= 2 {
            return ctx.teams.prefix(2).enumerated().map { i, t in
                let l = liveScore(i, id: t.id)
                let code = t.shortName.isEmpty ? TeamAcronym.make(t.name) : t.shortName
                return HeroTeam(id: i, name: t.name, acronym: code, score: l?.0 ?? "", overs: l?.1 ?? "",
                                isBatting: battingIsTeam1.map { $0 == (i == 0) } ?? false, logoURL: logo(t.name))
            }
        }
        if let live, let n1 = live.team1Name, let n2 = live.team2Name {
            return [(0, n1, live.team1Score, live.team1Overs), (1, n2, live.team2Score, live.team2Overs)].map { i, n, s, o in
                HeroTeam(id: i, name: n, acronym: TeamAcronym.make(n), score: s ?? "", overs: o ?? "",
                         isBatting: battingIsTeam1.map { $0 == (i == 0) } ?? false, logoURL: logo(n))
            }
        }
        return []
    }

    /// "India need 54 runs in 36 balls". The status text wins; the typed situation fills in.
    private static func chaseLine(status: String, live: LiveDetailPayload?, teams: [HeroTeam], multiDay: Bool) -> String? {
        if let r = status.range(of: #"(\S.*?)\s+need\s+(\d+)\s+runs?(?:\s+(?:in|off)\s+(\d+)\s+balls?)?"#,
                                options: [.regularExpression, .caseInsensitive]) {
            return String(status[r])
        }
        guard let s = live?.situation else { return nil }
        let needed = s.runsNeeded ?? s.target.flatMap { t in s.runsScored.map { t - $0 } }
        guard let needed, needed > 0 else { return nil }
        let team: String? = s.battingTeamIsTeam1.flatMap { teams.indices.contains($0 ? 0 : 1) ? teams[$0 ? 0 : 1].name : nil } ?? s.battingTeamName
        guard let team, !team.isEmpty else { return nil }
        let runs = "\(needed) \(needed == 1 ? "run" : "runs")"
        if !multiDay, let o = s.oversRemaining, o > 0 {
            let balls = Int((o * 6).rounded())
            return "\(team) need \(runs) in \(balls) \(balls == 1 ? "ball" : "balls")"
        }
        return "\(team) need \(runs) to win"
    }
}
