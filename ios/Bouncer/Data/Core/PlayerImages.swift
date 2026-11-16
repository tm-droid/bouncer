import Foundation
import Observation

/// Head-shots and Cricbuzz ids learned from squads, search hits and profiles, so any row that only
/// has a player's name (scorecard, crease, summary) can show their photo and open their profile by id.
/// Names match exactly after role tags, case, accents and spacing are normalised; there is no
/// fuzzy fallback, because a wrong photo or profile is worse than initials. Kept in Caches so
/// photos survive relaunches; the oldest entries go past `limit`.
@MainActor @Observable
final class PlayerImages {
    static let shared = PlayerImages(fileURL: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
        .appendingPathComponent("player-images.json"))
    static let limit = 3000

    struct Entry: Codable, Equatable {
        var id: String
        var name: String
        var url: URL?
    }

    private var byId: [String: Entry] = [:]
    private var idByName: [String: String] = [:]
    /// Ids oldest first, for trimming.
    @ObservationIgnored private var order: [String] = []
    @ObservationIgnored private let fileURL: URL?
    @ObservationIgnored private var saveScheduled = false

    /// `fileURL` nil keeps everything in memory (tests).
    init(fileURL: URL?) {
        self.fileURL = fileURL
        guard let fileURL, let data = try? Data(contentsOf: fileURL),
              let entries = try? JSONDecoder().decode([Entry].self, from: data) else { return }
        for e in entries { insert(e) }
    }

    nonisolated static func key(_ name: String) -> String {
        playerNameWithoutRoleTags(name)
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .split(whereSeparator: \.isWhitespace).joined(separator: " ")
    }

    /// The photo for a player: by id when known, else by name.
    func url(id: String? = nil, name: String) -> URL? {
        if let id, !id.isEmpty, let url = byId[id]?.url { return url }
        return idByName[Self.key(name)].flatMap { byId[$0]?.url }
    }

    /// Cricbuzz id for a name seen in a squad, search or profile; nil when unknown.
    func playerId(forName name: String) -> String? {
        idByName[Self.key(name)]
    }

    func register(_ squads: Squads) {
        for side in [squads.team1, squads.team2] {
            for p in side.playingXI + side.bench + side.squad { register(id: p.id, name: p.name, url: p.imageURL) }
        }
    }

    /// A nil `url` never clears a photo already known for `id`.
    func register(id: String, name: String, url: URL?) {
        guard !id.isEmpty, !Self.key(name).isEmpty else { return }
        let entry = Entry(id: id, name: name, url: url ?? byId[id]?.url)
        guard byId[id] != entry || idByName[Self.key(name)] != id else { return }
        insert(entry)
        scheduleSave()
    }

    private func insert(_ e: Entry) {
        if let old = byId[e.id] {
            if idByName[Self.key(old.name)] == e.id { idByName[Self.key(old.name)] = nil }
            order.removeAll { $0 == e.id }
        }
        byId[e.id] = e
        idByName[Self.key(e.name)] = e.id
        order.append(e.id)
        while order.count > Self.limit {
            let dropped = order.removeFirst()
            if let gone = byId.removeValue(forKey: dropped), idByName[Self.key(gone.name)] == dropped {
                idByName[Self.key(gone.name)] = nil
            }
        }
    }

    /// Coalesces bursts (a squad registers ~30 players) into one write.
    private func scheduleSave() {
        guard fileURL != nil, !saveScheduled else { return }
        saveScheduled = true
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            guard let self else { return }
            saveScheduled = false
            await save()
        }
    }

    /// Writes the registry off the main actor; the scheduled save calls this after its delay.
    func save() async {
        guard let fileURL else { return }
        let entries = order.compactMap { byId[$0] }
        await Task.detached(priority: .utility) {
            guard let data = try? JSONEncoder().encode(entries) else { return }
            try? data.write(to: fileURL, options: .atomic)
        }.value
    }
}
