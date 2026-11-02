import SwiftUI

struct InfoTab: View {
    let store: MatchDetailStore

    var body: some View {
        let sections = build()
        VStack(spacing: 0) {
            if sections.isEmpty {
                if store.loadState == .loading { DetailSkeleton(rows: 5) }
                else { DetailMessage(title: "No match info", message: "Details for this match are not available yet.", systemImage: "info.circle") }
            }
            ForEach(sections, id: \.title) { s in
                DetailSection(title: s.title) {
                    VStack(spacing: 0) {
                        ForEach(Array(s.rows.enumerated()), id: \.offset) { i, r in
                            StatRow(label: r.0, value: r.1, showDivider: i < s.rows.count - 1)
                        }
                        if let note = s.note {
                            Text(note).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
                                .padding(.horizontal, Spacing.lg).padding(.bottom, Spacing.sm)
                        }
                    }
                }
            }
        }
        .padding(.top, Spacing.sm)
    }

    private struct Section { var title: String; var rows: [(String, String)]; var note: String? = nil }

    private func build() -> [Section] {
        let summary = store.summary
        let ctx = store.context
        var out: [Section] = []

        var info: [(String, String)] = []
        if let s = summary?.seriesName, !s.isEmpty { info.append(("Series", s)) }
        if let s = summary {
            let m = [s.matchDesc, s.matchFormat].filter { !$0.isEmpty }.joined(separator: " · ")
            if !m.isEmpty { info.append(("Match", m)) }
        } else if let f = ctx?.format, !f.isEmpty { info.append(("Format", f)) }
        if let t = ctx?.toss, !t.winnerName.isEmpty {
            let d = t.decision.lowercased()
            let verb = d.contains("bowl") ? " chose to bowl" : d.contains("bat") ? " chose to bat" : ""
            info.append(("Toss", t.winnerName + verb))
        }
        if let v = ctx?.venue, !v.name.isEmpty {
            info.append(("Venue", [v.name, v.city, v.country].filter { !$0.isEmpty }.joined(separator: ", ")))
        }
        if let d = summary?.startsAt ?? ctx?.startMs.map({ Date(timeIntervalSince1970: TimeInterval($0) / 1000) }) {
            info.append(("Date", d.formatted(.dateTime.weekday(.wide).day().month(.wide).year())))
            info.append(("Time", d.formatted(date: .omitted, time: .shortened)))
        }
        if !info.isEmpty { out.append(Section(title: "Match info", rows: info)) }

        if let f = ctx?.forecast, let h = nearestHour(f.hours) {
            var rows: [(String, String)] = []
            if let v = h.tempC { rows.append(("Temperature", "\(Int(v.rounded()))°C")) }
            if let v = h.humidity { rows.append(("Humidity", "\(Int(v.rounded()))%")) }
            if let v = h.rainChance { rows.append(("Rain chance", "\(Int(v.rounded()))%")) }
            if let v = h.windKph { rows.append(("Wind", "\(Int(v.rounded())) km/h")) }
            if let v = h.cloud { rows.append(("Cloud cover", "\(Int(v.rounded()))%")) }
            if !rows.isEmpty {
                out.append(Section(title: "Forecast", rows: rows, note: f.source.isEmpty ? nil : "Forecast for the ground, \(f.source)"))
            }
        }

        if let v = ctx?.venue, v.known, let n = v.matches, n > 0 {
            var rows: [(String, String)] = [("Matches on record", "\(n)")]
            if let sc = v.scoring {
                let pct = Int(((sc - 1) * 100).rounded())
                rows.append(("First-innings scores", pct > 0 ? "\(pct)% above average" : pct < 0 ? "\(-pct)% below average" : "Level with average"))
            }
            if let t = v.typicalFirstInnings, t > 0 { rows.append(("Typical first innings", "\(t)")) }
            out.append(Section(title: "Ground record", rows: rows))
        }

        if let h = ctx?.h2h, h.games > 0, let t = ctx?.teams, t.count >= 2 {
            out.append(Section(title: "Head to head", rows: [
                ("Played", "\(h.games)"), ("\(t[0].shortName.isEmpty ? t[0].name : t[0].shortName) won", "\(h.firstWins)"),
                ("\(t[1].shortName.isEmpty ? t[1].name : t[1].shortName) won", "\(h.secondWins)"),
            ]))
        }
        return out
    }

    private func nearestHour(_ hours: [ForecastHour]) -> ForecastHour? {
        let target = store.summary?.startsAt ?? store.context?.startMs.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) } ?? .now
        return hours.min { abs($0.date.timeIntervalSince(target)) < abs($1.date.timeIntervalSince(target)) }
    }
}
