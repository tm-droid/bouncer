import SwiftUI

struct CommentaryTab: View {
    let store: MatchDetailStore
    @State private var chip: Chip = .all

    enum Chip: String, CaseIterable, Identifiable {
        case all = "All", wickets = "Wickets", boundaries = "Boundaries", milestones = "Milestones"
        var id: String { rawValue }
        var filter: CommentaryFilter {
            switch self {
            case .all, .milestones: .all
            case .wickets: .wickets
            case .boundaries: .boundaries
            }
        }
    }

    private var entries: [CommentaryEntry] {
        chip == .milestones ? store.commentary.filter(CommentaryText.isMilestone) : store.visibleCommentary
    }

    private var feedLoading: Bool {
        switch chip {
        case .all, .milestones: store.isLoadingOlder
        default: store.eventFeed(chip.filter).isLoading
        }
    }

    private var hasMore: Bool {
        switch chip {
        case .all, .milestones: store.hasMoreCommentary
        default: store.eventFeed(chip.filter).hasMore
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            chips
            let rows = entries
            if rows.isEmpty {
                if feedLoading || store.loadState == .loading || (chip != .all && chip != .milestones && !store.eventFeed(chip.filter).hasLoaded) {
                    DetailSkeleton(rows: 5)
                } else {
                    DetailMessage(title: chip == .all ? "No commentary yet" : "Nothing here yet",
                                  message: chip == .all ? "Ball-by-ball commentary appears once play starts." : "No matching moments in the loaded commentary.",
                                  systemImage: "text.bubble")
                }
            } else {
                LazyVStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.sortKey) { i, e in
                        if let over = CommentaryText.overNumber(e), i == 0 || CommentaryText.overNumber(rows[i - 1]) != over {
                            overHeader(over)
                        }
                        CommentaryLine(entry: e)
                            .onAppear { if i >= rows.count - 3 { loadMore() } }
                        Divider().overlay(BouncerColor.divider)
                    }
                    footer
                }
                .background(BouncerColor.surface)
            }
        }
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(Chip.allCases) { c in
                    let on = c == chip
                    Button {
                        chip = c
                        Task { await store.setFilter(c.filter) }
                    } label: {
                        Text(c.rawValue).font(.labelLarge)
                            .foregroundStyle(on ? BouncerColor.onGold : BouncerColor.inkPrimary)
                            .padding(.horizontal, Spacing.md).frame(minHeight: 36)
                            .background(on ? BouncerColor.goldFill : BouncerColor.surfaceVariant, in: Capsule())
                    }
                    .buttonStyle(.plain).frame(minHeight: 44)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(.horizontal, Spacing.lg)
        }
    }

    private func overHeader(_ over: Int) -> some View {
        Text("Over \(over)").font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.xs)
            .background(BouncerColor.surfaceVariant)
            .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private var footer: some View {
        if feedLoading {
            ProgressView().frame(maxWidth: .infinity).padding(Spacing.lg)
        } else if !hasMore {
            Text("Start of commentary").font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
                .frame(maxWidth: .infinity).padding(Spacing.lg)
        } else if chip == .milestones {
            Button("Load older") { loadMore() }.font(.labelLarge).foregroundStyle(BouncerColor.goldInk)
                .frame(maxWidth: .infinity, minHeight: 44)
        }
    }

    private func loadMore() {
        guard hasMore, !feedLoading else { return }
        Task {
            switch chip {
            case .all, .milestones: await store.loadOlder()
            default: await store.loadMoreEvents(chip.filter)
            }
        }
    }
}
