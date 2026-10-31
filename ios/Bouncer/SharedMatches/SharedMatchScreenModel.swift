import Foundation
import Appwrite
import SwiftUI

@MainActor
final class SharedMatchScreenModel: ObservableObject {
    let match: SharedMatch
    private let repository: any SharedMatchServing
    @Published var envelope: SharedMatchEnvelope?
    @Published var scorecard: SharedJSON?
    @Published var commentary: [SharedCommentary] = []
    @Published var loading = false
    @Published var loadingScorecard = false
    @Published var error: String?
    @Published var commentaryError: String?
    @Published var scorecardError: String?
    @Published var loadingOlder = false
    @Published var hasOlder = true
    private(set) var visibleSession: UUID?
    private var streams: [SharedMatchStream] = []
    static let heartbeatNanoseconds: UInt64 = 90_000_000_000

    init(match: SharedMatch, repository: any SharedMatchServing) {
        self.match = match
        self.repository = repository
        envelope = repository.savedDetail(match.id)
        if match.isTerminal, envelope?.summary?.isTerminal != true { envelope?.summary = match; envelope?.state = match.state }
        scorecard = envelope?.scorecard
        commentary = repository.savedCommentary(match.id)
    }
    var displayMatch: SharedMatch {
        var summary = envelope?.summary ?? match
        guard summary.isLive, !summary.isTerminal, !terminalState(envelope?.state), let detail = envelope?.detail else { return summary }
        if !detail["status"].text.isEmpty {
            summary = SharedMatch(id: summary.id, title: summary.title, status: detail["status"].text, state: summary.state,
                matchFormat: summary.matchFormat, seriesName: summary.seriesName, matchDesc: summary.matchDesc, startDate: summary.startDate, teams: summary.teams)
        }
        summary.teams = summary.teams?.map { team in
            var result = team
            for side in 1...2 {
                let id = detail["team\(side)Id"].text
                let name = detail["team\(side)Name"].text
                guard (!id.isEmpty && team.teamId == id) || (!name.isEmpty && team.name == name) else { continue }
                let score = detail["team\(side)Score"].text
                if !score.isEmpty { result.score = score; result.overs = detail["team\(side)Overs"].text }
            }
            return result
        }
        return summary
    }
    private func terminalState(_ state: String?) -> Bool {
        ["complete", "completed", "result", "terminal", "abandoned", "cancelled"].contains(state?.lowercased() ?? "")
    }
    var shouldFollowLive: Bool {
        let summary = envelope?.summary ?? match
        let status = envelope?.detail?["status"].text.lowercased() ?? ""
        let finalStatus = ["won by", "match drawn", "match tied", "abandoned", "no result", "match cancelled"].contains { status.contains($0) }
        return summary.isLive && !summary.isTerminal && !terminalState(envelope?.state) && !finalStatus
    }
    var displayedCommentary: [SharedCommentary] {
        SharedCommentary.merging(table: commentary, raw: (envelope?.commentary ?? []).compactMap {
            SharedCommentary.fromRaw($0, matchId: match.id)
        })
    }
    var stale: Bool {
        guard let envelope else { return false }
        guard let timestamp = envelope.fetchedAt else { return true }
        return envelope.stale || Date().timeIntervalSince1970 - timestamp / 1000 > (match.isLive ? 60 : 600)
    }
    func refresh() async {
        loading = true
        defer { loading = false }
        do {
            let value = try await repository.detail(match)
            try Task.checkCancellation()
            envelope = value
            error = nil
            if !shouldFollowLive { stop() }
        } catch is CancellationError { return }
        catch { self.error = error.localizedDescription }
    }
    func loadScorecard() async {
        loadingScorecard = true
        defer { loadingScorecard = false }
        do {
            let value = try await repository.scorecard(match)
            try Task.checkCancellation()
            scorecard = value?.value
            if var envelope, let value {
                envelope.scorecard = value.value
                envelope.availability["scorecard"] = SharedAvailability(available: true, fetchedAt: value.fetchedAt, stale: value.stale)
                envelope.stale = envelope.availability.values.contains { $0.available && $0.stale }
                self.envelope = envelope
                repository.save(envelope)
            }
            scorecardError = nil
        } catch is CancellationError { return }
        catch { scorecardError = error.localizedDescription }
    }
    func loadOlder() async {
        guard !loadingOlder, hasOlder, let oldest = commentary.last?.sort_key else { return }
        loadingOlder = true
        defer { loadingOlder = false }
        do {
            let lines = try await repository.commentary(match.id, olderThan: oldest, newerThan: nil)
            try Task.checkCancellation()
            merge(lines)
            hasOlder = lines.count == 150
            commentaryError = nil
        } catch is CancellationError { return }
        catch { commentaryError = error.localizedDescription }
    }
    private func merge(_ lines: [SharedCommentary]) {
        var indexed = Dictionary(commentary.map { ($0.id, $0) }, uniquingKeysWith: { _, new in new })
        for line in lines { indexed[line.id] = line }
        commentary = indexed.values.sorted { $0.sort_key > $1.sort_key }
        repository.saveCommentary(commentary, matchId: match.id)
    }
    /// This task is owned by the visible, foreground detail view. Cancellation closes both streams.
    func runVisible() async {
        let token = UUID()
        visibleSession = token
        defer { if visibleSession == token { stop() } }
        if shouldFollowLive {
            do {
                let handles = try await repository.observe(match.id, onDetail: { [weak self] row in
                    guard let self, self.visibleSession == token, let raw = row.payload,
                          let detail = try? SharedJSON.parse(raw) else { return }
                    let stamp = SharedMatchRepository.rowTime(row.updated_at)
                    let base = self.envelope ?? SharedMatchEnvelope(schemaVersion: 1, matchId: self.match.id, state: self.match.state, summary: self.match,
                        fetchedAt: nil, stale: true, retryAfterSeconds: 0, availability: [:])
                    self.envelope = base.applyingDetail(detail, fetchedAt: stamp, ttl: 60)
                    if let envelope = self.envelope { self.repository.save(envelope) }
                    if !self.shouldFollowLive { self.stop() }
                }, onCommentary: { [weak self] line in
                    guard let self, self.visibleSession == token else { return }
                    self.merge([line])
                })
                if visibleSession == token && !Task.isCancelled { streams = handles }
                else { for handle in handles { await handle.close() } }
            } catch { if !Task.isCancelled { self.error = "Live updates are unavailable. Pull to refresh. " + error.localizedDescription } }
        }
        guard !Task.isCancelled else { return }
        await refresh()
        guard !Task.isCancelled else { return }
        do {
            let lines = try await repository.commentary(match.id, olderThan: nil, newerThan: commentary.first?.sort_key)
            try Task.checkCancellation()
            merge(lines)
            commentaryError = nil
        } catch is CancellationError { return }
        catch { commentaryError = error.localizedDescription }
        guard shouldFollowLive, visibleSession == token else { return }
        do {
            while !Task.isCancelled, shouldFollowLive, visibleSession == token {
                do { try await repository.presence(match.id) }
                catch { if !Task.isCancelled { self.error = error.localizedDescription } }
                try await Task.sleep(nanoseconds: Self.heartbeatNanoseconds)
            }
        } catch { /* View lifecycle cancellation ends presence and streams. */ }
    }
    func stop() {
        visibleSession = nil
        let handles = streams
        streams = []
        Task { for handle in handles { await handle.close() } }
    }
}
