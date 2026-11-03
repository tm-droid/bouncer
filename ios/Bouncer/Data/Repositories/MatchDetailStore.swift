import Foundation
import Observation

/// One open match. Holds everything the detail screen shows and keeps it fresh without polling:
///
/// 1. First paint: one `getRow` each for live detail and scorecard, the `mc-{id}` context file and
///    the first commentary page (48 rows), all in parallel and each failing independently.
/// 2. Realtime: row channels for live detail and scorecard, the commentary table channel (filtered
///    to this match client-side). Nothing is polled on a timer.
/// 3. Presence: `bouncer-api /presence` every 90s while the match is open, in the foreground and
///    not over, which is what makes the backend generate commentary for it.
/// 4. Backgrounding: the socket is dropped after 60s. On return it reconnects and, once connected,
///    catches up with one live-detail `getRow` plus a `sort_key > newest` commentary query.
@Observable @MainActor
final class MatchDetailStore {
    enum LoadState: Equatable {
        case idle, loading, loaded
        /// Nothing at all could be loaded; carries a short reason.
        case failed(String)
    }

    struct EventFeed: Equatable {
        var entries: [CommentaryEntry] = []
        var hasMore = true
        var isLoading = false
        var hasLoaded = false
    }

    nonisolated static let presenceInterval: Duration = .seconds(90)
    nonisolated static let backgroundGrace: Duration = .seconds(60)
    /// A fixture further than this from kick-off is not synced yet, so a presence ping buys nothing.
    static let presenceLead: TimeInterval = 15 * 60
    static let catchUpCooldown: TimeInterval = 3

    let matchId: String

    private(set) var summary: MatchSummary?
    private(set) var live: LiveDetailPayload?
    private(set) var liveUpdatedAt: String?
    private(set) var scorecard: Scorecard?
    private(set) var context: MatchContext?
    /// Newest first.
    private(set) var commentary: [CommentaryEntry] = []
    private(set) var hasMoreCommentary = true
    private(set) var isLoadingOlder = false
    private(set) var eventFeeds: [CommentaryFilter: EventFeed] = [:]
    private(set) var loadState: LoadState = .idle
    /// True once the Realtime socket has confirmed this store's subscription.
    private(set) var isRealtimeConnected = false
    /// A 402/breaker is blocking reads; what is on screen is the last good copy.
    private(set) var isQuotaBlocked = false
    private(set) var filter: CommentaryFilter = .all
    /// Win-probability readings from this session's live payloads, oldest first.
    private(set) var sessionProbability: [ProbabilityPoint] = []
    /// Clips matched to this match; empty until `loadHighlights` has run.
    private(set) var highlights: [Highlight] = []

    @ObservationIgnored private let api: any AppwriteAPI
    @ObservationIgnored private let realtime: any RealtimeProviding
    @ObservationIgnored private let presenceInterval: Duration
    @ObservationIgnored private let backgroundGrace: Duration
    @ObservationIgnored private let now: @Sendable () -> Date
    @ObservationIgnored private var started = false
    @ObservationIgnored private var hasLoadedOnce = false
    @ObservationIgnored private var isConnecting = false
    @ObservationIgnored private var isForeground = true
    @ObservationIgnored private var subscription: RealtimeSubscription?
    @ObservationIgnored private var eventTask: Task<Void, Never>?
    @ObservationIgnored private var heartbeatTask: Task<Void, Never>?
    @ObservationIgnored private var dropTask: Task<Void, Never>?
    @ObservationIgnored private var needsCatchUp = false
    @ObservationIgnored private var lastCatchUp = Date.distantPast
    @ObservationIgnored private var highlightsLoaded = false
    @ObservationIgnored private var probabilityInnings: Int?
    @ObservationIgnored private var probabilityOffset = 0.0

    init(matchId: String,
         summary: MatchSummary? = nil,
         api: any AppwriteAPI = AppwriteClient.shared,
         realtime: any RealtimeProviding = RealtimeClient.shared,
         presenceInterval: Duration = MatchDetailStore.presenceInterval,
         backgroundGrace: Duration = MatchDetailStore.backgroundGrace,
         now: @escaping @Sendable () -> Date = { Date() }) {
        self.matchId = matchId
        self.summary = summary
        self.api = api
        self.realtime = realtime
        self.presenceInterval = presenceInterval
        self.backgroundGrace = backgroundGrace
        self.now = now
    }

    // MARK: Derived

