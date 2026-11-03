import SwiftUI

/// Photo or initials, the name, and "India · Batsman" under it with the side's crest.
struct PlayerHeader: View {
    let profile: PlayerProfile

    var body: some View {
        HStack(spacing: Spacing.lg) {
            PlayerAvatar(name: profile.name, imageURL: profile.imageURL, size: 72)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(profile.name).font(.headlineMedium).foregroundStyle(BouncerColor.inkPrimary)
                HStack(spacing: 6) {
                    if !profile.country.isEmpty { TeamCrest(name: profile.country, size: 18) }
                    Text([profile.country, profile.role].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

struct PlayerAvatar: View {
    let name: String
    let imageURL: URL?
    var size: CGFloat = 72

    var body: some View {
        ZStack {
            Circle().fill(BouncerColor.surfaceVariant)
            initials
            if let imageURL {
                AsyncImage(url: imageURL) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() }
                }
            }
        }
        .frame(width: size, height: size).clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var initials: some View {
        let letters = name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
        return Text(letters).font(.custom(BouncerFonts.bold, fixedSize: size * 0.34)).foregroundStyle(BouncerColor.inkSecondary)
    }
}

/// Born, styles and teams as label/value rows.
struct PlayerFacts: View {
    let profile: PlayerProfile

    var body: some View {
        let rows: [(String, String)] = [
            ("Born", PlayerForm.bornLine(profile.dateOfBirth)), ("Batting", profile.battingStyle),
            ("Bowling", profile.bowlingStyle), ("Teams", profile.teams.joined(separator: ", ")),
        ].filter { !$0.1.isEmpty }
        if !rows.isEmpty {
            DetailSection(title: "Profile") {
                VStack(spacing: 0) {
                    ForEach(rows.indices, id: \.self) { StatRow(label: rows[$0].0, value: rows[$0].1, showDivider: $0 < rows.count - 1) }
                }
            }
        }
    }
}

/// One format's figures: three that matter most, large, then the rest as rows. A bowler who never
/// batted gets no batting block and the reverse.
struct PlayerFormatStats: View {
    let stats: FormatStats
    let format: String

    var body: some View {
        if stats.hasBatting {
            DetailSection(title: "Batting in \(format)") {
                VStack(spacing: 0) {
                    HeadlineFigures(figures: [("\(stats.runs)", "Runs"), (Self.oneDecimal(stats.average), "Average"),
                                              (Self.oneDecimal(stats.strikeRate), "Strike rate")])
                    StatRow(label: "Matches", value: "\(stats.matches)")
                    StatRow(label: "Innings", value: "\(stats.innings)")
                    StatRow(label: "Highest score", value: stats.highScore)
                    StatRow(label: "100s / 50s", value: "\(stats.hundreds) / \(stats.fifties)")
                    StatRow(label: "Fours / sixes", value: "\(stats.fours) / \(stats.sixes)", showDivider: false)
                }
            }
        }
        if stats.hasBowling {
            DetailSection(title: "Bowling in \(format)") {
                VStack(spacing: 0) {
                    HeadlineFigures(figures: [("\(stats.wickets)", "Wickets"), (Self.oneDecimal(stats.bowlingAverage), "Average"),
                                              (Self.oneDecimal(stats.economy), "Economy")])
                    StatRow(label: "Best bowling", value: stats.bestBowling)
                    StatRow(label: "5-wicket hauls", value: "\(stats.fiveWickets)", showDivider: false)
                }
            }
        }
    }

    static func oneDecimal(_ v: Double) -> String { String(format: "%.1f", v) }
}

/// Three figures in the score face with a plain caption under each.
private struct HeadlineFigures: View {
    let figures: [(value: String, caption: String)]

    var body: some View {
        HStack(alignment: .top) {
            ForEach(figures.indices, id: \.self) { i in
                VStack(alignment: .leading, spacing: 0) {
                    Text(figures[i].value).font(.scoreLarge).foregroundStyle(BouncerColor.inkPrimary).tabularFigures()
                    Text(figures[i].caption).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .accessibilityElement(children: .combine)
        Divider().overlay(BouncerColor.divider)
    }
}

/// The last innings, each with opponent, format and date, under one line of what they add up to.
struct PlayerRecentForm: View {
    let profile: PlayerProfile

    var body: some View {
        let form = profile.recentForm
        if !form.isEmpty {
            let scores = form.map(\.runs)
            let average = Double(scores.reduce(0, +)) / Double(scores.count)
            SectionHeader(title: "Recent form")
            Text("\(PlayerFormatStats.oneDecimal(average)) runs an innings over the last \(scores.count). Trend: \(PlayerForm.trend(scores))")
                .font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                .padding(.horizontal, Spacing.lg).padding(.bottom, Spacing.sm)
            BouncerCard {
                VStack(spacing: 0) {
                    ForEach(form.indices, id: \.self) { i in
                        InningsRow(innings: form[i])
                        if i < form.count - 1 { Divider().overlay(BouncerColor.divider) }
                    }
                }
            }
            .padding(.horizontal, Spacing.lg).padding(.bottom, Spacing.sm)
        }
    }
}

private struct InningsRow: View {
    let innings: RecentInnings

    var body: some View {
        let detail = [innings.opponent.isEmpty ? nil : "v \(innings.opponent)", innings.format.isEmpty ? nil : innings.format,
                      innings.date.isEmpty ? nil : innings.date].compactMap { $0 }.joined(separator: " · ")
        HStack(spacing: Spacing.md) {
            Text(innings.balls > 0 ? "\(innings.runs) (\(innings.balls))" : "\(innings.runs)")
                .font(.numeric.weight(.bold)).foregroundStyle(BouncerColor.inkPrimary).tabularFigures()
                .frame(minWidth: 72, alignment: .leading)
            Text(detail).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.sm)
        .accessibilityElement(children: .combine)
    }
}

struct PlayerBio: View {
    let profile: PlayerProfile

    var body: some View {
        let bio = profile.bio.trimmingCharacters(in: .whitespacesAndNewlines)
        if !bio.isEmpty {
            SectionHeader(title: "About")
            Text(bio).font(.bodyMedium).foregroundStyle(BouncerColor.inkPrimary)
                .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.xs)
        }
    }
}
