import SwiftUI

struct NewsArticleScreen: View {
    let storyId: Int
    @Environment(AppEnvironment.self) private var env
    @State private var story: NewsStory?
    @State private var article: NewsArticle?
    @State private var loading = true
    @State private var failed = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if let story {
                    header(story)
                    if !story.intro.isEmpty { paragraph(story.intro, weight: .medium) }
                } else if loading {
                    ShimmerBar(height: 200).padding(Spacing.lg)
                }
                if article?.isStale == true {
                    StaleBanner(message: "Showing a saved copy of this story.") { Task { await load() } }
                }
                if loading {
                    ForEach(0..<5, id: \.self) { _ in ShimmerBar(height: 16).padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.xs) }
                }
                ForEach(Array((article?.paragraphs ?? []).enumerated()), id: \.offset) { _, p in paragraph(p) }
                if failed && article == nil {
                    EmptyState(title: "Couldn't load the full story", message: "Check your connection and try again.",
                               systemImage: "newspaper", actionTitle: "Retry") { Task { await load() } }
                }
            }
            .padding(.bottom, Spacing.xxl)
        }
        .background(BouncerColor.bg)
        .navigationTitle("Article")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let url = story?.articleURL {
                ToolbarItem(placement: .topBarTrailing) { ShareLink(item: url, subject: Text(story?.headline ?? "")) }
            }
        }
        .task { await load() }
    }

    @ViewBuilder private func header(_ s: NewsStory) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if s.imageURL(width: 2048, height: 1152) != nil {
                NewsImageView(url: s.imageURL(width: 2048, height: 1152))
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                let credit = [s.coverImage?.caption, s.coverImage?.source].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                if !credit.isEmpty { Text(credit).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary) }
            }
            if !s.kicker.isEmpty { Text(s.kicker).font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary) }
            Text(s.headline).font(.headlineLarge).foregroundStyle(BouncerColor.inkPrimary)
            let meta = [s.source.isEmpty ? nil : s.source, s.publishedDay].compactMap { $0 }.joined(separator: " · ")
            if !meta.isEmpty { Text(meta).font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary) }
        }
        .padding(Spacing.lg)
    }

    private func paragraph(_ text: String, weight: Font.Weight? = nil) -> some View {
        Text(text)
            .font(weight == nil ? .bodyLarge : .bouncer(16, .medium, relativeTo: .body))
            .foregroundStyle(BouncerColor.inkPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
    }

    private func load() async {
        loading = true; failed = false
        if story == nil {
            story = (try? await env.news.stories().stories)?.first { $0.id == storyId }
        }
        do { article = try await env.news.article(id: storyId) }
        catch is CancellationError {}
        catch { failed = true }
        loading = false
    }
}
