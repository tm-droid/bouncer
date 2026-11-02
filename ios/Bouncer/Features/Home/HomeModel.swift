import Foundation
import Observation

/// Loads and holds the three match lists for Home. All fetches go through the repository's TTL cache.
@Observable @MainActor
final class HomeModel {
    private(set) var lists: [MatchListKind: MatchList] = [:]
    private(set) var loaded = false
    private(set) var failed = false

    func load(_ kinds: [MatchListKind], repo: MatchRepository, force: Bool = false) async {
        for k in kinds where lists[k] == nil {
            if let c = await repo.cached(k) { lists[k] = c }
        }
        let results = await withTaskGroup(of: (MatchListKind, MatchList?).self) { group in
            for k in kinds { group.addTask { (k, try? await repo.matches(k, forceRefresh: force)) } }
            var out: [MatchListKind: MatchList] = [:]
            for await (k, l) in group { if let l { out[k] = l } }
            return out
        }
        guard !Task.isCancelled else { return }
        for (k, l) in results where lists[k] != l { lists[k] = l }
        failed = kinds.allSatisfy { lists[$0] == nil }
        loaded = true
    }
}
