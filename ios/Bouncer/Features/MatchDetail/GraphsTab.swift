import SwiftUI
import Charts

/// Every chart the match has data for: the win-probability arc, the worm, runs per over and the
/// wagon wheels. Offered only when at least one has data, so it is never a tab of empty states.
struct GraphsTab: View {
    let model: GraphsModel
    let teams: [HeroTeam]
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let ink = ChartInk(teams: teams, scheme: scheme)
        let wormInk = model.worm.map { ink.ordered(firstLabel: $0.team1Name, teams: teams) } ?? ink
        VStack(spacing: 0) {
            if model.hasWinProbability, teams.count >= 2 {
                DetailSection(title: "Win probability") {
                    WinProbabilityChart(points: model.probability, team1: teams[0].acronym, team2: teams[1].acronym, ink: ink)
                }
            }
            if let worm = model.worm {
                DetailSection(title: "Worm") { WormChart(worm: worm, ink: wormInk) }
                if !worm.perOver.isEmpty {
                    DetailSection(title: "Runs per over") { RunsPerOverChart(worm: worm, requiredRunRate: model.requiredRunRate, ink: wormInk) }
                }
            }
            ForEach(model.wheels) { innings in
                DetailSection(title: "Wagon wheels · \(innings.team)") { WagonWheelRow(wheels: innings.wheels) }
            }
        }
        .padding(.top, Spacing.sm)
    }
}

/// The one colour pair every chart on a match uses: first side, second side (MatchChartColors.kt).
/// The teams' colours when the registry knows both; otherwise two opaque inks, not the hero bar's
/// faint second share, because charts label their series in these colours as well as drawing them.
private struct ChartInk {
    var first = BouncerColor.inkPrimary
    var second = BouncerColor.inkSecondary

    /// WCAG's floor for graphics and large text: these colours draw lines and label them.
    private static let minContrast = 3.0

    init(teams: [HeroTeam], scheme: ColorScheme) {
        guard teams.count >= 2,
              let pair = TeamColorRegistry.segmentColors(teams[0].name, teams[1].name, scheme: scheme, minContrast: Self.minContrast)
        else { return }
        (first, second) = (pair.0.color, pair.1.color)
    }

    private init(first: Color, second: Color) {
        self.first = first
        self.second = second
    }

    /// Ordered for a series whose first line is `label`: the worm names its innings by the batting
    /// side, which is not always `teams[0]`. A label naming neither team keeps the match order.
    func ordered(firstLabel label: String, teams: [HeroTeam]) -> ChartInk {
        guard teams.count >= 2 else { return self }
        let l = label.trimmingCharacters(in: .whitespaces)
        let second = teams[1]
        let swapped = !l.isEmpty && (l.caseInsensitiveCompare(second.name) == .orderedSame
            || l.caseInsensitiveCompare(second.acronym) == .orderedSame)
        return swapped ? ChartInk(first: self.second, second: first) : self
    }
}

private struct ChartLegend: View {
    let entries: [(name: String, color: Color)]

    var body: some View {
        HStack(spacing: Spacing.lg) {
            ForEach(Array(entries.enumerated()), id: \.offset) { _, e in
                HStack(spacing: Spacing.xs) {
                    Circle().fill(e.color).frame(width: 8, height: 8)
                    Text(e.name).font(.labelMedium).foregroundStyle(BouncerColor.inkPrimary)
                }
            }
            Spacer(minLength: 0)
        }
        .accessibilityHidden(true)
    }
}

private extension View {
    func chartAxisStyle() -> some View {
        chartXAxis {
            AxisMarks { _ in
                AxisGridLine().foregroundStyle(BouncerColor.divider)
                AxisValueLabel().font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
            }
        }
        .chartYAxis {
            AxisMarks { _ in
                AxisGridLine().foregroundStyle(BouncerColor.divider)
                AxisValueLabel().font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
            }
        }
    }
}

