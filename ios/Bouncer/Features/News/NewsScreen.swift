import SwiftUI

struct NewsScreen: View {
    @Environment(AppEnvironment.self) private var env
    @State private var stories: [NewsStory] = []
    @State private var loading = true
    @State private var failed = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if loading && stories.isEmpty {
                    ForEach(0..<6, id: \.self) { _ in NewsRowSkeleton(); Divider().overlay(BouncerColor.divider) }
                } else if stories.isEmpty {
                    EmptyState(title: failed ? "Couldn't load news" : "No news yet",
                               message: failed ? "Check your connection and try again." : "Pull down to refresh.",
                               systemImage: failed ? "icloud.slash" : "newspaper",
                               actionTitle: "Retry") { Task { await load(force: true) } }
                } else {
                    if failed {
                        StaleBanner(message: "Couldn't refresh headlines. Showing earlier stories.") { Task { await load(force: true) } }
                    }
                    ForEach(stories) { story in
                        NavigationLink(value: Route.news(id: story.id)) { NewsRow(story: story) }
                            .buttonStyle(.plain)
                        Divider().overlay(BouncerColor.divider).padding(.horizontal, Spacing.lg)
                    }
                }
            }
        }
        .background(BouncerColor.bg)
        .refreshable { await load(force: true) }
        .task { await load(force: false) }
        .navigationTitle("News")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func load(force: Bool) async {
        if stories.isEmpty { loading = true }
        do {
            stories = try await env.news.stories(forceRefresh: force).stories
            failed = false
        } catch is CancellationError {
        } catch {
            failed = true
        }
        loading = false
    }
}

private struct NewsRow: View {
    let story: NewsStory
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                if !story.kicker.isEmpty {
                    Text(story.kicker).font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
                }
                Text(story.headline)
                    .font(.bouncer(16, .semibold, relativeTo: .body))
                    .foregroundStyle(BouncerColor.inkPrimary)
                    .lineLimit(3).multilineTextAlignment(.leading)
                if !story.byline.isEmpty {
                    Text(story.byline).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if story.imageURL(width: 384, height: 288) != nil {
                NewsImageView(url: story.imageURL(width: 384, height: 288))
                    .frame(width: 96, height: 72)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .contentShape(Rectangle())
    }
}

private struct NewsRowSkeleton: View {
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                ShimmerBar(width: 90, height: 12)
                ShimmerBar(height: 16)
                ShimmerBar(width: 200, height: 16)
                ShimmerBar(width: 110, height: 12)
            }
            ShimmerBar(width: 96, height: 72)
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .accessibilityElement().accessibilityLabel("Loading")
    }
}
