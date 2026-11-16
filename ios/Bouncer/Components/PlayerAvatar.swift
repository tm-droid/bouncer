import SwiftUI

/// A player's head-shot in a circle, over their initials. The photo is `imageURL` when the caller
/// has one, else whatever `PlayerImages` learned for `id` or `name`.
struct PlayerAvatar: View {
    let name: String
    var id: String? = nil
    var imageURL: URL? = nil
    var size: CGFloat = 72

    var body: some View {
        ZStack {
            Circle().fill(BouncerColor.surfaceVariant)
            initials
            if let url = imageURL ?? PlayerImages.shared.url(id: id, name: name) {
                RemoteImage(url: url)
            }
        }
        .frame(width: size, height: size).clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var initials: some View {
        let letters = playerNameWithoutRoleTags(name).split(separator: " ").prefix(2)
            .compactMap(\.first).map(String.init).joined().uppercased()
        return Text(letters).font(.custom(BouncerFonts.bold, fixedSize: size * 0.34)).foregroundStyle(BouncerColor.inkSecondary)
    }
}
