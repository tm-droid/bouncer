import SwiftUI

/// First-run flow: welcome, favourite teams + preferred matches, done. Calls `onFinish` at the end or on Skip;
/// RootView stores the flag. Picks are saved only on finish, since a saved favourite would end the flow early.
struct OnboardingScreen: View {
    static let popularTeams = ["India", "Australia", "England", "Pakistan", "South Africa", "New Zealand",
                               "Sri Lanka", "West Indies", "Bangladesh", "Afghanistan", "Zimbabwe", "Ireland"]
    private static let pageCount = 3

    var onFinish: () -> Void
    @Environment(AppEnvironment.self) private var env
    @AppStorage("home.competition") private var preferredCompetition = HomeFilters.all
    @State private var page = 0
    @State private var teams: [String] = []
    @State private var category = HomeFilters.all
    @ObservedObject private var logos = TeamLogoLoader.shared

    private var isLast: Bool { page == Self.pageCount - 1 }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if !isLast {
                    Button("Skip", action: finish)
                        .font(.labelLarge).foregroundStyle(BouncerColor.inkSecondary).frame(minWidth: 44, minHeight: 44)
                }
            }
            .frame(height: 44)
            ScrollView {
                Group {
                    switch page {
                    case 0: welcome
                    case 1: yourCricket
                    default: complete
                    }
                }
                .frame(maxWidth: .infinity).padding(.vertical, Spacing.lg)
                .transition(.opacity)
            }
            .scrollBounceBehavior(.basedOnSize)
            dots.padding(.vertical, Spacing.lg)
            Button(action: next) {
                Text(isLast ? "Get started" : "Next").font(.bouncer(16, .bold)).foregroundStyle(BouncerColor.onGold)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(BouncerColor.goldFill, in: RoundedRectangle(cornerRadius: Radius.md))
            }
            .buttonStyle(.plain)
        }
        .padding(Spacing.lg)
        .background(BouncerColor.bg.ignoresSafeArea())
    }

    private func next() {
        if isLast { finish() } else { withAnimation(.easeInOut(duration: 0.2)) { page += 1 } }
    }

    private func finish() {
        teams.forEach(env.favourites.add)
        preferredCompetition = category
        onFinish()
    }

    // MARK: - Pages

    private var welcome: some View {
        VStack(spacing: Spacing.sm) {
            Text("Bouncer").font(.bouncer(44, .bold, relativeTo: .largeTitle)).tracking(-0.4)
                .foregroundStyle(BouncerColor.goldInk).padding(.bottom, Spacing.lg)
            title("Welcome to Bouncer")
            subtitle("Live scores, ball-by-ball commentary and match stats.")
            VStack(alignment: .leading, spacing: Spacing.sm) {
                ForEach(["Live ball-by-ball updates", "Win-probability insights", "Follow your teams"], id: \.self) { line in
                    Label { Text(line).font(.bodyMedium).foregroundStyle(BouncerColor.inkPrimary) } icon: {
                        Image(systemName: "checkmark").font(.system(size: 13, weight: .semibold)).foregroundStyle(BouncerColor.inkPrimary)
                    }
                }
            }
            .padding(.top, Spacing.lg)
        }
    }

    private var yourCricket: some View {
        VStack(spacing: Spacing.sm) {
            title("Your Cricket")
            subtitle("Pick your teams and preferred cricket.")
            ChipFlow(spacing: Spacing.sm) {
                ForEach(Self.popularTeams, id: \.self) { team in
                    let selected = teams.contains(team)
                    let full = !selected && teams.count >= FavouritesStore.maxTeams
                    Button {
                        if selected { teams.removeAll { $0 == team } } else { teams.append(team) }
                    } label: {
                        HStack(spacing: Spacing.sm) {
                            TeamCrest(name: team, imageURL: logos.getLogoUrl(for: team).flatMap(URL.init(string:)), size: 24)
                            Text(team).font(.bouncer(14, selected ? .bold : .medium, relativeTo: .subheadline))
                        }
                        .padding(.leading, 6).padding(.trailing, 14).padding(.vertical, 6)
                        .foregroundStyle(selected ? BouncerColor.onGold : BouncerColor.inkPrimary)
                        .background(selected ? BouncerColor.goldFill : BouncerColor.surfaceVariant, in: Capsule())
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain).disabled(full).opacity(full ? 0.4 : 1)
                    .accessibilityValue(selected ? "Selected" : "Not selected")
                }
            }
            .padding(.top, Spacing.lg)
            Text("Preferred matches").font(.labelMedium).foregroundStyle(BouncerColor.inkSecondary).padding(.top, Spacing.xl)
            ChipFlow(spacing: Spacing.sm) {
                ForEach(HomeFilters.competitions, id: \.self) { option in
                    let selected = option == category
                    Button { category = option } label: {
                        Text(option).font(.bouncer(12, selected ? .bold : .regular, relativeTo: .caption))
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .foregroundStyle(selected ? BouncerColor.onGold : BouncerColor.inkSecondary)
                            .background(selected ? BouncerColor.goldFill : BouncerColor.surfaceVariant, in: Capsule())
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            .padding(.top, Spacing.sm)
        }
    }

    private var complete: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "checkmark.circle").font(.system(size: 64, weight: .light))
                .foregroundStyle(BouncerColor.inkSecondary).padding(.bottom, Spacing.lg).accessibilityHidden(true)
            title("You're All Set")
            subtitle(Self.summary(teams: teams.count, category: category))
        }
    }

    /// The completion line: team count and preferred category, or the generic invitation.
    static func summary(teams: Int, category: String) -> String {
        guard teams > 0 else { return "Start exploring matches, squads, and insights." }
        let following = "Following \(teams) \(teams == 1 ? "team" : "teams")."
        return category == HomeFilters.all ? "\(following) Start exploring!" : "\(following) Showing \(category) matches."
    }

    private func title(_ text: String) -> some View {
        Text(text).font(.headlineMedium).foregroundStyle(BouncerColor.inkPrimary)
            .multilineTextAlignment(.center).accessibilityAddTraits(.isHeader)
    }

    private func subtitle(_ text: String) -> some View {
        Text(text).font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary).multilineTextAlignment(.center)
    }

    private var dots: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(0..<Self.pageCount, id: \.self) { i in
                Circle().fill(i == page ? BouncerColor.goldFill : BouncerColor.surfaceVariant)
                    .frame(width: i == page ? 10 : 8, height: i == page ? 10 : 8)
            }
        }
        .accessibilityElement().accessibilityLabel("Page \(page + 1) of \(Self.pageCount)")
    }
}

