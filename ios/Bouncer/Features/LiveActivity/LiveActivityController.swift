import ActivityKit
import Foundation

/// Starts, updates and ends the match Live Activity. There is no push server: the app drives it, so
/// updates only land while the app is running (the integration step feeds `update` from MatchDetailStore).
@MainActor
final class LiveActivityController {
    static let shared = LiveActivityController()

    /// Past this with no update the system marks the activity stale, so a frozen card stops reading as live.
    static let staleAfter: TimeInterval = 5 * 60

    private var activities: [String: Activity<MatchActivityAttributes>] = [:]

    private init() {}

    /// True while an activity for this match is on screen (also finds one that outlived an app relaunch).
    func isActive(matchId: String) -> Bool { activity(for: matchId) != nil }

    /// Starts the activity for a match with two named sides. No-op if one is already running, Live
    /// Activities are off in Settings, or the request fails. Returns whether one is running afterwards.
    @discardableResult
    func start(summary: MatchSummary) -> Bool {
        if isActive(matchId: summary.id) { return true }
        guard ActivityAuthorizationInfo().areActivitiesEnabled, let attributes = Self.attributes(for: summary) else { return false }
        let content = ActivityContent(state: Self.state(for: summary), staleDate: .now + Self.staleAfter)
        guard let activity = try? Activity.request(attributes: attributes, content: content, pushType: nil) else { return false }
        activities[summary.id] = activity
        return true
    }

    /// Folds a live-detail payload into the running activity. A finished match is pushed once more
    /// and then dismissed after a few minutes.
    func update(matchId: String, payload: LiveDetailPayload) {
        guard let activity = activity(for: matchId) else { return }
        let state = Self.state(from: payload, attributes: activity.attributes, previous: activity.content.state)
        guard state != activity.content.state else { return }
        let finished = state.phase == .finished
        Task {
            await activity.update(ActivityContent(state: state, staleDate: .now + Self.staleAfter))
            if finished { await activity.end(nil, dismissalPolicy: .after(.now + 300)) }
        }
        if finished { activities[matchId] = nil }
    }

    func end(matchId: String) {
        guard let activity = activity(for: matchId) else { return }
        activities[matchId] = nil
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }

    private func activity(for matchId: String) -> Activity<MatchActivityAttributes>? {
        if let known = activities[matchId], known.activityState == .active { return known }
        activities[matchId] = nil
        let found = Activity<MatchActivityAttributes>.activities.first { $0.attributes.matchId == matchId && $0.activityState == .active }
        activities[matchId] = found
        return found
    }

    // MARK: - Mapping (pure, unit-tested)

    nonisolated static func attributes(for summary: MatchSummary) -> MatchActivityAttributes? {
        guard let one = summary.team1, let two = summary.team2 else { return nil }
        return MatchActivityAttributes(
            matchId: summary.id, team1Id: one.teamId, team1Name: one.name, team1Acronym: one.acronym,
            team2Id: two.teamId, team2Name: two.name, team2Acronym: two.acronym,
            seriesName: summary.seriesName, matchFormat: summary.matchFormat)
    }

    nonisolated static func state(for summary: MatchSummary) -> MatchActivityAttributes.ContentState {
        let battingSide = summary.teams.firstIndex(where: \.isBatting).map { $0 < 2 ? $0 + 1 : 0 } ?? 0
        return .init(
            team1Score: summary.team1?.score ?? "", team1Overs: summary.team1?.overs ?? "",
            team2Score: summary.team2?.score ?? "", team2Overs: summary.team2?.overs ?? "",
            battingSide: battingSide, statusLine: summary.status, recentBalls: [], phase: activityPhase(summary.phase))
    }

    /// The payload's `team1` is the side that batted first, which need not be the activity's side 1, so
    /// slots are joined to sides by team id, then by short code, and only then by position. Fields the
    /// payload does not carry keep their previous value.
    nonisolated static func state(from payload: LiveDetailPayload, attributes: MatchActivityAttributes,
                                  previous: MatchActivityAttributes.ContentState) -> MatchActivityAttributes.ContentState {
        let swapped = isSwapped(payload, attributes)
        let (s1, o1) = swapped ? (payload.team2Score, payload.team2Overs) : (payload.team1Score, payload.team1Overs)
        let (s2, o2) = swapped ? (payload.team1Score, payload.team1Overs) : (payload.team2Score, payload.team2Overs)

        var state = previous
        state.team1Score = nonEmpty(s1) ?? previous.team1Score
        state.team1Overs = nonEmpty(o1) ?? previous.team1Overs
        state.team2Score = nonEmpty(s2) ?? previous.team2Score
        state.team2Overs = nonEmpty(o2) ?? previous.team2Overs
        if let slot = battingSlot(payload) { state.battingSide = (slot == 1) != swapped ? 1 : 2 }
        if let status = nonEmpty(payload.status) { state.statusLine = status }
        if !payload.recentBalls.isEmpty { state.recentBalls = payload.recentBalls }
        state.phase = activityPhase(payload.phase)
        if state.phase != .live { state.battingSide = 0 }
        return state
    }

    private nonisolated static func isSwapped(_ payload: LiveDetailPayload, _ a: MatchActivityAttributes) -> Bool {
        if let id1 = nonEmpty(payload.team1Id) {
            if id1 == a.team1Id { return false }
            if id1 == a.team2Id { return true }
        }
        if let code = nonEmpty(payload.team1Name)?.lowercased() {
            if code == a.team1Acronym.lowercased() { return false }
            if code == a.team2Acronym.lowercased() { return true }
        }
        return false
    }

    /// 1 or 2: which payload slot is batting. `situation` is authoritative; the innings-score match is the fallback.
    private nonisolated static func battingSlot(_ payload: LiveDetailPayload) -> Int? {
        if let isTeam1 = payload.situation?.battingTeamIsTeam1 { return isTeam1 ? 1 : 2 }
        guard let live = nonEmpty(payload.score)?.components(separatedBy: " (").first else { return nil }
        func latest(_ s: String?) -> String? { s?.components(separatedBy: "&").last?.trimmingCharacters(in: .whitespaces) }
        if latest(payload.team1Score) == live { return 1 }
        if latest(payload.team2Score) == live { return 2 }
        return nil
    }

    private nonisolated static func activityPhase(_ phase: MatchPhase) -> ActivityPhase {
        switch phase {
        case .upcoming: .upcoming
        case .live: .live
        case .finished: .finished
        }
    }

    private nonisolated static func nonEmpty(_ s: String?) -> String? {
        guard let s = s?.trimmingCharacters(in: .whitespaces), !s.isEmpty else { return nil }
        return s
    }
}