    var rowId: String { MatchRowId.sanitize(matchId) }
    /// The live payload is the fresher source; the list summary is a cache snapshot from open time.
    var phase: MatchPhase {
        if let live, !live.status.isEmpty { return live.phase }
        return summary?.phase ?? live?.phase ?? .upcoming
    }
    var isOver: Bool { phase == .finished }

    /// The commentary list for the selected filter.
    var visibleCommentary: [CommentaryEntry] {
        filter == .all ? commentary : (eventFeeds[filter]?.entries ?? [])
    }

    func eventFeed(_ filter: CommentaryFilter) -> EventFeed { eventFeeds[filter] ?? EventFeed() }

    var liveChannel: String { AppwriteConfig.rowChannel(table: AppwriteConfig.Table.liveDetail, rowId: rowId) }
    var scorecardChannel: String { AppwriteConfig.rowChannel(table: AppwriteConfig.Table.scorecard, rowId: rowId) }
    var commentaryChannel: String { AppwriteConfig.tableChannel(table: AppwriteConfig.Table.commentary) }

    /// The two sides' Cricbuzz team ids in screen order, for aligning the server's probability.
    var teamIds: (first: String?, second: String?) {
        if let t = summary?.teams, t.count >= 2 { return (t[0].teamId, t[1].teamId) }
        if let t = context?.teams, t.count >= 2 { return (t[0].id, t[1].id) }
        return (nil, nil)
    }

    func updateSummary(_ new: MatchSummary) { summary = new }

    // MARK: Lifecycle

    /// First paint, then Realtime and presence. Idempotent.
    func start() async {
        guard !started else { return }
        started = true
        if hasLoadedOnce {
            // Reopened: keep what is on screen, reconnect and run the single catch-up path.
            needsCatchUp = true
            await connectRealtime()
            if started { startHeartbeat() }
            return
        }
        loadState = .loading
        async let l: Void = refreshLive()
        async let s: Void = refreshScorecard()
        async let c: Void = loadContext()
        async let f: Void = loadFirstCommentaryPage()
        _ = await (l, s, c, f)
        hasLoadedOnce = true
        if live == nil, scorecard == nil, context == nil, commentary.isEmpty {
            loadState = .failed(isQuotaBlocked ? "quota" : "no data")
        } else {
            loadState = .loaded
        }
        guard started else { return }
        await connectRealtime()
        if started { startHeartbeat() }
    }

    /// Releases the socket and timers. Call when the screen goes away.
    func stop() async {
        started = false
        dropTask?.cancel(); dropTask = nil
        heartbeatTask?.cancel(); heartbeatTask = nil
        await disconnectRealtime()
    }

    /// Scene phase hook: `active == false` starts the 60s grace before the socket is dropped.
    func setActive(_ active: Bool) async {
        isForeground = active
        guard started else { return }
        if active {
            dropTask?.cancel(); dropTask = nil
            if subscription == nil {
                needsCatchUp = true
                await connectRealtime()
            }
        } else {
            dropTask?.cancel()
            let grace = backgroundGrace
            dropTask = Task { [weak self] in
                try? await Task.sleep(for: grace)
                guard !Task.isCancelled, let self else { return }
                await self.dropForBackground()
            }
        }
    }

    private func dropForBackground() async {
        guard !isForeground else { return }
        needsCatchUp = true
        await disconnectRealtime()
    }

    // MARK: Reads

    func refreshLive() async {
        do {
            let data = try await api.getRow(table: AppwriteConfig.Table.liveDetail, id: rowId)
            applyLive(try WireJSON.decode(LiveDetailRow.self, from: data))
            isQuotaBlocked = false
        } catch { record(error) }
    }

    func refreshScorecard() async {
        do {
            let data = try await api.getRow(table: AppwriteConfig.Table.scorecard, id: rowId)
            applyScorecard(try WireJSON.decode(ScorecardRow.self, from: data))
            isQuotaBlocked = false
        } catch { record(error) }
    }

    /// One `highlights` read per store, for matches that have started. Later calls are no-ops, so a
    /// failed read is not retried while the screen stays open.
    func loadHighlights(for target: HighlightTarget?) async {
        guard let target, phase != .upcoming, !highlightsLoaded else { return }
        highlightsLoaded = true
        do {
            highlights = try await HighlightsRepository(api: api).clips(for: target)
            isQuotaBlocked = false
        } catch { record(error) }
    }

    private func loadContext() async {
        do {
            let data = try await api.download(file: MatchRowId.contextFile(matchId))
            context = try WireJSON.decode(MatchContext.self, from: data)
        } catch { record(error) }
    }