/// Left-to-right wrapping rows, each centred; chips keep their own width.
private struct ChipFlow: Layout {
    var spacing: CGFloat

    private func rows(_ subviews: Subviews, width: CGFloat) -> [[Subviews.Element]] {
        var rows: [[Subviews.Element]] = [[]]
        var x: CGFloat = 0
        for v in subviews {
            let w = v.sizeThatFits(.unspecified).width
            if x > 0 && x + w > width { rows.append([]); x = 0 }
            rows[rows.count - 1].append(v)
            x += w + spacing
        }
        return rows
    }

    private func rowSize(_ row: [Subviews.Element]) -> CGSize {
        let sizes = row.map { $0.sizeThatFits(.unspecified) }
        return CGSize(width: sizes.map(\.width).reduce(0, +) + spacing * CGFloat(max(sizes.count - 1, 0)),
                      height: sizes.map(\.height).max() ?? 0)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rs = rows(subviews, width: proposal.width ?? .infinity).map(rowSize)
        return CGSize(width: rs.map(\.width).max() ?? 0, height: rs.map(\.height).reduce(0, +) + spacing * CGFloat(max(rs.count - 1, 0)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(subviews, width: bounds.width) {
            let size = rowSize(row)
            var x = bounds.midX - size.width / 2
            for v in row {
                let s = v.sizeThatFits(.unspecified)
                v.place(at: CGPoint(x: x, y: y + (size.height - s.height) / 2), proposal: ProposedViewSize(s))
                x += s.width + spacing
            }
            y += size.height + spacing
        }
    }
}

#Preview("Light") { OnboardingScreen {}.environment(AppEnvironment()) }
#Preview("Dark") { OnboardingScreen {}.environment(AppEnvironment()).preferredColorScheme(.dark) }