private struct WinProbabilityChart: View {
    let points: [ProbabilityPoint]
    let team1: String
    let team2: String
    let ink: ChartInk

    var body: some View {
        let last = points.last
        let n1 = team1.isEmpty ? "Team 1" : team1
        let n2 = team2.isEmpty || team2 == team1 ? "Team 2" : team2
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ChartLegend(entries: [("\(n1) \(percent(last?.team1 ?? 0))", ink.first),
                                  ("\(n2) \(percent(1 - (last?.team1 ?? 0)))", ink.second)])
            Chart {
                ForEach(Array(points.enumerated()), id: \.offset) { _, p in
                    LineMark(x: .value("Over", p.over), y: .value("Probability", p.team1), series: .value("Team", n1))
                        .foregroundStyle(ink.first)
                    LineMark(x: .value("Over", p.over), y: .value("Probability", 1 - p.team1), series: .value("Team", n2))
                        .foregroundStyle(ink.second)
                }
            }
            .chartYScale(domain: 0...1)
            .chartYAxis {
                AxisMarks(values: [0, 0.5, 1]) { value in
                    AxisGridLine().foregroundStyle(BouncerColor.divider)
                    AxisValueLabel { if let v = value.as(Double.self) { Text(percent(v)) } }
                        .font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
                }
            }
            .chartXAxis {
                AxisMarks { _ in
                    AxisGridLine().foregroundStyle(BouncerColor.divider)
                    AxisValueLabel().font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
                }
            }
            .frame(height: 180)
            .accessibilityLabel("Win probability by over. \(n1) \(percent(last?.team1 ?? 0)), \(n2) \(percent(1 - (last?.team1 ?? 0)))")
        }
        .padding(Spacing.lg)
    }

    private func percent(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }
}

private struct WormChart: View {
    let worm: OverWorm
    let ink: ChartInk

    var body: some View {
        let (n1, n2) = WormNames.of(worm)
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ChartLegend(entries: worm.team2Scores.isEmpty ? [(n1, ink.first)] : [(n1, ink.first), (n2, ink.second)])
            Chart {
                ForEach(Array(worm.team1Scores.enumerated()), id: \.offset) { i, score in
                    LineMark(x: .value("Over", i + 1), y: .value("Runs", score), series: .value("Innings", n1))
                        .foregroundStyle(ink.first)
                }
                ForEach(Array(worm.team2Scores.enumerated()), id: \.offset) { i, score in
                    LineMark(x: .value("Over", i + 1), y: .value("Runs", score), series: .value("Innings", n2))
                        .foregroundStyle(ink.second)
                }
            }
            .chartAxisStyle()
            .frame(height: 200)
            .accessibilityLabel("Cumulative runs by over. \(n1) \(worm.team1Scores.last ?? 0)\(worm.team2Scores.isEmpty ? "" : ", \(n2) \(worm.team2Scores.last ?? 0)")")
        }
        .padding(Spacing.lg)
    }
}

private struct RunsPerOverChart: View {
    let worm: OverWorm
    let requiredRunRate: Double?
    let ink: ChartInk

    var body: some View {
        let (n1, n2) = WormNames.of(worm)
        let hasSecond = worm.perOver.contains { $0.team2Runs != nil }
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ChartLegend(entries: hasSecond ? [(n1, ink.first), (n2, ink.second)] : [(n1, ink.first)])
            Chart {
                ForEach(worm.perOver, id: \.over) { o in
                    BarMark(x: .value("Over", o.over), y: .value("Runs", o.team1Runs))
                        .foregroundStyle(ink.first).position(by: .value("Innings", n1))
                    if let r = o.team2Runs {
                        BarMark(x: .value("Over", o.over), y: .value("Runs", r))
                            .foregroundStyle(ink.second).position(by: .value("Innings", n2))
                    }
                }
                if let rrr = requiredRunRate {
                    RuleMark(y: .value("Required run rate", rrr))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 3])).foregroundStyle(BouncerColor.inkSecondary)
                        .annotation(position: .top, alignment: .trailing) {
                            Text("Req \(String(format: "%.2f", rrr))").font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
                        }
                }
            }
            .chartAxisStyle()
            .frame(height: 180)
            .accessibilityLabel("Runs scored in each over")
        }
        .padding(Spacing.lg)
    }
}

