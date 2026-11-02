import Foundation

struct NewsImage: Decodable, Equatable, Hashable, Sendable {
    var id: String = ""
    var caption: String = ""
    var source: String = ""

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        id = w.string("id") ?? ""
        caption = w.string("caption") ?? ""
        source = w.string("source") ?? ""
    }
}

struct NewsStory: Decodable, Equatable, Hashable, Sendable, Identifiable {
    var id: Int = 0
    var headline: String = ""
    var intro: String = ""
    /// Epoch ms as the wire string.
    var pubTime: String = ""
    var source: String = ""
    /// "News", "Features", ...
    var storyType: String = ""
    var imageId: String = ""
    var seoHeadline: String = ""
    var context: String = ""
    var coverImage: NewsImage?

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        id = w.int("id") ?? 0
        headline = w.string("hline") ?? ""
        intro = w.string("intro") ?? ""
        pubTime = w.string("pubTime") ?? ""
        source = w.string("source") ?? ""
        storyType = w.string("storyType") ?? ""
        imageId = w.string("imageId") ?? ""
        seoHeadline = w.string("seoHeadline") ?? ""
        context = w.string("context") ?? ""
        coverImage = w.value("coverImage")
    }

    var publishedAt: Date? {
        guard let ms = Int64(pubTime), ms > 0 else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(ms) / 1000)
    }
}

/// `/news` with an empty body. `storyList` mixes `{story}` and `{ad}` entries; ads are dropped.
struct NewsList: Decodable, Equatable, Sendable {
    var stories: [NewsStory] = []
    var lastUpdatedTime: String?

    init(stories: [NewsStory] = [], lastUpdatedTime: String? = nil) {
        self.stories = stories; self.lastUpdatedTime = lastUpdatedTime
    }

    init(from decoder: Decoder) throws {
        struct Entry: Decodable {
            var story: NewsStory?
            init(from decoder: Decoder) throws {
                let w = try Wire(decoder)
                story = w.value("story")
            }
        }
        let w = try Wire(decoder)
        stories = w.array("storyList", of: Entry.self).compactMap(\.story)
        lastUpdatedTime = w.string("lastUpdatedTime")
    }
}

/// `/news` with `{"storyId":N}`. `isStale` mirrors the server's `_bouncerStale` flag (served from an
/// older cache because the source was unreachable).
struct NewsArticle: Decodable, Equatable, Sendable {
    var storyId: String = ""
    var paragraphs: [String] = []
    var isStale: Bool = false

    init(storyId: String = "", paragraphs: [String] = [], isStale: Bool = false) {
        self.storyId = storyId; self.paragraphs = paragraphs; self.isStale = isStale
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        storyId = w.string("storyId") ?? ""
        paragraphs = w.array("paragraphs", of: LossyString.self).map(\.value)
        isStale = w.bool("_bouncerStale") ?? false
    }
}

struct SeriesSummary: Decodable, Equatable, Hashable, Sendable, Identifiable {
    var id: String = ""
    var name: String = ""
    /// INTERNATIONAL / LEAGUE / DOMESTIC
    var category: String = ""
    var isWomens: Bool = false
    var startDate: String = ""
    var endDate: String = ""

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        id = w.string("id") ?? ""
        name = w.string("name") ?? ""
        category = w.string("category") ?? ""
        isWomens = w.bool("isWomens") ?? false
        startDate = w.string("startDate") ?? ""
        endDate = w.string("endDate") ?? ""
    }
}

/// The `active-series` file: a flat array under `data` (not `data.series`), plus `archiveCursor`.
struct ActiveSeriesFile: Decodable, Equatable, Sendable {
    var series: [SeriesSummary] = []
    var fetchedAt: Date?
    var archiveCursor: String?

    init(series: [SeriesSummary] = [], fetchedAt: Date? = nil, archiveCursor: String? = nil) {
        self.series = series; self.fetchedAt = fetchedAt; self.archiveCursor = archiveCursor
    }

    init(from decoder: Decoder) throws {
        let w = try Wire(decoder)
        series = w.array("data")
        if let ms = w.int64("fetchedAt"), ms > 0 { fetchedAt = Date(timeIntervalSince1970: TimeInterval(ms) / 1000) }
        archiveCursor = w.string("archiveCursor")
    }
}
