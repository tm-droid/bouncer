import SwiftUI

private enum BouncerMatchPalette {
    static let accent = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 224/255, green: 183/255, blue: 79/255, alpha: 1) : UIColor(red: 138/255, green: 101/255, blue: 22/255, alpha: 1)
    })
    static let canvas = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 12/255, green: 16/255, blue: 21/255, alpha: 1) : .systemGroupedBackground
    })
}

struct SharedMatchesHome: View {
    @Environment(\.scenePhase) private var phase
    @AppStorage("matches.selectedList") private var selected = "live"
    @State private var repository: SharedMatchRepository?
    @State private var matches: SharedMatchList?
    @State private var loading = false
    @State private var error: String?
    @State private var visible = false
    private var kind: MatchListKind { MatchListKind(rawValue: selected) ?? .live }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("Matches", selection: $selected) {
                    ForEach(MatchListKind.allCases) { kind in Text(kind.title).tag(kind.rawValue) }
                }
                .pickerStyle(.segmented)
                .padding()
                List {
                    if let error { Section { statusMessage(error, symbol: "wifi.exclamationmark") } }
                    if let matches, matches.isStale(kind) {
                        Section { statusMessage("Saved scores · updated \(Date(timeIntervalSince1970: matches.fetchedAt / 1000).formatted(date: .abbreviated, time: .shortened))", symbol: "clock") }
                    }
                    if let repository {
                        ForEach(matches?.data.matches ?? []) { match in
                            NavigationLink {
                                SharedMatchDetailScreen(match: match, repository: repository)
                            } label: { SharedScoreRow(match: match) }
                            .listRowInsets(EdgeInsets(top: 18, leading: 18, bottom: 18, trailing: 16))
                        }
                    }
                    if matches?.data.matches.isEmpty != false {
                        ContentUnavailableView {
                            Label(loading ? "Loading matches" : "No \(kind.title.lowercased()) matches", systemImage: "sportscourt")
                        } description: {
                            Text(loading ? "Fetching the latest scores." : "Pull down to check for updates.")
                        }
                        .listRowBackground(Color.clear)
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
                .refreshable { await reload(force: true) }
            }
            .background(BouncerMatchPalette.canvas)
            .navigationTitle("Bouncer")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    if loading { ProgressView().accessibilityLabel("Updating matches") }
                    else { Button { Task { await reload(force: true) } } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("Refresh matches") }
                }
            }
            .onAppear { visible = true }
            .onDisappear { visible = false }
            .task(id: "\(selected).\(phase == .active).\(visible)") {
                guard phase == .active, visible else { return }
                if repository == nil {
                    do { repository = SharedMatchRepository(configuration: try .bundled()) }
                    catch { self.error = error.localizedDescription; return }
                }
                matches = repository?.savedList(kind)
                do {
                    while !Task.isCancelled {
                        await reload()
                        try await Task.sleep(for: .seconds(kind.ttl))
                    }
                } catch { /* Background or navigation cancels the list loop. */ }
            }
        }
        .tint(BouncerMatchPalette.accent)
    }
    @MainActor private func reload(force: Bool = false) async {
        guard let repository, phase == .active else { return }
        let requested = kind
        loading = true
        defer { loading = false }
        do {
            let value = try await repository.list(requested, force: force)
            try Task.checkCancellation()
            guard requested == kind else { return }
            matches = value
            error = nil
        } catch is CancellationError { return }
        catch { self.error = error.localizedDescription }
    }
}