/// Series names for the two innings; two sides that share a label (or none) are told apart by innings.
private enum WormNames {
    static func of(_ worm: OverWorm) -> (String, String) {
        let a = worm.team1Name.isEmpty ? "1st innings" : worm.team1Name
        let b = worm.team2Name.isEmpty || worm.team2Name == a ? "2nd innings" : worm.team2Name
        return (a, b)
    }
}

/// One wheel per batter, side by side, with the bowler's end at the bottom.
private struct WagonWheelRow: View {
    let wheels: [WagonEntry]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(Array(wheels.enumerated()), id: \.offset) { _, w in WagonWheelCard(wheel: w) }
            }
            .padding(.horizontal, Spacing.sm)
        }
    }
}

private struct WagonWheelCard: View {
    let wheel: WagonEntry
    private static let size: CGFloat = 132

    var body: some View {
        let weights = WagonWheels.weights(wheel.zones)
        let busiest = wheel.zones.indices.max { wheel.zones[$0] < wheel.zones[$1] } ?? 0
        VStack(spacing: Spacing.xs) {
            Text(wheel.name).font(.labelLarge).foregroundStyle(BouncerColor.inkPrimary).lineLimit(1)
            Text("\(wheel.runs) \(wheel.runs == 1 ? "run" : "runs") · \(wheel.balls) \(wheel.balls == 1 ? "ball" : "balls")")
                .font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
            ZStack {
                ForEach(0..<WagonWheels.zoneCount, id: \.self) { i in
                    WheelWedge(index: i).fill(BouncerColor.inkPrimary.opacity(0.06 + 0.36 * weights[i]))
                    WheelWedge(index: i).stroke(BouncerColor.divider, lineWidth: 1)
                    if wheel.zones[i] > 0 {
                        Text("\(wheel.zones[i])").font(.numericSmall).tabularFigures().foregroundStyle(BouncerColor.inkPrimary)
                            .offset(WheelWedge.labelOffset(index: i, radius: Self.size / 2 * 0.68))
                    }
                }
                Circle().strokeBorder(BouncerColor.divider, lineWidth: 1)
            }
            .frame(width: Self.size, height: Self.size)
            .padding(.vertical, Spacing.xs)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(wheel.name): most runs, \(wheel.zones[busiest]), \(WagonWheels.zoneWords[busiest])")
            Text("Bowler's end").font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
        }
        .frame(width: 164).padding(.vertical, Spacing.sm)
    }
}

/// One octant of the wheel, zones running clockwise from the top. Built from points so the arc's
/// direction does not depend on SwiftUI's flipped y axis.
private struct WheelWedge: Shape {
    let index: Int

    static func angle(_ degrees: Double) -> Double { (degrees - 90) * .pi / 180 }
    static func labelOffset(index: Int, radius: CGFloat) -> CGSize {
        let mid = angle((Double(index) + 0.5) * 45)
        return CGSize(width: radius * cos(mid), height: radius * sin(mid))
    }

    func path(in rect: CGRect) -> Path {
        let c = CGPoint(x: rect.midX, y: rect.midY)
        let r = min(rect.width, rect.height) / 2
        var p = Path()
        p.move(to: c)
        for step in 0...12 {
            let a = Self.angle((Double(index) + Double(step) / 12) * 45)
            p.addLine(to: CGPoint(x: c.x + r * cos(a), y: c.y + r * sin(a)))
        }
        p.closeSubpath()
        return p
    }
}