    private func loadFirstCommentaryPage() async {
        do {
            let (page, rawCount) = try await fetchCommentaryPage(CommentaryQueries.feed(matchId: matchId))
            merge(page)
            hasMoreCommentary = rawCount >= CommentaryQueries.firstPageLimit
        } catch { record(error) }
    }

    /// The next page back, strictly older than the oldest loaded line.
    func loadOlder() async {
        guard !isLoadingOlder, hasMoreCommentary, let oldest = commentary.last?.sortKey else { return }
        isLoadingOlder = true
        defer { isLoadingOlder = false }
        do {
            let (page, rawCount) = try await fetchCommentaryPage(CommentaryQueries.feed(matchId: matchId, olderThan: oldest))
            merge(page)
            hasMoreCommentary = rawCount >= CommentaryQueries.pageLimit
        } catch { record(error) }
    }

    /// Selects a filter chip. Wickets and boundaries ask the server for just those rows (60 per page)
    /// instead of paging the whole match in to find them.
    func setFilter(_ new: CommentaryFilter) async {
        filter = new
        guard new != .all, !eventFeed(new).hasLoaded else { return }
        await loadMoreEvents(new)
    }

    func loadMoreEvents(_ filter: CommentaryFilter) async {
        guard let values = filter.eventValues else { return }
        var feed = eventFeed(filter)
        guard !feed.isLoading, feed.hasMore else { return }
        feed.isLoading = true
        eventFeeds[filter] = feed
        do {
            let (page, rawCount) = try await fetchCommentaryPage(
                CommentaryQueries.events(matchId: matchId, events: values, olderThan: feed.entries.last?.sortKey))
            var updated = eventFeed(filter)
            updated.entries = Self.mergeSorted(updated.entries, page)
            updated.hasMore = rawCount >= CommentaryQueries.eventPageLimit
            updated.hasLoaded = true
            updated.isLoading = false
            eventFeeds[filter] = updated
        } catch {
            var failed = eventFeed(filter)
            failed.isLoading = false
            eventFeeds[filter] = failed
            record(error)
        }
    }

    private func fetchCommentary(_ queries: [Query]) async throws -> [CommentaryEntry] {
        try await fetchCommentaryPage(queries).rows
    }

    /// Filtered rows plus the raw row count, which is what tells whether the server has more.
    private func fetchCommentaryPage(_ queries: [Query]) async throws -> (rows: [CommentaryEntry], rawCount: Int) {
        let data = try await api.listRows(table: AppwriteConfig.Table.commentary, queries: queries)
        let list = try WireJSON.decode(RowList<CommentaryEntry>.self, from: data)
        isQuotaBlocked = false
        return (list.rows.filter { !$0.text.isEmpty && $0.sortKey != 0 }, list.rows.count)
    }

    /// What the feed missed while the socket was down: one live-detail row and the commentary newer
    /// than the newest line held. Bills only the rows missed.
    func catchUp() async {
        guard now().timeIntervalSince(lastCatchUp) >= Self.catchUpCooldown else { return }
        lastCatchUp = now()
        async let l: Void = refreshLive()
        async let c: Void = catchUpCommentary()
        _ = await (l, c)
    }

    private func catchUpCommentary() async {
        do {
            let page: [CommentaryEntry]
            if let newest = commentary.first?.sortKey {
                page = try await fetchCommentary(CommentaryQueries.catchUp(matchId: matchId, newerThan: newest))
            } else {
                page = try await fetchCommentary(CommentaryQueries.feed(matchId: matchId))
            }
            merge(page)
        } catch { record(error) }
    }

    // MARK: Realtime

    private func connectRealtime() async {
        guard subscription == nil, !isConnecting else { return }
        isConnecting = true
        defer { isConnecting = false }
        let sub = await realtime.subscribe(channels: [liveChannel, scorecardChannel, commentaryChannel])
        guard started else {
            await realtime.unsubscribe(sub.id)
            return
        }
        subscription = sub
        let realtime = self.realtime
        eventTask = Task { [weak self] in
            for await event in sub.events {
                guard let self else { break }
                await self.handle(event)
            }
            await realtime.unsubscribe(sub.id)
        }
    }

    private func disconnectRealtime() async {
        eventTask?.cancel(); eventTask = nil
        isRealtimeConnected = false
        if let sub = subscription {
            subscription = nil
            await realtime.unsubscribe(sub.id)
        }
    }

