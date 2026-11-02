import SwiftUI

enum RowResult { case undecided, won, lost }

/// One team line: crest, acronym/name, score + overs. Batting/won teams are bold; losers are dimmed.
struct TeamRow: View {
    let name: String
    var acronym: String = ""
    var score: String = ""
    var overs: String = ""
    var isBatting = false
    var yetToBat = false
    var result: RowResult = .undecided
    var image: UIImage?
    var imageURL: URL?
    var crestSize: CGFloat = 28

    private var dimmed: Bool { result == .lost }
    private var emphasised: Bool { isBatting || result == .won }

    var body: some View {
        HStack(spacing: 10) {
            TeamCrest(name: name, acronym: acronym.isEmpty ? nil : acronym, image: image, imageURL: imageURL, size: crestSize)
            Text(acronym.isEmpty ? name : acronym)
                .font(.bouncer(17, isBatting ? .bold : dimmed ? .regular : .semibold, relativeTo: .headline))
                .foregroundStyle(dimmed ? BouncerColor.inkSecondary : BouncerColor.inkPrimary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            scoreCell
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var scoreCell: some View {
        if score.isEmpty {
            if yetToBat { Text("Yet to bat").font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary) }
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(score)
                    .font(.bouncer(emphasised ? 24 : 18, emphasised ? .bold : .semibold, relativeTo: emphasised ? .title2 : .title3))
                    .foregroundStyle(dimmed ? BouncerColor.inkSecondary : BouncerColor.inkPrimary)
                    .tabularFigures().lineLimit(1).minimumScaleFactor(0.7)
                if !overs.isEmpty && overs != "0.0" {
                    Text("(\(overs))").font(.numericSmall).foregroundStyle(BouncerColor.inkSecondary).tabularFigures()
                }
            }
        }
    }
}

#Preview("Light") {
    BouncerCard {
        VStack(spacing: Spacing.md) {
            TeamRow(name: "India", acronym: "IND", score: "287/4", overs: "50.0", result: .won)
            TeamRow(name: "Australia", acronym: "AUS", score: "251", overs: "47.3", result: .lost)
            TeamRow(name: "England", acronym: "ENG", yetToBat: true)
        }.padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.rowV)
    }.padding().background(BouncerColor.bg)
}

#Preview("Dark") {
    BouncerCard {
        VStack(spacing: Spacing.md) {
            TeamRow(name: "India", acronym: "IND", score: "145/3", overs: "12.3", isBatting: true)
            TeamRow(name: "Australia", acronym: "AUS", score: "251", overs: "47.3")
        }.padding(.horizontal, Spacing.rowH).padding(.vertical, Spacing.rowV)
    }.padding().background(BouncerColor.bg).preferredColorScheme(.dark)
}
