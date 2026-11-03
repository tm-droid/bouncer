import Foundation
import WidgetKit

struct MatchEntry: TimelineEntry {
    let date: Date
    let match: WidgetMatch?

    static let sample = MatchEntry(date: .now, match: WidgetMatch(
        id: "0", status: "Need 84 runs", state: "In Progress", format: "ODI", series: "ICC Cricket World Cup League Two",
        startsAt: nil,
        teams: [WidgetTeam(name: "Oman", score: "182/10", overs: "49.2"),
                WidgetTeam(name: "Netherlands", score: "99/1", overs: "19.6", isBatting: true)]))
}

/// Fetches the public match-list files directly (the extension has no access to the app's cache).
struct MatchProvider: TimelineProvider {
    func placeholder(in context: Context) -> MatchEntry { .sample }

    func getSnapshot(in context: Context, completion: @escaping (MatchEntry) -> Void) {
        if context.isPreview { return completion(.sample) }
        Task { completion(MatchEntry(date: .now, match: await Self.load())) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MatchEntry>) -> Void) {
        Task {
            let match = await Self.load()
            let now = Date()
            completion(Timeline(entries: [MatchEntry(date: now, match: match)], policy: .after(Self.nextReload(for: match, now: now))))
        }
    }

    /// 15 minutes while live, otherwise an hour (sooner if the next match kicks off before that).
    static func nextReload(for match: WidgetMatch?, now: Date) -> Date {
        if match?.phase == .live { return now.addingTimeInterval(15 * 60) }
        let hour = now.addingTimeInterval(60 * 60)
        guard let start = match?.startsAt, start > now else { return hour }
        return max(now.addingTimeInterval(5 * 60), min(hour, start))
    }

    static func load() async -> WidgetMatch? {
        async let live = fetch("live-matches")
        async let upcoming = fetch("upcoming-matches")
        let favourites = UserDefaults(suiteName: WidgetBackend.suiteName)?.stringArray(forKey: WidgetBackend.favouritesKey) ?? []
        return WidgetMatchSelector.select(live: await live, upcoming: await upcoming, favourites: favourites)
    }

    private static func fetch(_ file: String) async -> [WidgetMatch] {
        var request = URLRequest(url: WidgetBackend.fileURL(file), timeoutInterval: 15)
        request.setValue(WidgetBackend.projectId, forHTTPHeaderField: "X-Appwrite-Project")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return [] }
        return WidgetSnapshotDecoder.decode(data)
    }
}