    private func handle(_ event: RealtimeEvent) async {
        switch event {
        case let .connected(isRecovery):
            isRealtimeConnected = true
            if needsCatchUp || isRecovery {
                needsCatchUp = false
                await catchUp()
            }
        case let .message(message):
            apply(message)
        }
    }

    /// Applies one Realtime message. Internal so tests can drive it directly.
    func apply(_ message: RealtimeMessage) {
        guard message.isWrite else { return }
        if message.matches(channel: liveChannel) {
            if let row = try? WireJSON.decode(LiveDetailRow.self, from: message.payload) { applyLive(row) }
        } else if message.matches(channel: scorecardChannel) {
            if let row = try? WireJSON.decode(ScorecardRow.self, from: message.payload) { applyScorecard(row) }
        } else if message.matches(channel: commentaryChannel) {
            guard let entry = try? WireJSON.decode(CommentaryEntry.self, from: message.payload),
                  entry.matchId == matchId, !entry.text.isEmpty, entry.sortKey != 0 else { return }
            merge([entry])
        }
    }

    private func applyLive(_ row: LiveDetailRow) {
        guard row.matchId.isEmpty || row.matchId == matchId, let payload = row.decodePayload() else { return }
        live = payload
        liveUpdatedAt = row.updatedAt
        recordProbability(payload)
    }

    /// Keeps the session's win-probability line: one point per ball count, in the screen's team order.
    /// A new innings continues after the last over of the one before.
    private func recordProbability(_ payload: LiveDetailPayload) {
        guard let p = payload.winProbabilityTeam1, let balls = payload.situation?.ballsBowled else { return }
        let innings = payload.situation?.inningsId
        if innings != probabilityInnings {
            if probabilityInnings != nil { probabilityOffset = sessionProbability.last?.over ?? probabilityOffset }
            probabilityInnings = innings
        }
        let ids = teamIds
        let flip = ProbabilityHistory.isFlipped(serverTeam1Id: payload.team1Id, firstId: ids.first, secondId: ids.second)
        let point = ProbabilityPoint(over: probabilityOffset + Double(balls) / 6, team1: min(max(flip ? 1 - p : p, 0), 1))
        if let last = sessionProbability.last, last.over == point.over {
            sessionProbability[sessionProbability.count - 1] = point
        } else {
            sessionProbability.append(point)
        }
    }

    private func applyScorecard(_ row: ScorecardRow) {
        guard row.matchId.isEmpty || row.matchId == matchId, let card = row.decodeScorecard() else { return }
        scorecard = card
    }

    // MARK: Merge

    private func merge(_ incoming: [CommentaryEntry]) {
        guard !incoming.isEmpty else { return }
        commentary = Self.mergeSorted(commentary, incoming)
        for (kind, feed) in eventFeeds where feed.hasLoaded {
            let matching = incoming.filter(kind.matches)
            if !matching.isEmpty {
                var updated = feed
                updated.entries = Self.mergeSorted(feed.entries, matching)
                eventFeeds[kind] = updated
            }
        }
    }

    /// Union by `sort_key` (newer data wins), newest first.
    nonisolated static func mergeSorted(_ existing: [CommentaryEntry], _ incoming: [CommentaryEntry]) -> [CommentaryEntry] {
        if incoming.count == 1, let one = incoming.first {
            if existing.isEmpty || one.sortKey > existing[0].sortKey { return [one] + existing }
        }
        var byKey: [Int64: CommentaryEntry] = [:]
        for e in existing { byKey[e.sortKey] = e }
        for e in incoming { byKey[e.sortKey] = e }
        return byKey.values.sorted { $0.sortKey > $1.sortKey }
    }

    // MARK: Presence

    private func startHeartbeat() {
        heartbeatTask?.cancel()
        let interval = presenceInterval
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, !self.isOver else { return }
                if self.isForeground, self.shouldPing() { await self.sendPresence() }
                try? await Task.sleep(for: interval)
            }
        }
    }

    private func shouldPing() -> Bool {
        guard phase == .upcoming else { return true }
        let start = summary?.startsAt ?? context?.startMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) }
        guard let start else { return true }
        return start.timeIntervalSince(now()) <= Self.presenceLead
    }

    private func sendPresence() async {
        let body = "{\"matchIds\":[\(WireJSON.quote(matchId))]}"
        do { _ = try await api.execute(path: "/presence", body: body) } catch { record(error) }
    }

    private func record(_ error: Error) {
        guard let e = error as? AppwriteError else { return }
        if e.isQuota { isQuotaBlocked = true }
    }
}
