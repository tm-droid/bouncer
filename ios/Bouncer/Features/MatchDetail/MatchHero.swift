import SwiftUI

/// Header: crest/acronym/score columns, status lines, win-probability bar and recent balls.
struct MatchHero: View {
    let model: HeroModel
    var title = ""

    var body: some View {
        VStack(spacing: Spacing.md) {
            if !title.isEmpty {
                Text(title).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
                    .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
            }
            if model.teams.count >= 2 {
                HStack(alignment: .top, spacing: Spacing.xs) {
                    column(model.teams[0])
                    center.frame(width: 92)
                    column(model.teams[1])
                }
            }
            if let chase = model.chase {
                Text(chase).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
            }
            if !model.status.isEmpty, model.status != model.chase, model.phase != .upcoming || model.teams.contains(where: \.hasScore) {
                Text(model.status)
                    .font(model.phase == .finished ? .titleSmall : .bodyMedium)
                    .foregroundStyle(model.phase == .finished ? BouncerColor.inkPrimary : BouncerColor.inkSecondary)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
            }
            if let p = model.winProbability, model.teams.count >= 2 {
                WinProbabilityBar(probability: p, team1: model.teams[0].acronym, team2: model.teams[1].acronym)
            }
            if !model.recentBalls.isEmpty { RecentBallsStrip(balls: model.recentBalls) }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .frame(maxWidth: .infinity)
        .background(BouncerColor.surface)
        .accessibilityElement(children: .contain)
    }

    private func column(_ t: HeroTeam) -> some View {
        VStack(spacing: Spacing.xs) {
            TeamCrest(name: t.name, acronym: t.acronym, imageURL: t.logoURL, size: 56)
            Text(t.acronym).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary)
                .lineLimit(1)
            if t.hasScore {
                if !t.earlierInnings.isEmpty {
                    Text(t.earlierInnings).font(.scoreMedium).tabularFigures()
                        .foregroundStyle(BouncerColor.inkSecondary).lineLimit(1).minimumScaleFactor(0.6)
                }
                Text(t.latestInnings).font(.scoreHero).tabularFigures()
                    .foregroundStyle(BouncerColor.inkPrimary).lineLimit(1).minimumScaleFactor(0.5)
                if !t.overs.isEmpty, t.overs != "0.0" {
                    Text("\(displayOvers(t.overs)) ov").font(.numericSmall).tabularFigures().foregroundStyle(BouncerColor.inkSecondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .opacity(model.phase == .live && !t.isBatting && model.teams.contains(where: \.isBatting) ? 0.75 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spoken(t))
    }

    private func spoken(_ t: HeroTeam) -> String {
        var s = t.name
        if t.hasScore {
            s += " " + t.score.replacingOccurrences(of: "/", with: " for ").replacingOccurrences(of: "&", with: "and")
            if !t.overs.isEmpty { s += " in \(displayOvers(t.overs)) overs" }
        }
        return s
    }

    @ViewBuilder private var center: some View {
        VStack(spacing: Spacing.xs) {
            switch model.phase {
            case .live:
                StatusBadge(status: .live)
                if let v = nonZero(model.crr) { rate("CRR", v) }
                if let v = nonZero(model.rrr) { rate("RRR", v) }
            case .finished:
                StatusBadge(status: .completed)
            case .upcoming:
                Text("vs").font(.titleLarge).foregroundStyle(BouncerColor.inkSecondary)
                if let start = model.startsAt { Countdown(start: start) }
            }
        }
        .frame(maxWidth: .infinity).padding(.top, Spacing.lg)
    }

    private func nonZero(_ s: String) -> String? { (Double(s) ?? 0) > 0 ? s : nil }

    private func rate(_ label: String, _ value: String) -> some View {
        HStack(spacing: 4) {
            Text(label).font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
            Text(value).font(.numeric).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label == "CRR" ? "Current run rate" : "Required run rate") \(value)")
    }
}

private struct Countdown: View {
    let start: Date
    var body: some View {
        TimelineView(.everyMinute) { ctx in
            VStack(spacing: 2) {
                if let c = MatchRowText.countdown(to: start, now: ctx.date) {
                    Text(c).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary)
                    Text(start.formatted(date: .omitted, time: .shortened)).font(.numericSmall).foregroundStyle(BouncerColor.inkSecondary)
                } else {
                    Text(start.formatted(date: .omitted, time: .shortened)).font(.titleSmall).foregroundStyle(BouncerColor.inkPrimary)
                    Text(MatchRowText.day(start, now: ctx.date)).font(.numericSmall).foregroundStyle(BouncerColor.inkSecondary)
                }
            }
            .multilineTextAlignment(.center).lineLimit(1).minimumScaleFactor(0.7)
        }
    }
}

struct WinProbabilityBar: View {
    let probability: WinProbability
    let team1: String
    let team2: String

    var body: some View {
        let total = probability.team1 + probability.team2 + (probability.draw ?? 0)
        VStack(spacing: 4) {
            GeometryReader { geo in
                HStack(spacing: 1) {
                    Rectangle().fill(BouncerColor.inkPrimary).frame(width: geo.size.width * probability.team1 / total)
                    if let d = probability.draw {
                        Rectangle().fill(BouncerColor.border).frame(width: geo.size.width * d / total)
                    }
                    Rectangle().fill(BouncerColor.inkSecondary.opacity(0.45))
                }
                .clipShape(Capsule())
            }
            .frame(height: 6)
            HStack {
                Text("\(team1) \(pct(probability.team1 / total))")
                Spacer()
                if let d = probability.draw { Text("Draw \(pct(d / total))"); Spacer() }
                Text("\(team2) \(pct(probability.team2 / total))")
            }
            .font(.numericSmall).tabularFigures().foregroundStyle(BouncerColor.inkSecondary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Win probability \(team1) \(pct(probability.team1 / total)), \(team2) \(pct(probability.team2 / total))")
    }

    private func pct(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }
}

struct RecentBallsStrip: View {
    let balls: [String]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.xs) {
                Text("This over").font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary).padding(.trailing, Spacing.xs)
                ForEach(Array(balls.suffix(10).enumerated()), id: \.offset) { _, b in BallChip(label: b, size: 24) }
            }
            .padding(.vertical, 1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Recent balls: " + balls.suffix(10).joined(separator: ", "))
    }
}

/// 56pt bar the header collapses to: "MI 186/4 · CSK 142/3".
struct CollapsedHeroBar: View {
    let model: HeroModel

    var body: some View {
        HStack(spacing: Spacing.sm) {
            if model.phase == .live { StatusBadge(status: .live, label: "") }
            Text(summary).font(.titleMedium).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
                .lineLimit(1).minimumScaleFactor(0.6)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.lg).frame(height: 56)
        .background(BouncerColor.surface)
        .overlay(alignment: .bottom) { Divider().overlay(BouncerColor.divider) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(model.teams.map { $0.hasScore ? "\($0.name) \($0.latestInnings)" : $0.name }.joined(separator: ", "))
    }

    private var summary: String {
        model.teams.map { $0.hasScore ? "\($0.acronym) \($0.latestInnings)" : $0.acronym }.joined(separator: " · ")
    }
}
