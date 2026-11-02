import SwiftUI

extension NewsStory {
    func imageURL(width: Int, height: Int) -> URL? {
        guard !imageId.isEmpty else { return nil }
        return URL(string: "https://static.cricbuzz.com/a/img/v1/\(width)x\(height)/i1/c\(imageId)/i.jpg")
    }

    var kicker: String {
        [storyType, context].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    var articleURL: URL? {
        let slug = headline.lowercased()
            .replacingOccurrences(of: "[^a-z0-9\\s-]", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: "\\s+", with: "-", options: .regularExpression)
        return URL(string: "https://www.cricbuzz.com/cricket-news/\(id)/\(slug)")
    }

    var byline: String {
        var parts: [String] = []
        if !source.isEmpty { parts.append(source) }
        if let d = publishedAt { parts.append(Self.relative.localizedString(for: d, relativeTo: Date())) }
        return parts.joined(separator: " · ")
    }

    var publishedDay: String? {
        publishedAt?.formatted(date: .abbreviated, time: .omitted)
    }

    private static let relative: RelativeDateTimeFormatter = {
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .abbreviated
        return f
    }()
}

struct NewsImageView: View {
    let url: URL?
    var body: some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image): image.resizable().scaledToFill()
            default: BouncerColor.surfaceVariant
            }
        }
        .accessibilityHidden(true)
    }
}
