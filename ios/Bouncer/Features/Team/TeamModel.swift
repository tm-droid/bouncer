import Foundation
import Observation

/// Loads the matches, history records and last XI for one team page.
@Observable @MainActor
final class TeamModel {
    private(set) var current: [MatchSummary] = []
    private(set) var results: [MatchSummary] = []
    private(set) var loaded = false
    private(set) var records: [FormatRecord] = []
    private(set) var lastEleven: [SquadPlayer] = []

    var form: [FormResult] {
        Array(results.compactMap { TeamPage.formResult($0, team: team) }.prefix(TeamPage.formLength))
    }

    private let team: String

    init(team: String) { self.team = team }

    /// All three lists, cache first; then the last XI from the newest result.
    func loadMatches(repo: MatchRepository, squads: SquadsRepository = .shared, force: Bool = false) async {
        let kinds = MatchListKind.allCases
        if !loaded {
            var cached: [MatchSummary] = []
            for k in kinds { cached += await repo.cached(k)?.matches ?? [] }
            if !cached.isEmpty { apply(cached) }
        }
        let fresh = await repo.refreshAll(forceRefresh: force)
        guard !Task.isCancelled else { return }
        let all = kinds.flatMap { fresh[$0]?.matches ?? [] }
        if !all.isEmpty { apply(all) }
        loaded = true
        guard let last = results.first else { lastEleven = []; return }
        let xi = TeamPage.lastEleven(last, team: team, squads: await squads.squads(matchId: last.id))
        guard !Task.isCancelled else { return }
        lastEleven = xi
    }

    func loadRecords(repo: TeamRecordsRepository = .shared) async {
        let loaded = await repo.records(for: team) ?? []
        guard !Task.isCancelled else { return }
        records = loaded
    }

    private func apply(_ matches: [MatchSummary]) {
        let split = TeamPage.split(matches, team: team)
        if split.current != current { current = split.current }
        if split.results != results { results = split.results }
    }
}