private struct SharedScoreRow: View {
    let match: SharedMatch
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text([match.matchFormat, match.matchDesc].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
                if match.isLive { Label("Live", systemImage: "dot.radiowaves.left.and.right").font(.caption.weight(.semibold)) }
            }
            if let teams = match.teams, !teams.isEmpty {
                ForEach(Array(teams.enumerated()), id: \.offset) { _, team in
                    HStack(alignment: .firstTextBaseline) {
                        Text(team.name ?? "").font(.headline)
                        Spacer(minLength: 12)
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(team.score ?? "").font(.title3.weight(.bold)).monospacedDigit()
                            if let overs = team.overs, !overs.isEmpty { Text("\(overs) overs").font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            } else { Text(match.title).font(.headline) }
            Text(match.status).font(.subheadline).foregroundStyle(.secondary)
            if let series = match.seriesName, !series.isEmpty { Text(series).font(.caption).foregroundStyle(.secondary) }
        }
        .foregroundStyle(.primary)
    }
}

struct SharedMatchDetailScreen: View {
    @Environment(\.scenePhase) private var phase
    @StateObject private var model: SharedMatchScreenModel
    @State private var tab = "Overview"
    @State private var visible = false
    init(match: SharedMatch, repository: SharedMatchRepository) {
        _model = StateObject(wrappedValue: SharedMatchScreenModel(match: match, repository: repository))
    }
    var body: some View {
        List {
            Section { SharedScoreRow(match: model.displayMatch) }
            if model.loading { ProgressView("Updating match") }
            if let error = model.error { statusMessage(error, symbol: "exclamationmark.arrow.trianglehead.2.clockwise.rotate.90") }
            if model.stale { statusMessage("Saved match data · updates may be delayed", symbol: "clock") }
            Section {
                Picker("Match details", selection: $tab) {
                    ForEach(["Overview", "Scorecard", "Commentary"], id: \.self) { Text($0) }
                }.pickerStyle(.segmented)
            }
            switch tab {
            case "Scorecard": scorecard
            case "Commentary": commentary
            default: overview
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(model.match.matchDesc?.isEmpty == false ? model.match.matchDesc! : "Match centre")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable {
            await model.refresh()
            if tab == "Scorecard" { await model.loadScorecard() }
        }
        .onAppear { visible = true }
        .onDisappear { visible = false; model.stop() }
        .onChange(of: phase) { _, newValue in if newValue != .active { model.stop() } }
        .task(id: "\(phase == .active).\(visible)") {
            guard phase == .active, visible else { return }
            await model.runVisible()
        }
        .task(id: "\(tab).\(phase == .active).\(visible)") {
            guard tab == "Scorecard", phase == .active, visible else { return }
            await model.loadScorecard()
        }
    }
    @ViewBuilder private var overview: some View {
        if let detail = model.envelope?.detail {
            Section("Match overview") {
                if !detail["score"].text.isEmpty { Text(detail["score"].text).font(.title2.bold()).monospacedDigit() }
                if model.shouldFollowLive, !detail["status"].text.isEmpty { Text(detail["status"].text) }
                if !detail["recentBalls"].array.isEmpty {
                    LabeledContent("Recent balls", value: detail["recentBalls"].array.map(\.text).joined(separator: "  "))
                }
                ForEach(Array(detail["batsmen"].array.enumerated()), id: \.offset) { _, player in
                    LabeledContent(player["name"].text, value: "\(player["runs"].text) (\(player["balls"].text))")
                }
                if !detail["bowler"]["name"].text.isEmpty {
                    LabeledContent("Bowling", value: detail["bowler"]["name"].text)
                }
            }
        } else { unavailable("Match overview", message: "Detailed scores have not been published for this match.") }
        let rows = SharedMetadataRow.rows(model.envelope?.meta)
        if !rows.isEmpty {
            Section("Match information") {
                ForEach(rows) { row in LabeledContent(row.label, value: row.value) }
            }
        }
    }
    @ViewBuilder private var scorecard: some View {
        if let error = model.scorecardError { statusMessage(error, symbol: "wifi.exclamationmark") }
        if let scorecard = model.scorecard, !scorecard["innings"].array.isEmpty {
            ForEach(Array(scorecard["innings"].array.enumerated()), id: \.offset) { _, innings in
                Section("\(innings["teamName"].text) · \(innings["score"].text) (\(innings["overs"].text))") {
                    ForEach(Array(innings["batsmen"].array.enumerated()), id: \.offset) { _, batter in
                        VStack(alignment: .leading, spacing: 5) {
                            LabeledContent(batter["name"].text, value: "\(batter["runs"].text) (\(batter["balls"].text))")
                            Text(batter["dismissal"].text).font(.caption).foregroundStyle(.secondary)
                            Text("4s \(batter["fours"].text) · 6s \(batter["sixes"].text) · SR \(batter["strikeRate"].text)").font(.caption).foregroundStyle(.secondary)
                        }.accessibilityElement(children: .combine)
                    }
                    LabeledContent("Extras", value: innings["extras"].text)
                    LabeledContent("Total", value: innings["total"].text).fontWeight(.semibold)
                    ForEach(Array(innings["bowlers"].array.enumerated()), id: \.offset) { _, bowler in
                        VStack(alignment: .leading, spacing: 5) {
                            LabeledContent(bowler["name"].text, value: "\(bowler["wickets"].text)/\(bowler["runs"].text)")
                            Text("\(bowler["overs"].text) overs · \(bowler["maidens"].text) maidens · Econ \(bowler["economy"].text)").font(.caption).foregroundStyle(.secondary)
                        }.accessibilityElement(children: .combine)
                    }
                }
            }
        } else if model.loadingScorecard { ProgressView("Loading scorecard") }
        else { unavailable("Scorecard", message: "The scorecard has not been published for this match.") }
    }
    @ViewBuilder private var commentary: some View {
        if let error = model.commentaryError { statusMessage(error, symbol: "wifi.exclamationmark") }
        if !model.displayedCommentary.isEmpty {
            Section("Latest deliveries") {
                ForEach(model.displayedCommentary) { line in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            if let ball = line.ball { Text(ball).font(.caption.bold()).monospacedDigit() }
                            if let innings = line.innings { Text("Innings \(innings)").font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                            if line.source == "cricbuzz" { Text("Cricbuzz").font(.caption).foregroundStyle(.secondary) }
                        }
                        Text(line.commentary).font(.body)
                    }.padding(.vertical, 4).accessibilityElement(children: .combine)
                }
                if model.hasOlder && !model.commentary.isEmpty {
                    Button { Task { await model.loadOlder() } } label: {
                        if model.loadingOlder { ProgressView() } else { Text("Load earlier commentary") }
                    }.disabled(model.loadingOlder)
                }
            }
        } else { unavailable("Commentary", message: "Commentary has not been published for this match.") }
    }
}

private func statusMessage(_ message: String, symbol: String) -> some View {
    Label(message, systemImage: symbol).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
}
private func unavailable(_ title: String, message: String) -> some View {
    Section { ContentUnavailableView(title, systemImage: "sportscourt", description: Text(message)) }
}
