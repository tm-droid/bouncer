import SwiftUI

/// Circular team logo (28 or 56pt) on a surfaceVariant plate; logo inset 12%, monogram until/unless an image loads.
struct TeamCrest: View {
    let name: String
    var acronym: String?
    var image: UIImage?
    var imageURL: URL?
    var size: CGFloat = 28

    private var monogram: String {
        let code = String((acronym ?? "").filter { $0.isLetter || $0.isNumber }.prefix(3)).uppercased()
        return code.isEmpty ? String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased() : code
    }

    var body: some View {
        ZStack {
            Circle().fill(BouncerColor.surfaceVariant)
            if let image {
                logo(Image(uiImage: image))
            } else if let imageURL {
                AsyncImage(url: imageURL) { phase in
                    if let img = phase.image { logo(img) } else { initials }
                }
            } else {
                initials
            }
        }
        .frame(width: size, height: size)
        .accessibilityElement()
        .accessibilityLabel(name)
    }

    private func logo(_ img: Image) -> some View {
        img.resizable().scaledToFit().padding(size * 0.12)
    }

    private var initials: some View {
        let fontSize = size * (monogram.count > 2 ? 0.3 : 0.38)
        return Text(monogram)
            .font(.custom(BouncerFonts.bold, fixedSize: fontSize))
            .foregroundStyle(BouncerColor.inkSecondary)
            .lineLimit(1)
    }
}

#Preview("Light") {
    HStack { TeamCrest(name: "India", acronym: "IND"); TeamCrest(name: "Australia", size: 56) }
        .padding().background(BouncerColor.bg)
}

#Preview("Dark") {
    HStack { TeamCrest(name: "India", acronym: "IND"); TeamCrest(name: "Australia", acronym: "AU", size: 56) }
        .padding().background(BouncerColor.bg).preferredColorScheme(.dark)
}
