import ActivityKit
import SwiftUI
import WidgetKit

private typealias State = MatchActivityAttributes.ContentState

private extension MatchActivityAttributes {
    func acronym(_ side: Int) -> String { side == 1 ? team1Acronym : team2Acronym }
    func name(_ side: Int) -> String { side == 1 ? team1Name : team2Name }
}

private extension State {
    func score(_ side: Int) -> String { side == 1 ? team1Score : team2Score }
    func overs(_ side: Int) -> String { side == 1 ? team1Overs : team2Overs }
    /// The side shown first in compact spaces: the one batting, else side 1.
    var focus: Int { battingSide == 2 ? 2 : 1 }
    /// Current innings only ("346 & 32/0" -> "32/0") for places with no room for both.
    func latestScore(_ side: Int) -> String {
        score(side).components(separatedBy: "&").last?.trimmingCharacters(in: .whitespaces) ?? ""
    }
}

struct MatchLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: MatchActivityAttributes.self) { context in
            LockScreenCard(attributes: context.attributes, state: context.state, isStale: context.isStale)
                .padding(Spacing.lg)
                .activityBackgroundTint(BouncerColor.surface)
                .activitySystemActionForegroundColor(BouncerColor.inkPrimary)
                .widgetURL(WidgetBackend.matchURL(context.attributes.matchId))
        } dynamicIsland: { context in
            let a = context.attributes, s = context.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) { IslandSide(attributes: a, state: s, side: 1) }
                DynamicIslandExpandedRegion(.trailing) { IslandSide(attributes: a, state: s, side: 2) }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: Spacing.xs) {
                        if !s.recentBalls.isEmpty { BallsRow(balls: s.recentBalls) }
                        Text(s.statusLine).font(.bouncer(12, .regular, relativeTo: .caption))
                            .foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                    }
                }
            } compactLeading: {
                Text(a.acronym(s.focus)).font(.bouncer(12, .semibold, relativeTo: .caption))
            } compactTrailing: {
                Text(s.latestScore(s.focus)).font(.bouncer(12, .bold, relativeTo: .caption)).tabularFigures()
                    .lineLimit(1).minimumScaleFactor(0.7)
            } minimal: {
                Text(a.acronym(s.focus)).font(.bouncer(10, .bold, relativeTo: .caption2)).minimumScaleFactor(0.6)
            }
            .widgetURL(WidgetBackend.matchURL(a.matchId))
        }
    }
}

/// Lock-screen / banner card, laid out like the Android Live Update card: both sides, the over so far, the status.
private struct LockScreenCard: View {
    let attributes: MatchActivityAttributes
    let state: State
    let isStale: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.xs) {
                if state.phase == .live && isStale {
                    Text("NOT UPDATING").font(.overline).foregroundStyle(BouncerColor.inkSecondary)
                } else if state.phase == .live {
                    Circle().fill(BouncerColor.live).frame(width: 6, height: 6)
                    Text("LIVE").font(.overline).foregroundStyle(BouncerColor.live)
                }
                Text([attributes.matchFormat, attributes.seriesName].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.overline).foregroundStyle(BouncerColor.inkSecondary).lineLimit(1)
            }
            ForEach([1, 2], id: \.self) { side in
                HStack(spacing: Spacing.xs) {
                    Text(attributes.name(side))
                        .font(.bouncer(15, state.battingSide == side ? .semibold : .regular, relativeTo: .subheadline))
                        .foregroundStyle(BouncerColor.inkPrimary).lineLimit(1)
                    if state.battingSide == side { Circle().fill(BouncerColor.inkPrimary).frame(width: 5, height: 5) }
                    Spacer(minLength: Spacing.xs)
                    Text(state.score(side)).font(.bouncer(18, .bold, relativeTo: .title3)).tabularFigures()
                        .foregroundStyle(BouncerColor.inkPrimary).lineLimit(1).minimumScaleFactor(0.7)
                    if !state.overs(side).isEmpty {
                        Text("(\(state.overs(side)))").font(.bouncer(12, .regular, relativeTo: .caption)).tabularFigures()
                            .foregroundStyle(BouncerColor.inkSecondary)
                    }
                }
            }
            if !state.recentBalls.isEmpty { BallsRow(balls: state.recentBalls) }
            if !state.statusLine.isEmpty {
                Text(state.statusLine).font(.bouncer(13, .medium, relativeTo: .footnote))
                    .foregroundStyle(BouncerColor.inkSecondary).lineLimit(2)
            }
        }
    }
}

private struct IslandSide: View {
    let attributes: MatchActivityAttributes
    let state: State
    let side: Int

    var body: some View {
        VStack(alignment: side == 1 ? .leading : .trailing, spacing: 0) {
            Text(attributes.acronym(side)).font(.bouncer(12, state.battingSide == side ? .semibold : .regular, relativeTo: .caption))
                .foregroundStyle(.white.opacity(0.7))
            Text(state.score(side)).font(.bouncer(20, .bold, relativeTo: .title3)).tabularFigures()
                .lineLimit(1).minimumScaleFactor(0.6)
            if !state.overs(side).isEmpty {
                Text("\(state.overs(side)) ov").font(.bouncer(11, .regular, relativeTo: .caption2)).tabularFigures()
                    .foregroundStyle(.white.opacity(0.7))
            }
        }
    }
}

private struct BallsRow: View {
    let balls: [String]

    var body: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(Array(balls.suffix(8).enumerated()), id: \.offset) { BallChip(label: $0.element, size: 20) }
        }
    }
}
