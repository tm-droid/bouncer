import Foundation
import Testing
@testable import Bouncer

/// Realtime stub: counts subscribes/unsubscribes, never emits.
final class FakeRealtime: RealtimeProviding, @unchecked Sendable {
    private let lock = NSLock()
    private var _subs = 0, _unsubs = 0
    var subscribes: Int { lock.withLock { _subs } }
    var unsubscribes: Int { lock.withLock { _unsubs } }
    func subscribe(channels: Set<String>) async -> RealtimeSubscription {
        lock.withLock { _subs += 1 }
        return RealtimeSubscription(id: UUID(), events: AsyncStream { _ in })
    }
    func unsubscribe(_ id: UUID) async { lock.withLock { _unsubs += 1 } }
}

@MainActor
struct MatchDetailStoreTests {
    @Test func restartSkipsFirstPaintReads() async {
        let api = FakeAPI()
        let rt = FakeRealtime()
        let store = MatchDetailStore(matchId: "123", api: api, realtime: rt, presenceInterval: .seconds(3600))
        await store.start()
        await store.stop()
        let rows = await api.rowCalls.count, dl = await api.downloadCalls.count, ls = await api.listCalls.count
        #expect(rows == 2 && dl == 1 && ls == 1)
        await store.start()
        #expect(await api.rowCalls.count == rows)
        #expect(await api.downloadCalls.count == dl)
        #expect(await api.listCalls.count == ls)
        #expect(rt.subscribes == 2)
        await store.stop()
        #expect(rt.unsubscribes >= 2) // the event task also unsubscribes on cancel (idempotent)
    }

    @Test func hasMoreUsesRawPageCount() async throws {
        // 48 rows, all with empty text: filtered away client-side but the page was full.
        let row = #"{"match_id":"123","sort_key":5,"commentary":""}"#
        let list = "{\"total\":99,\"rows\":[" + Array(repeating: row, count: CommentaryQueries.firstPageLimit).joined(separator: ",") + "]}"
        let api = FakeAPI()
        await api.set(list: AppwriteConfig.Table.commentary, Data(list.utf8))
        let store = MatchDetailStore(matchId: "123", api: api, realtime: FakeRealtime(), presenceInterval: .seconds(3600))
        await store.start()
        await store.stop()
        #expect(store.commentary.isEmpty)
        #expect(store.hasMoreCommentary)
    }
}
