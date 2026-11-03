import SwiftUI
import SafariServices

/// Matched clips as a grid of thumbnails; a tap opens the clip in an in-app browser sheet.
struct HighlightsTab: View {
    let clips: [Highlight]
    @State private var playing: Highlight?

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: Spacing.md, alignment: .top)], spacing: Spacing.md) {
            ForEach(clips) { clip in
                Button { playing = clip } label: { HighlightClipCard(clip: clip) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(clip.title), \(clip.channel)")
                    .accessibilityHint("Plays the clip")
            }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .sheet(item: $playing) { SafariView(url: $0.url).ignoresSafeArea() }
    }
}

private struct HighlightClipCard: View {
    let clip: Highlight

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            BouncerColor.surfaceVariant
                .aspectRatio(16 / 9, contentMode: .fit)
                .overlay {
                    AsyncImage(url: clip.imageURL) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        } else {
                            Image(systemName: "play.rectangle").font(.system(size: 28)).foregroundStyle(BouncerColor.inkSecondary)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(BouncerColor.divider, lineWidth: 1))
            Text(clip.title).font(.bodySmall).foregroundStyle(BouncerColor.inkPrimary)
                .lineLimit(2).multilineTextAlignment(.leading).padding(.horizontal, Spacing.xs)
            Text(clip.channel).font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
                .lineLimit(1).padding(.horizontal, Spacing.xs)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .contentShape(Rectangle())
    }
}

private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
