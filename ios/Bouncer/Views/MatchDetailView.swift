import SwiftUI
import Foundation

enum ConfettiType { case none, boundary, wicket, win, milestone }

struct CommentaryItem: Equatable {
    let ball: String
    let text: String
    let bowler: String?
    let batsman: String?
}

func eventIcon(for type: ConfettiType, commentary: String?) -> String {
    let text = (commentary ?? "").lowercased()
    switch type {
    case .wicket: return "🏏"
    case .boundary:
        if text.contains("six") { return "6️⃣" }
        if text.contains("four") { return "4️⃣" }
        return "✨"
    case .win: return "🏆"
    case .milestone:
        if text.contains("fifty") || text.contains("half-century") { return "5️⃣0️⃣" }
        if text.contains("hundred") || text.contains("century") { return "💯" }
        if text.contains("fifer") || text.contains("5 wickets") { return "5️⃣" }
        if text.contains("hat-trick") { return "🎩" }
        return "⭐️"
    case .none:
        return "✨"
    }
}

enum EventOverlayType: Equatable {
    case fifty(String)
    case hundred(String)
    case hatTrick(String)
}

struct MatchDetailView: View {
    let match: CricketMatch
    @StateObject private var colorFetcher = TeamLogoColorFetcher()
    @StateObject private var matchViewModel = MatchViewModel()
    @State private var batsmen: [ScrapedBatsman] = []
    @State private var bowler: ScrapedBowler? = nil
    @State private var selectedTab = 0
    @State private var showConfetti = false
    @State private var confettiType: ConfettiType = .none
    @State private var lastEventDescription: String? = nil
    @State private var lastBallEvent: String? = nil
    @State private var celebratedMilestones: Set<String> = [] // Format: "player-milestone"
    @State private var eventOverlayType: EventOverlayType? = nil
    @State private var achievements: [String] = [] // Track unlocked achievements
    @State private var showAchievementToast: Bool = false
    @State private var latestAchievement: String? = nil
    @State private var showAchievementsSheet: Bool = false
    // Track unlocked achievements by Kind
    @State private var unlockedAchievements: Set<Achievement.Kind> = Achievement.Kind.loadUnlocked()

    var body: some View {
        TabView(selection: $selectedTab) {
            // Tab 1: Overview
            OverviewTab(
                match: match,
                colorFetcher: colorFetcher,
                matchViewModel: matchViewModel,
                showConfetti: $showConfetti,
                confettiType: $confettiType,
                lastEventDescription: $lastEventDescription,
                triggerConfetti: triggerConfetti
            )
            .tabItem {
                Label("Overview", systemImage: "info.circle.fill")
            }
            .tag(0)

            // Tab 2: Scorecard (placeholder)
            VStack {
                Text("Scorecard (Coming Soon)")
                    .font(.headline)
                    .foregroundColor(.secondary)
                    .padding(.top, 32)
                Spacer()
            }
            .tabItem {
                Label("Scorecard", systemImage: "list.bullet.rectangle")
            }
            .tag(1)

            // Tab 3: Squads
            SquadView(match: match)
                .tabItem {
                    Label("Squads", systemImage: "person.3.fill")
                }
                .tag(2)

            // Tab 4: Statistics (Win Probability, etc)
            StatisticsTab(match: match, colorFetcher: colorFetcher, matchViewModel: matchViewModel)
                .tabItem {
                    Label("Statistics", systemImage: "chart.bar.xaxis")
                }
                .tag(3)

            // Tab 5: Match Info
            VStack(alignment: .leading, spacing: 16) {
                Text("Match Info")
                    .font(.title2)
                    .bold()
                    .padding(.top, 24)
                Divider().background(Color.white.opacity(0.2))
                Group {
                    Text("Title: \(match.matchTitle)")
                    Text("Format: \(match.matchType)")
                    Text("Venue: \(match.venue)")
                    Text("Date: \(match.date)")
                    Text("Time: \(match.matchTime)")
                }
                .font(.headline)
                .foregroundColor(.white)
                Spacer()
            }
            .padding(.horizontal)
            .tabItem {
                Label("Match Info", systemImage: "calendar")
            }
            .tag(4)
        }
        .background(Color.black.edgesIgnoringSafeArea(.all))
        .onAppear {
            print("[DEBUG] MatchDetailView main .onAppear called")
            Task {
                let team1LogoUrl = match.teams.first.flatMap { TeamDirectory.shared.teams[$0.name]?.logoUrl }
                let team2LogoUrl = match.teams.count > 1 ? TeamDirectory.shared.teams[match.teams[1].name]?.logoUrl : nil
                await colorFetcher.fetchColors(for: team1LogoUrl, team2LogoUrl: team2LogoUrl)
                await matchViewModel.fetchMatchDetails(matchId: match.id, existingMatch: match)
                // Start live activity with real match data
                if let details = matchViewModel.matchDetails {
                    CricketMatchActivityView.startLiveActivity(
                        matchId: details.id,
                        team1Name: details.teams.first?.name ?? "Team 1",
                        team2Name: details.teams.dropFirst().first?.name ?? "Team 2",
                        matchType: details.matchType,
                        team1Score: details.teams.first?.score ?? "-",
                        team2Score: details.teams.dropFirst().first?.score ?? "-",
                        currentInnings: details.score?.currentInnings ?? "",
                        alertType: nil,
                        batsmen: nil, // You can map details.score?.batsmen to [BatsmanWidget] if available
                        bowler: nil,  // You can map details.score?.bowlers.first to BowlerWidget if available
                        recentBalls: details.score?.recentBalls?.components(separatedBy: " ")
                    )
                }
            }
            unlockedAchievements = Achievement.Kind.loadUnlocked()
        }
        .navigationTitle(match.teams.map { $0.name }.joined(separator: " vs "))
        .navigationBarTitleDisplayMode(.inline)
        // Custom event overlay
        Group {
            if let overlay = eventOverlayType {
                EventOverlayView(type: overlay)
                    .transition(.scale.combined(with: .opacity))
                    .zIndex(100)
            }
        }
        .onChange(of: batsmen) { _, _ in checkForAchievements() }
        .onChange(of: bowler) { _, _ in checkForAchievements() }
        .onChange(of: match.score?.recentBalls) { _, newBalls in
            guard let balls = newBalls?.components(separatedBy: " "), let latest = balls.last else { return }
            if latest == "4" || latest == "6" || latest == "W" {
                // logoBounce = true // This was for team logo bounce, now handled by achievements
                // logoGlow = true // This was for team logo glow, now handled by achievements
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
                    // logoBounce = false // This was for team logo bounce, now handled by achievements
                    // logoGlow = false // This was for team logo glow, now handled by achievements
                }
            }
        }
        .onChange(of: showAchievementToast) { _, newValue in
            if newValue {
                #if canImport(UIKit)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                #endif
            }
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showAchievementsSheet = true }) {
                    Label("Achievements", systemImage: "rosette")
                }
            }
        }
        .sheet(isPresented: $showAchievementsSheet) {
            AchievementsView(achievements: Achievement.all.map { a in
                var copy = a
                copy.unlocked = unlockedAchievements.contains(a.id)
                return copy
            }, unlockedCount: unlockedAchievements.count, totalCount: Achievement.all.count)
        }
    }

    // Confetti trigger logic
    private func triggerConfetti() {
        withAnimation(.easeOut(duration: 0.2)) { showConfetti = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            withAnimation(.easeIn(duration: 0.4)) { showConfetti = false }
        }
        #if canImport(UIKit)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        #endif
    }

    // Add this function to detect and handle achievements
    private func checkForAchievements() {
        // Batting achievements
        for batsman in batsmen {
            if (Int(batsman.runs) ?? 0) >= 30 { unlock(.runs30, msg: "3️⃣0️⃣ \(batsman.name) scored 30 runs!") }
            if (Int(batsman.runs) ?? 0) >= 50 { unlock(.runs50, msg: "🏅 \(batsman.name) scored a Fifty!") }
            if (Int(batsman.runs) ?? 0) >= 75 { unlock(.runs75, msg: "7️⃣5️⃣ \(batsman.name) scored 75 runs!") }
            if (Int(batsman.runs) ?? 0) >= 100 { unlock(.runs100, msg: "🎖️ \(batsman.name) scored a Century!") }
            if (Int(batsman.runs) ?? 0) >= 150 { unlock(.runs150, msg: "1️⃣5️⃣0️⃣ \(batsman.name) scored 150 runs!") }
            if (Int(batsman.runs) ?? 0) >= 200 { unlock(.runs200, msg: "💯💯 \(batsman.name) scored a Double Century!") }
            if (Int(batsman.runs) ?? 0) >= 250 { unlock(.runs250, msg: "2️⃣5️⃣0️⃣ \(batsman.name) scored 250 runs!") }
            if (Int(batsman.runs) ?? 0) >= 300 { unlock(.runs300, msg: "3️⃣0️⃣0️⃣ \(batsman.name) scored a Triple Century!") }
        }
        // Bowling achievements
        if let bowler = bowler {
            if (Int(bowler.wickets) ?? 0) >= 3 { unlock(.wickets3, msg: "🎯 \(bowler.name) took 3 wickets!") }
            if (Int(bowler.wickets) ?? 0) >= 4 { unlock(.wickets4, msg: "4️⃣ \(bowler.name) took 4 wickets!") }
            if (Int(bowler.wickets) ?? 0) >= 5 { unlock(.wickets5, msg: "5️⃣ \(bowler.name) took 5 wickets!") }
            if (Int(bowler.wickets) ?? 0) >= 10 { unlock(.wickets10, msg: "🔟 \(bowler.name) took 10 wickets!") }
            if bowler.hatTrick { unlock(.hatTrick, msg: "🎩 \(bowler.name) took a Hat-trick!") }
            // Add more as needed
        }
        // Team achievements (example: team 200+)
        for team in match.teams {
            if let score = Int(team.score.components(separatedBy: "/").first ?? "0"), score >= 200 {
                unlock(.team200, msg: "2️⃣0️⃣0️⃣ \(team.name) scored 200+ runs!")
            }
        }
        // Add more detection as needed
    }

    private func unlock(_ kind: Achievement.Kind, msg: String) {
        if !unlockedAchievements.contains(kind) {
            unlockedAchievements.insert(kind)
            Achievement.Kind.saveUnlocked(unlockedAchievements)
            latestAchievement = msg
            confettiType = .milestone
            triggerConfetti()
            showAchievementToast = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { showAchievementToast = false }
        }
    }
}

extension MatchDetailView {
    func commentaryColor(for type: ConfettiType, commentary: String?) -> Color {
        let text = (commentary ?? "").lowercased()
        switch type {
        case .wicket: return .red
        case .boundary:
            if text.contains("six") { return .green }
            if text.contains("four") { return .blue }
            return .white
        case .win: return .goldPrimary // Use signature gold for win confetti
        case .milestone: return .purple
        case .none: return .white
        }
    }
}

// Add AnimatedScoreView for animated team scores
struct AnimatedScoreView: View {
    let score: String
    let color: Color
    var namespace: Namespace.ID? = nil
    @State private var animate = false
    @State private var shimmerPhase: CGFloat = -1.0
    @State private var rotationY: Double = 0
    @State private var offsetY: CGFloat = 0

    var body: some View {
        let scoreText = Text(score)
            .font(.system(size: 24, weight: .heavy, design: .rounded))
            .foregroundColor(.goldPrimary)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(Color.goldPrimary.opacity(0.13))
            .cornerRadius(10)
            .shadow(color: Color.goldPrimary.opacity(0.18), radius: 4, y: 2)
            .scaleEffect(animate ? 1.08 : 1.0)
            .rotation3DEffect(.degrees(rotationY), axis: (x: 0, y: 1, z: 0))
            .offset(y: offsetY)
            .overlay(
                LinearGradient(
                    gradient: Gradient(colors: [Color.white.opacity(0.0), Color.white.opacity(0.45), Color.white.opacity(0.0)]),
                    startPoint: .leading, endPoint: .trailing
                )
                .blendMode(.plusLighter)
                .mask(
                    Text(score)
                        .font(.system(size: 24, weight: .heavy, design: .rounded))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                )
                .offset(x: shimmerPhase * 120)
                .opacity(0.7)
            )
            .onChange(of: score) {
                // Flip and slide animation
                withAnimation(.easeInOut(duration: 0.18)) {
                    rotationY = 90
                    offsetY = 18
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        rotationY = -90
                        offsetY = -18
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                        rotationY = 0
                        offsetY = 0
                    }
                }
                withAnimation(.spring(response: 0.4, dampingFraction: 0.5)) {
                    animate = true
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    withAnimation(.spring()) { animate = false }
                }
            }
            .onAppear {
                withAnimation(.easeIn(duration: 0.7)) { animate = true }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
                    withAnimation(.spring()) { animate = false }
                }
                withAnimation(Animation.linear(duration: 1.6).repeatForever(autoreverses: false)) {
                    shimmerPhase = 1.0
                }
            }
        
        // Apply matchedGeometryEffect conditionally
        if let ns = namespace {
            scoreText.matchedGeometryEffect(id: "score-\(score)", in: ns)
        } else {
            scoreText
        }
    }
}

// Team logo lookup table (full)
// Team logo lookup from JSON file
func getTeamLogoUrl(for teamName: String) -> String? {
    guard let url = Bundle.main.url(forResource: "TeamLogos", withExtension: "json") else {
        return nil
    }

    do {
        let data = try Data(contentsOf: url)
        let teamLogos = try JSONDecoder().decode([String: String].self, from: data)
        return teamLogos[teamName]
    } catch {
        print("Error loading team logos: \(error)")
        return nil
    }
}

// Helper to get team abbreviation for event
extension MatchDetailView {
    func teamAbbreviationForEvent(event: ConfettiType, battingTeam: String, bowlingTeam: String, winningTeam: String? = nil) -> String? {
        switch event {
        case .wicket: return bowlingTeam
        case .boundary: return battingTeam
        case .win, .milestone, .none: return winningTeam
        }
    }
}

// Refactored ConfettiOverlay for reusability and richer visuals
struct ConfettiOverlay: View {
    let type: ConfettiType
    let team1Color: Color
    let team2Color: Color
    var commentary: String? = nil
    var logoUrl: String? = nil // NEW
    @State private var particles: [ConfettiParticle] = []
    let particleCount = 12 // Fewer, larger particles for sleek look
    var shapes: [String] {
        switch type {
        case .wicket: return ["cricket.ball.fill", "circle.fill", "xmark.circle.fill"]
        case .win: return ["trophy.fill", "star.fill", "circle.fill"]
        case .milestone: return ["star.fill", "hexagon.fill", "circle.fill"]
        case .boundary: return ["sparkles", "star.fill", "circle.fill"]
        case .none: return ["circle.fill", "star.fill", "hexagon.fill"]
        }
    }
    var confettiColor: Color {
        switch type {
        case .wicket: return .red
        case .win: return .goldPrimary
        case .milestone: return .purple
        case .boundary: return .blue
        case .none: return .white
        }
    }
    @State private var iconAnim: Double = 0
    @State private var shimmerPhase: CGFloat = 0
    @State private var show: Bool = true

    var mainColor: Color {
        switch type {
        case .boundary: return team1Color
        case .wicket: return team2Color
        case .win: return .yellow
        case .milestone: return .purple
        case .none: return .white
        }
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                // Glassy, blurred background
                RoundedRectangle(cornerRadius: 32, style: .continuous)
                    .fill(Color.white.opacity(0.12))
                    .background(BlurView(style: .systemUltraThinMaterialDark))
                    .frame(width: geo.size.width * 0.85, height: geo.size.height * 0.32)
                    .position(x: geo.size.width/2, y: geo.size.height * 0.32)
                    .shadow(color: mainColor.opacity(0.18), radius: 24, y: 8)
                    .blur(radius: 0.5)
                    .opacity(show ? 1 : 0)
                    .transition(.opacity)
                // Confetti particles (sleek, softly glowing)
                if !particles.isEmpty {
                    ForEach(Array(particles.enumerated()), id: \.element.id) { index, particle in
                        let shapeIndex = index % shapes.count
                        let shape = shapes[shapeIndex]
                        Image(systemName: shape)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: particle.size, height: particle.size)
                            .foregroundColor(confettiColor)
                            .shadow(color: confettiColor.opacity(0.25), radius: 12, y: 0)
                            .opacity(particle.opacity)
                            .position(particle.position)
                            .scaleEffect(particle.scale)
                            .rotationEffect(.degrees(particle.rotation))
                            .blur(radius: 0.7)
                            .animation(.spring(response: 1.1, dampingFraction: 0.8), value: particle.position)
                    }
                }
                // Main icon/emoji with shimmer and glow
                VStack {
                    ZStack {
                        Circle()
                            .fill(mainColor.opacity(0.22))
                            .frame(width: 80, height: 80)
                            .shadow(color: mainColor.opacity(0.32), radius: 18, y: 0)
                        Text(eventIcon(for: type, commentary: commentary))
                            .font(.system(size: 54))
                            .foregroundColor(.white)
                            .shadow(color: mainColor.opacity(0.45), radius: 12, y: 0)
                            .scaleEffect(show ? 1.15 : 1.0)
                            .opacity(show ? 1 : 0)
                            .overlay(
                                // Shimmer effect
                                LinearGradient(gradient: Gradient(colors: [Color.white.opacity(0.2), mainColor, Color.white.opacity(0.2)]), startPoint: .leading, endPoint: .trailing)
                                    .blendMode(.plusLighter)
                                    .mask(
                                        Text(eventIcon(for: type, commentary: commentary))
                                            .font(.system(size: 54))
                                            .scaleEffect(show ? 1.15 : 1.0)
                                    )
                                    .offset(x: shimmerPhase * 120)
                                    .opacity(0.7)
                            )
                            .animation(.spring(response: 0.7, dampingFraction: 0.7), value: show)
                    }
                    .padding(.bottom, 8)
                    if let commentary = commentary, !commentary.isEmpty {
                        Text(commentary)
                            .font(.system(size: 22, weight: .heavy, design: .rounded))
                            .foregroundColor(mainColor)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 18)
                                    .fill(Color.black.opacity(0.22))
                                    .blur(radius: 0.5)
                            )
                            .shadow(color: mainColor.opacity(0.7), radius: 14, y: 0)
                            .scaleEffect(show ? 1.10 : 1.0)
                            .offset(y: show ? 0 : -24)
                            .opacity(show ? 1 : 0)
                            .overlay(
                                // Shimmer effect
                                LinearGradient(gradient: Gradient(colors: [Color.white.opacity(0.2), mainColor, Color.white.opacity(0.2)]), startPoint: .leading, endPoint: .trailing)
                                    .blendMode(.plusLighter)
                                    .mask(
                                        Text(commentary)
                                            .font(.system(size: 22, weight: .heavy, design: .rounded))
                                            .scaleEffect(show ? 1.10 : 1.0)
                                    )
                                    .offset(x: shimmerPhase * 120)
                                    .opacity(0.7)
                            )
                            .transition(
                                .asymmetric(
                                    insertion: .scale.combined(with: .opacity),
                                    removal: .move(edge: .top).combined(with: .opacity)
                                )
                            )
                            .animation(.spring(response: 0.5, dampingFraction: 0.7), value: show)
                    }
                }
                .frame(maxWidth: geo.size.width)
                .position(x: geo.size.width/2, y: geo.size.height * 0.32)
            }
            .onAppear {
                print("[DEBUG] ConfettiOverlay .onAppear called")
                // Animate particles outward
                particles = (0..<particleCount).map { i in
                    let color = i % 2 == 0 ? team1Color : team2Color
                    return ConfettiParticle(
                        color: color,
                        position: CGPoint(x: geo.size.width/2, y: geo.size.height * 0.32),
                        size: CGFloat.random(in: 32...54),
                        opacity: 1,
                        scale: CGFloat.random(in: 0.85...1.25),
                        rotation: Double.random(in: 0...360)
                    )
                }
                for i in 0..<particleCount {
                    guard i < particles.count else { continue } // Add bounds checking
                    let angle = Double(i) / Double(particleCount) * 2 * .pi
                    let radius = CGFloat.random(in: geo.size.height * 0.18...geo.size.height * 0.38)
                    let x = geo.size.width/2 + cos(angle) * radius
                    let y = geo.size.height * 0.32 + sin(angle) * radius
                    let rotation = Double.random(in: 0...360)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.03 * Double(i)) {
                        guard i < particles.count else { return } // Add bounds checking
                        withAnimation(.spring(response: 1.1, dampingFraction: 0.8)) {
                            particles[i].position = CGPoint(x: x, y: y)
                            particles[i].opacity = 0
                            particles[i].scale = CGFloat.random(in: 0.7...1.3)
                            particles[i].rotation = rotation
                        }
                    }
                }
                withAnimation(.interpolatingSpring(stiffness: 180, damping: 12)) {
                    iconAnim = 18
                }
                withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: false)) {
                    shimmerPhase = 1
                }
                // Hide after 1.3s
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    withAnimation(.easeOut(duration: 0.3)) {
                        show = false
                    }
                }
            }
            .onDisappear {
                iconAnim = 0
                shimmerPhase = 0
            }
        }
        .allowsHitTesting(false)
    }
}

// UIKit blur for glassy background
import UIKit
struct BlurView: UIViewRepresentable {
    var style: UIBlurEffect.Style = .systemMaterial
    func makeUIView(context: Context) -> UIVisualEffectView {
        UIVisualEffectView(effect: UIBlurEffect(style: style))
    }
    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {}
}

struct ConfettiParticle: Identifiable {
    let id = UUID()
    var color: Color
    var position: CGPoint
    var size: CGFloat
    var opacity: Double
    var scale: CGFloat
    var rotation: Double = 0
}

// Subviews for Overview tab
struct SeriesHeaderView: View {
    let match: CricketMatch
    var body: some View {
        VStack(spacing: 4) {
            Text(match.matchTitle)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .transition(.opacity)
            Text("\(match.matchType)  |  \(match.info)")
                .font(.system(size: 15, weight: .medium, design: .rounded))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .transition(.opacity)
        }
        .padding(.top, 18)
        .frame(maxWidth: .infinity)
        .background(Color.clear)
    }
}

struct MatchStatusBadge: View {
    let status: String
    var body: some View {
        HStack {
            Text(status.capitalized.isEmpty ? "Upcoming" : status.capitalized)
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(
                    Capsule().fill(status.lowercased().contains("live") ? Color.green.opacity(0.7) : Color.blue.opacity(0.5))
                )
                .shadow(color: .black.opacity(0.18), radius: 6, y: 2)
                .transition(.scale)
            Spacer()
        }
        .padding(.horizontal)
    }
}

struct TeamSectionView: View {
    let match: CricketMatch
    let colorFetcher: TeamLogoColorFetcher
    var liveTeam1Score: String? = nil
    var liveTeam2Score: String? = nil
    @Namespace private var scoreNamespace
    @State private var lastStriker: String? = nil
    @State private var logoBounce: Bool = false
    @State private var logoGlow: Bool = false

    /// Display score: prefer live score, fall back to API score
    private var displayTeam1Score: String {
        if let live = liveTeam1Score, !live.isEmpty, live != "-" { return live }
        return match.teams.first?.score ?? "-"
    }
    private var displayTeam2Score: String {
        if let live = liveTeam2Score, !live.isEmpty, live != "-" { return live }
        return match.teams.count > 1 ? match.teams[1].score : "-"
    }

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            // Team 1
            VStack(spacing: 10) {
                ZStack {
                    LinearGradient(
                        gradient: Gradient(colors: [colorFetcher.team1Color.opacity(0.7), colorFetcher.team1Color.opacity(0.3)]),
                        startPoint: .topLeading, endPoint: .bottomTrailing)
                        .cornerRadius(18)
                        .shadow(color: colorFetcher.team1Color.opacity(0.25), radius: 8, y: 4)
                    VStack(spacing: 8) {
                        if let logoUrl = match.teams.first?.logoUrl, let url = URL(string: logoUrl) {
                            AsyncImage(url: url) { image in
                                image.resizable()
                            } placeholder: {
                                Color.gray
                            }
                            .frame(width: 60, height: 60)
                            .clipShape(Circle())
                            .scaleEffect(logoBounce ? 1.18 : 1.0)
                            .shadow(color: logoGlow ? .goldPrimary : colorFetcher.team1Color.opacity(0.45), radius: logoGlow ? 18 : 4, y: 0)
                            .animation(.spring(response: 0.5, dampingFraction: 0.6), value: logoBounce)
                            .animation(.easeOut(duration: 0.5), value: logoGlow)
                        }
                        Text(match.teams.first?.name ?? "Team 1")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(.goldPrimary)
                            .transition(.opacity)
                        AnimatedScoreView(score: displayTeam1Score, color: .goldPrimary, namespace: scoreNamespace)
                    }
                    .padding(.vertical, 16)
                    .padding(.horizontal, 12)
                }
            }
            .frame(maxWidth: .infinity)
            // VS
            VStack {
                Text("vs")
                    .font(.system(size: 32, weight: .heavy, design: .rounded))
                    .foregroundColor(.white)
                    .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
            }
            .frame(width: 60)
            // Team 2
            VStack(spacing: 10) {
                ZStack {
                    LinearGradient(
                        gradient: Gradient(colors: [colorFetcher.team2Color.opacity(0.7), colorFetcher.team2Color.opacity(0.3)]),
                        startPoint: .topTrailing, endPoint: .bottomLeading)
                        .cornerRadius(18)
                        .shadow(color: colorFetcher.team2Color.opacity(0.25), radius: 8, y: 4)
                    VStack(spacing: 8) {
                        if match.teams.count > 1, let logoUrl = match.teams[1].logoUrl, let url = URL(string: logoUrl) {
                            AsyncImage(url: url) { image in
                                image.resizable()
                            } placeholder: {
                                Color.gray
                            }
                            .frame(width: 60, height: 60)
                            .clipShape(Circle())
                            .scaleEffect(logoBounce ? 1.18 : 1.0)
                            .shadow(color: logoGlow ? .goldPrimary : colorFetcher.team2Color.opacity(0.45), radius: logoGlow ? 18 : 4, y: 0)
                            .animation(.spring(response: 0.5, dampingFraction: 0.6), value: logoBounce)
                            .animation(.easeOut(duration: 0.5), value: logoGlow)
                        }
                        Text(match.teams.count > 1 ? match.teams[1].name : "Team 2")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(.goldPrimary)
                            .transition(.opacity)
                        AnimatedScoreView(score: displayTeam2Score, color: .goldPrimary, namespace: scoreNamespace)
                    }
                    .padding(.vertical, 16)
                    .padding(.horizontal, 12)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal)
        .onChange(of: match.score?.recentBalls) { _, newBalls in
            guard let balls = newBalls?.components(separatedBy: " "), let latest = balls.last else { return }
            if latest == "4" || latest == "6" || latest == "W" {
                logoBounce = true
                logoGlow = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
                    logoBounce = false
                    logoGlow = false
                }
            }
        }
    }
}

struct WinProbabilityCard: View {
    let match: CricketMatch
    let colorFetcher: TeamLogoColorFetcher
    let matchViewModel: MatchViewModel
    var body: some View {
        VStack(spacing: 0) {
            Text("Win Probability")
                .font(.headline)
                .foregroundColor(.white)
                .padding(.top, 8)
            ProbabilityGraphView(
                events: matchViewModel.probabilityEvents,
                team1Name: match.teams.first?.name ?? "Team 1",
                team2Name: match.teams.dropFirst().first?.name ?? "Team 2",
                team1Color: colorFetcher.team1Color,
                team2Color: colorFetcher.team2Color,
                firstInningsEndBall: matchViewModel.firstInningsEndBall,
                currentMatchScore: matchViewModel.currentMatchScore,
                latestEventDescription: matchViewModel.latestEventDescription,
                matchResult: matchViewModel.matchResult
            )
            .padding(.horizontal, 0)
            .padding(.bottom, 8)
            .transition(.opacity)
        }
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(Color.white.opacity(0.07))
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(LinearGradient(gradient: Gradient(colors: [colorFetcher.team1Color, colorFetcher.team2Color]), startPoint: .leading, endPoint: .trailing), lineWidth: 2)
                )
        )
        .shadow(color: .black.opacity(0.18), radius: 8, y: 4)
        .padding(.horizontal)
        .onChange(of: matchViewModel.probabilityEvents) { _, _ in
            // (if you need to do something with the new value, use it here)
        }
        .animation(.easeIn(duration: 0.7), value: matchViewModel.probabilityEvents)
    }
}

struct LiveCommentarySection: View {
    let match: CricketMatch
    @State private var commentary: [CommentaryItem] = []
    @State private var isLoading: Bool = false
    @State private var timer: Timer? = nil
    @State private var animateIdx: Int? = nil
    @State private var batsmen: [ScrapedBatsman] = []
    @State private var bowler: ScrapedBowler? = nil
    private let commentaryManager = LiveCommentaryManager()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Live Commentary")
                .font(.subheadline)
                .foregroundColor(.yellow)
                .transition(.opacity)
            if !commentary.isEmpty {
                EventSummaryRow(commentary: commentary)
                    .padding(.bottom, 4)
            }
            if isLoading {
                ProgressView().padding(.vertical, 8)
            }
            if !commentary.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(commentary.indices.reversed(), id: \.self) { idx in
                            let item = commentary[idx]
                            let event = CommentaryEventType.from(text: item.text)
                            CommentaryBallCard(
                                idx: idx,
                                item: item,
                                event: event,
                                animateIdx: animateIdx,
                                onHighlight: { animateIdx = idx; DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { animateIdx = nil } }
                            )
                            if idx != commentary.startIndex {
                                Divider().background(Color.white.opacity(0.08)).padding(.vertical, 4)
                            }
                        }
                    }
                    .animation(.easeInOut(duration: 0.35), value: commentary)
                }
                .frame(maxHeight: 340)
                .transition(.opacity)
            } else if !isLoading {
                Text("No commentary available yet.")
                    .font(.body)
                    .foregroundColor(.white)
                    .transition(.opacity)
            }
        }
        .padding()
        .background(.ultraThinMaterial)
        .cornerRadius(18)
        .shadow(color: Color.black.opacity(0.13), radius: 10, y: 2)
        .padding(.horizontal)
        .onAppear {
            print("[DEBUG] LiveCommentarySection .onAppear called")
            fetchCommentary()
            timer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { _ in
                fetchCommentary()
            }
        }
        .onDisappear {
            timer?.invalidate()
            timer = nil
        }
    }

    private func fetchCommentary() {
        isLoading = true
        Task {
            let best = await commentaryManager.getBestCommentaryForMatch(
                matchTitle: match.matchTitle,
                cricbuzzId: match.id,
                cricbuzzUrl: "https://www.cricbuzz.com/live-cricket-scores/\(match.id)",
                keyNames: match.teams.map { $0.name }
            )
            // Fetch real batsmen and bowler from CricBuzzScraper
            if let result = try? await CricBuzzScraper().fetchBallByBallAndBatsmen(matchId: match.id) {
                if batsmen != result.batsmen {
                    await MainActor.run {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            batsmen = result.batsmen
                        }
                    }
                }
                if bowler != result.bowler {
                    await MainActor.run {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            bowler = result.bowler
                        }
                    }
                }
            }
            let pattern = "(\\d{1,3}\\.\\d:|\\d{1,3}\\.\\d )"
            let regex = try? NSRegularExpression(pattern: pattern, options: [])
            var newItems: [CommentaryItem] = []
            if let regex = regex {
                let nsBest = best as NSString
                let matches = regex.matches(in: best, options: [], range: NSRange(location: 0, length: nsBest.length))
                var ranges: [NSRange] = matches.map { $0.range }
                ranges.append(NSRange(location: nsBest.length, length: 0))
                for i in 0..<(ranges.count - 1) {
                    let start = ranges[i].location
                    let end = ranges[i+1].location
                    let range = NSRange(location: start, length: end - start)
                    let line = nsBest.substring(with: range).trimmingCharacters(in: .whitespacesAndNewlines)
                    let ballRegex = try? NSRegularExpression(pattern: "^(\\d{1,3}\\.\\d)", options: [])
                    var ball = "?"
                    var text = line
                    if let ballRegex = ballRegex,
                       let match = ballRegex.firstMatch(in: line, options: [], range: NSRange(location: 0, length: (line as NSString).length)),
                       let range = Range(match.range(at: 1), in: line) {
                        ball = String(line[range])
                        text = String(line.dropFirst(match.range.upperBound)).trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                    // Try to match batsman and bowler from the text
                    let batsmanName = batsmen.first(where: { text.contains($0.name) })?.name
                    let bowlerName = bowler?.name != nil && text.contains(bowler!.name) ? bowler!.name : nil
                    if !text.isEmpty && !commentary.contains(where: { $0.ball == ball && $0.text == text }) {
                        newItems.append(CommentaryItem(ball: ball, text: text, bowler: bowlerName, batsman: batsmanName))
                    }
                }
                let ball = match.score?.overs ?? "?"
                let text = best
                let batsmanName = batsmen.first(where: { text.contains($0.name) })?.name
                let bowlerName = bowler?.name != nil && text.contains(bowler!.name) ? bowler!.name : nil
                if !commentary.contains(where: { $0.text == text }) {
                    newItems.append(CommentaryItem(ball: ball, text: text, bowler: bowlerName, batsman: batsmanName))
                }
            }
            await MainActor.run {
                if commentary != newItems {
                    withAnimation(.easeInOut(duration: 0.35)) {
                        commentary = newItems
                    }
                }
                isLoading = false
            }
        }
    }
    // Helper: Get batsman stats (real)
    private func batsmanStats(for name: String) -> String? {
        if let b = batsmen.first(where: { $0.name == name }) {
            return "Runs: \(b.runs), Balls: \(b.balls), Boundaries: \(b.fours)x4s, \(b.sixes)x6s, SR: \(b.strikeRate)"
        }
        return nil
    }
    // Helper: Get bowler stats (real)
    private func bowlerStats(for name: String) -> String? {
        if let bw = bowler, bw.name == name {
            return "Overs: \(bw.overs), Runs: \(bw.runs), Wkts: \(bw.wickets), Econ: \(bw.economy)"
        }
        return nil
    }
}

// Event type for commentary
private enum CommentaryEventType {
    case wicket, four, six, dot, run, other
    static func from(text: String) -> CommentaryEventType {
        let lower = text.lowercased()
        if lower.contains("wicket") || lower.contains("out") || lower.contains("lbw") || lower.contains("bowled") || lower.contains("caught") { return .wicket }
        if lower.contains("six") { return .six }
        if lower.contains("four") { return .four }
        if lower.contains("no run") || lower == "dot ball." || lower == "dot" { return .dot }
        if let first = lower.split(separator: " ").first, ["1","2","3"].contains(first) { return .run }
        return .other
    }
    var icon: String {
        switch self {
        case .wicket: return "figure.cricket"
        case .four: return "4.circle"
        case .six: return "6.circle"
        case .dot: return "circle"
        case .run: return "arrowtriangle.right.fill"
        case .other: return "ellipsis"
        }
    }
    var backgroundColor: Color {
        switch self {
        case .wicket: return .red
        case .four: return .blue
        case .six: return .green
        case .dot: return .gray
        case .run: return .blue
        case .other: return .white
        }
    }
    var borderColor: Color {
        switch self {
        case .wicket: return .red
        case .four: return .blue
        case .six: return .green
        case .dot: return .gray
        case .run: return .blue
        case .other: return .white.opacity(0.3)
        }
    }
    var isHighlight: Bool {
        switch self {
        case .wicket, .four, .six: return true
        default: return false
        }
    }
}

// Ball number pill
private struct BallPill: View {
    let ball: String
    let event: CommentaryEventType
    var body: some View {
        Text(ball)
            .font(.system(size: 13, weight: .bold, design: .rounded))
            .foregroundColor(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(event.backgroundColor.opacity(0.7))
            .cornerRadius(8)
    }
}
// Event icon
private struct EventIcon: View {
    let event: CommentaryEventType
    var body: some View {
        Image(systemName: event.icon)
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(event.borderColor)
            .shadow(color: event.borderColor.opacity(0.18), radius: 2, y: 1)
    }
}
// Event summary row (last 6 balls)
private struct EventSummaryRow: View {
    let commentary: [CommentaryItem]
    var body: some View {
        let last6 = commentary.suffix(6)
        HStack(spacing: 10) {
            Text("Last 6 balls:")
                .font(.caption)
                .foregroundColor(.white.opacity(0.7))
            ForEach(Array(last6.enumerated()), id: \.offset) { _, item in
                let event = CommentaryEventType.from(text: item.text)
                EventIcon(event: event)
                BallPill(ball: item.ball, event: event)
            }
        }
        .padding(.vertical, 2)
        .padding(.horizontal, 8)
        .background(Color.white.opacity(0.04))
        .cornerRadius(8)
    }
}

// 1. CommentaryBallCard: break up complex view modifiers
private struct CommentaryBallCard: View {
    let idx: Int
    let item: CommentaryItem
    let event: CommentaryEventType
    let animateIdx: Int?
    let onHighlight: () -> Void

    var cardBackground: some View {
        RoundedRectangle(cornerRadius: 14)
            .stroke(event.borderColor.opacity(0.7), lineWidth: event.isHighlight ? 2.2 : 0.7)
    }

    var body: some View {
        let textView = Text(item.text.trimmingCharacters(in: CharacterSet(charactersIn: "/ ").union(.whitespacesAndNewlines)))
            .font(.body)
            .foregroundColor(.white)
            .padding(.top, 2)
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                BallPill(ball: item.ball, event: event)
                EventIcon(event: event)
                    .scaleEffect(animateIdx == idx ? 1.25 : 1.0)
                    .animation(.spring(response: 0.4, dampingFraction: 0.5), value: animateIdx == idx)
                Spacer()
            }
            textView
        }
        .padding(12)
        .background(.ultraThinMaterial)
        .overlay(cardBackground)
        .cornerRadius(14)
        .shadow(color: event.isHighlight ? event.borderColor.opacity(0.18) : Color.black.opacity(0.10), radius: event.isHighlight ? 8 : 4)
        .padding(.vertical, 4)
        .onAppear {
            if event.isHighlight {
                onHighlight()
                #if canImport(UIKit)
                if event == .wicket {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                } else if event == .four || event == .six {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: 1.0)
                }
                #endif
            }
        }
    }
}

struct MatchDetailView_Previews: PreviewProvider {
    static var previews: some View {
        MatchDetailView(match: CricketMatch(
            id: "1",
            seriesId: "series1",
            matchTitle: "Team 1 vs Team 2",
            matchType: "Test",
            status: "Live",
            teams: [
                CricketMatch.Team(name: "Team 1", score: "0-0", status: "bat", isBatting: true, logoUrl: nil),
                CricketMatch.Team(name: "Team 2", score: "0-0", status: "bowl", isBatting: false, logoUrl: nil)
            ],
            venue: "Mumbai",
            date: "2023-04-01",
            matchTime: "14:00",
            score: nil,
            currentStatus: "1st Innings",
            info: "First Test",
            recentBalls: nil,
            currentBowler: nil,
            scrapedBatsmen: nil,
            category: "International"
        ))
    }
}

// 2. OverviewTab: extract summary section
private struct OverviewTab: View {
    let match: CricketMatch
    @ObservedObject var colorFetcher: TeamLogoColorFetcher
    @ObservedObject var matchViewModel: MatchViewModel
    @Binding var showConfetti: Bool
    @Binding var confettiType: ConfettiType
    @Binding var lastEventDescription: String?
    let triggerConfetti: () -> Void
    @State private var batsmen: [ScrapedBatsman] = []
    @State private var bowler: ScrapedBowler? = nil
    @State private var recentBallsSummary: String? = nil
    @State private var liveScore: LiveScoreData? = nil
    @State private var pollingTimer: Timer? = nil
    @Namespace private var batsmanAnim
    @State private var shimmerPhase: CGFloat = 0
    @State private var pulseIdx: Int? = nil
    @State private var pulseAnim: Bool = false
    @State private var lastBallEvent: String? = nil
    @State private var celebratedMilestones: Set<String> = []
    @State private var eventOverlayType: EventOverlayType? = nil
    @State private var showAchievementToast: Bool = false
    @State private var latestAchievement: String? = nil
    @State private var unlockedAchievements: Set<Achievement.Kind> = Achievement.Kind.loadUnlocked()

    /// Computed live team 1 score: prefer live scraped data, fall back to API data
    private var liveTeam1Score: String {
        if let live = liveScore, let first = live.teams.first {
            // Match by name prefix to handle abbreviations
            let team1Name = match.teams.first?.name ?? ""
            if first.name.localizedCaseInsensitiveContains(String(team1Name.prefix(3))) || team1Name.localizedCaseInsensitiveContains(first.name) {
                return first.score.isEmpty ? (match.teams.first?.score ?? "-") : "\(first.score) \(first.overs)"
            } else if live.teams.count > 1 {
                let second = live.teams[1]
                return second.score.isEmpty ? (match.teams.first?.score ?? "-") : "\(second.score) \(second.overs)"
            }
        }
        return match.teams.first?.score ?? "-"
    }

    /// Computed live team 2 score: prefer live scraped data, fall back to API data
    private var liveTeam2Score: String {
        if let live = liveScore, live.teams.count > 1 {
            let team2Name = match.teams.count > 1 ? match.teams[1].name : ""
            let second = live.teams[1]
            if second.name.localizedCaseInsensitiveContains(String(team2Name.prefix(3))) || team2Name.localizedCaseInsensitiveContains(second.name) {
                return second.score.isEmpty ? (match.teams.count > 1 ? match.teams[1].score : "-") : "\(second.score) \(second.overs)"
            } else {
                let first = live.teams[0]
                return first.score.isEmpty ? (match.teams.count > 1 ? match.teams[1].score : "-") : "\(first.score) \(first.overs)"
            }
        }
        return match.teams.count > 1 ? match.teams[1].score : "-"
    }

    /// Computed live match status: prefer live scraped data, fall back to API data
    private var liveStatus: String {
        if let live = liveScore, !live.status.isEmpty {
            return live.status
        }
        return match.currentStatus
    }

    var summarySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !batsmen.isEmpty {
                HStack(spacing: 12) {
                    ForEach(batsmen, id: \.name) { batsman in
                        batsmanCard(for: batsman)
                    }
                }
                .animation(.spring(response: 0.5, dampingFraction: 0.7), value: batsmen)
            }
            if let bw = bowler {
                bowlerSection(for: bw)
            }
            if let recent = recentBallsSummary {
                recentBallsSection(for: recent)
            }
        }
        .padding(10)
        .background(.ultraThinMaterial)
        .cornerRadius(18)
        .shadow(color: Color.black.opacity(0.13), radius: 10, y: 2)
        .padding(.horizontal)
    }

    // Extract batsman card to reduce complexity
    private func batsmanCard(for batsman: ScrapedBatsman) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            batsmanNameSection(for: batsman)
            Text("Runs: \(batsman.runs), Balls: \(batsman.balls), SR: \(batsman.strikeRate)")
                .font(.caption2)
                .foregroundColor(.white.opacity(0.7))
        }
        .padding(6)
        .background(batsman.isStriker ? Color.blue.opacity(0.13) : Color.white.opacity(0.03))
        .cornerRadius(12)
        .transition(.asymmetric(
            insertion: .move(edge: .bottom).combined(with: .scale(scale: 0.8, anchor: .bottom)).combined(with: .opacity),
            removal: .move(edge: .top).combined(with: .scale(scale: 0.8, anchor: .top)).combined(with: .opacity)
        ))
        .animation(.spring(response: 0.5, dampingFraction: 0.7), value: batsmen)
    }

    // Extract batsman name section to reduce complexity
    private func batsmanNameSection(for batsman: ScrapedBatsman) -> some View {
        HStack(spacing: 4) {
            let nameText = Text(batsman.name)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(batsman.isStriker ? .white : .white.opacity(0.85))
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(batsman.isStriker ? Color.blue.opacity(0.7) : Color.white.opacity(0.08))
                .cornerRadius(16)
                .shadow(color: batsman.isStriker ? Color.blue.opacity(0.25) : .clear, radius: 8, y: 2)
                .overlay(
                    batsman.isStriker ? RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.goldPrimary.opacity(0.7), lineWidth: 2) : nil
                )
            
            if batsman.isStriker {
                nameText
                    .matchedGeometryEffect(id: "striker-pill", in: batsmanAnim)
                Text("*")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.goldPrimary)
                    .matchedGeometryEffect(id: "striker-star", in: batsmanAnim)
            } else {
                nameText
            }
        }
    }

    // Extract bowler section to reduce complexity
    private func bowlerSection(for bowler: ScrapedBowler) -> some View {
        HStack(spacing: 8) {
            Text("Bowler: ")
                .font(.system(size: 13, weight: .regular, design: .rounded))
                .foregroundColor(.green)
            Text(bowler.name)
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundColor(.green)
                .matchedGeometryEffect(id: "bowler-name", in: batsmanAnim)
            Text("Overs: \(bowler.overs), Runs: \(bowler.runs), Wkts: \(bowler.wickets), Econ: \(bowler.economy)")
                .font(.caption2)
                .foregroundColor(.white.opacity(0.7))
        }
        .padding(.vertical, 2)
        .transition(.asymmetric(
            insertion: .move(edge: .trailing).combined(with: .scale(scale: 0.8, anchor: .trailing)).combined(with: .opacity),
            removal: .move(edge: .leading).combined(with: .scale(scale: 0.8, anchor: .leading)).combined(with: .opacity)
        ))
        .animation(.spring(response: 0.5, dampingFraction: 0.7), value: bowler.name)
    }

    // Extract recent balls section to reduce complexity
    private func recentBallsSection(for recent: String) -> some View {
        let ballsArray = recent.components(separatedBy: " ")
        let balls = Array(ballsArray.prefix(8))
        return HStack(spacing: 8) {
            Text("Recent balls:")
                .font(.caption)
                .foregroundColor(.white.opacity(0.7))
            ForEach(Array(balls.enumerated()), id: \.offset) { idx, ball in
                ballCircle(for: ball, at: idx, totalBalls: balls.count)
            }
        }
        .transition(.opacity)
        .animation(.easeInOut(duration: 0.35), value: recentBallsSummary)
        .onAppear {
            pulseIdx = balls.count - 1
            pulseAnim = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
                pulseAnim = false
            }
        }
    }

    // Extract ball circle to reduce complexity
    private func ballCircle(for ball: String, at idx: Int, totalBalls: Int) -> some View {
        let color: Color
        if ball == "W" {
            color = .red
        } else if ball == "4" {
            color = .blue
        } else if ball == "6" {
            color = .green
        } else {
            color = .gray.opacity(0.5)
        }
        let isLast = idx == totalBalls - 1
        let scale: CGFloat = (isLast && pulseAnim) ? 1.35 : 1.0
        let shadowColor: Color = (isLast && pulseAnim) ? color.opacity(0.7) : .clear
        
        return Circle()
            .fill(color)
            .frame(width: 18, height: 18)
            .overlay(
                Text(ball)
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
            )
            .scaleEffect(scale)
            .shadow(color: shadowColor, radius: (isLast && pulseAnim) ? 12 : 0)
            .animation(.spring(response: 0.5, dampingFraction: 0.6), value: pulseAnim)
            .transition(.scale.combined(with: .opacity))
    }

    var body: some View {
        ZStack {
            ScrollView {
                VStack(spacing: 32) {
                    SeriesHeaderView(match: match)
                    MatchStatusBadge(status: match.status)
                    TeamSectionView(match: match, colorFetcher: colorFetcher, liveTeam1Score: liveTeam1Score, liveTeam2Score: liveTeam2Score)
                    if !liveStatus.isEmpty {
                        liveCurrentStatusSection
                    }
                    // Live run rate pills
                    if let live = liveScore, (live.currentRunRate != nil || live.requiredRunRate != nil) {
                        HStack(spacing: 16) {
                            if let crr = live.currentRunRate {
                                HStack(spacing: 4) {
                                    Text("CRR")
                                        .font(.system(size: 12, weight: .bold, design: .rounded))
                                        .foregroundColor(.secondary)
                                    Text(crr)
                                        .font(.system(size: 14, weight: .heavy, design: .rounded))
                                        .foregroundColor(.goldPrimary)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.white.opacity(0.06))
                                .cornerRadius(10)
                            }
                            if let rrr = live.requiredRunRate {
                                HStack(spacing: 4) {
                                    Text("RRR")
                                        .font(.system(size: 12, weight: .bold, design: .rounded))
                                        .foregroundColor(.secondary)
                                    Text(rrr)
                                        .font(.system(size: 14, weight: .heavy, design: .rounded))
                                        .foregroundColor(.red)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.white.opacity(0.06))
                                .cornerRadius(10)
                            }
                        }
                        .padding(.horizontal)
                    }
                    // --- SUMMARY SECTION ---
                    if !batsmen.isEmpty || bowler != nil || recentBallsSummary != nil {
                        summarySection
                    }
                    // --- END SUMMARY SECTION ---
                    LiveCommentarySection(match: match)
                    #if DEBUG
                    AddBallDemoButton(matchViewModel: matchViewModel)
                    #endif
                }
                .padding(.bottom, 40)
                .transition(.opacity)
                .animation(.easeIn(duration: 0.7), value: match.id)
            }
            Group {
                if showConfetti {
                    ConfettiOverlay(type: confettiType, team1Color: colorFetcher.team1Color, team2Color: colorFetcher.team2Color)
                        .transition(.opacity)
                        .zIndex(10)
                }
            }
            .onChange(of: batsmen) { _, _ in checkForAchievements() }
            .onChange(of: bowler) { _, _ in checkForAchievements() }
        }
        .onAppear {
            print("[DEBUG] OverviewTab .onAppear called")
            // Initial fetch
            fetchLiveData()
            // Start polling every 10 seconds for live score updates
            pollingTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { _ in
                fetchLiveData()
            }
        }
        .onDisappear {
            pollingTimer?.invalidate()
            pollingTimer = nil
        }
        .onChange(of: recentBallsSummary) { handleRecentBallsChange($0) }
        .onChange(of: match.status) { handleMatchStatusChange($0) }
        .onChange(of: batsmen) { handleBatsmenChange($0) }
        .onChange(of: bowler) { handleBowlerChange($0) }
        .onChange(of: matchViewModel.latestEventDescription) { handleLatestEventDescriptionChange($0) }
        .onChange(of: showAchievementToast) { _, newValue in
            if newValue {
                #if canImport(UIKit)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                #endif
            }
        }
    }

    // Extract onChange handlers to reduce complexity
    private func handleRecentBallsChange(_ newBalls: String?) {
        guard let balls = newBalls?.components(separatedBy: " "), let latest = balls.last, latest != lastBallEvent else { return }
        // Ball event confetti
        if latest == "4" || latest == "6" {
            confettiType = .boundary
            triggerConfetti()
            #if canImport(UIKit)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: 1.0)
            #endif
        } else if latest == "W" {
            confettiType = .wicket
            triggerConfetti()
            #if canImport(UIKit)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            #endif
        } else {
            #if canImport(UIKit)
            UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.7)
            #endif
        }
        lastBallEvent = latest
        pulseAnim = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
            pulseAnim = false
        }
    }

    private func handleMatchStatusChange(_ newStatus: String) {
        let status = newStatus.lowercased()
        if status.contains("won") || status.contains("win") || status.contains("victory") {
            confettiType = .win
            triggerConfetti()
        }
    }

    private func handleBatsmenChange(_ newBatsmen: [ScrapedBatsman]) {
        for batsman in newBatsmen {
            if let runs = Int(batsman.runs) {
                if runs >= 50 && !celebratedMilestones.contains("\(batsman.name)-50") {
                    confettiType = .milestone
                    triggerConfetti()
                    celebratedMilestones.insert("\(batsman.name)-50")
                    eventOverlayType = .fifty(batsman.name)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { eventOverlayType = nil }
                }
                if runs >= 100 && !celebratedMilestones.contains("\(batsman.name)-100") {
                    confettiType = .milestone
                    triggerConfetti()
                    celebratedMilestones.insert("\(batsman.name)-100")
                    eventOverlayType = .hundred(batsman.name)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { eventOverlayType = nil }
                }
            }
        }
    }

    private func handleBowlerChange(_ newBowler: ScrapedBowler?) {
        if let bw = newBowler, let wickets = Int(bw.wickets) {
            if wickets >= 5 && !celebratedMilestones.contains("\(bw.name)-5w") {
                confettiType = .milestone
                triggerConfetti()
                celebratedMilestones.insert("\(bw.name)-5w")
            }
            // Hat-trick detection (if you have ball-by-ball wicket sequence, add logic here)
            // For demo, if wickets == 3, show hat-trick overlay
            if bw.hatTrick && !celebratedMilestones.contains("\(bw.name)-hattrick") {
                eventOverlayType = .hatTrick(bw.name)
                celebratedMilestones.insert("\(bw.name)-hattrick")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { eventOverlayType = nil }
            }
        }
    }

    private func handleLatestEventDescriptionChange(_ newValue: String) {
        // No longer needed for confetti triggers, but can be used for display
        lastEventDescription = newValue.lowercased()
    }

    // Fetch live data from scraper (batsmen, bowler, recent balls, and live scores)
    private func fetchLiveData() {
        Task {
            if let result = try? await CricBuzzScraper().fetchBallByBallAndBatsmen(matchId: match.id) {
                await MainActor.run {
                    if batsmen != result.batsmen {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            batsmen = result.batsmen
                        }
                    }
                    if bowler != result.bowler {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            bowler = result.bowler
                        }
                    }
                    if recentBallsSummary != result.recentBallsSummary {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            recentBallsSummary = result.recentBallsSummary
                        }
                    }
                    if liveScore != result.liveScore {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            liveScore = result.liveScore
                        }
                    }
                }
            }
        }
    }

    // Check for batting/bowling achievements and show toast
    private func checkForAchievements() {
        for batsman in batsmen {
            if (Int(batsman.runs) ?? 0) >= 30 { unlock(.runs30, msg: "3️⃣0️⃣ \(batsman.name) scored 30 runs!") }
            if (Int(batsman.runs) ?? 0) >= 50 { unlock(.runs50, msg: "🏅 \(batsman.name) scored a Fifty!") }
            if (Int(batsman.runs) ?? 0) >= 75 { unlock(.runs75, msg: "7️⃣5️⃣ \(batsman.name) scored 75 runs!") }
            if (Int(batsman.runs) ?? 0) >= 100 { unlock(.runs100, msg: "🎖️ \(batsman.name) scored a Century!") }
            if (Int(batsman.runs) ?? 0) >= 150 { unlock(.runs150, msg: "1️⃣5️⃣0️⃣ \(batsman.name) scored 150 runs!") }
            if (Int(batsman.runs) ?? 0) >= 200 { unlock(.runs200, msg: "💯💯 \(batsman.name) scored a Double Century!") }
            if (Int(batsman.runs) ?? 0) >= 250 { unlock(.runs250, msg: "2️⃣5️⃣0️⃣ \(batsman.name) scored 250 runs!") }
            if (Int(batsman.runs) ?? 0) >= 300 { unlock(.runs300, msg: "3️⃣0️⃣0️⃣ \(batsman.name) scored a Triple Century!") }
        }
        if let bowler = bowler {
            if (Int(bowler.wickets) ?? 0) >= 3 { unlock(.wickets3, msg: "🎯 \(bowler.name) took 3 wickets!") }
            if (Int(bowler.wickets) ?? 0) >= 4 { unlock(.wickets4, msg: "4️⃣ \(bowler.name) took 4 wickets!") }
            if (Int(bowler.wickets) ?? 0) >= 5 { unlock(.wickets5, msg: "5️⃣ \(bowler.name) took 5 wickets!") }
            if (Int(bowler.wickets) ?? 0) >= 10 { unlock(.wickets10, msg: "🔟 \(bowler.name) took 10 wickets!") }
            if bowler.hatTrick { unlock(.hatTrick, msg: "🎩 \(bowler.name) took a Hat-trick!") }
        }
        for team in match.teams {
            if let score = Int(team.score.components(separatedBy: "/").first ?? "0"), score >= 200 {
                unlock(.team200, msg: "2️⃣0️⃣0️⃣ \(team.name) scored 200+ runs!")
            }
        }
    }

    private func unlock(_ kind: Achievement.Kind, msg: String) {
        if !unlockedAchievements.contains(kind) {
            unlockedAchievements.insert(kind)
            Achievement.Kind.saveUnlocked(unlockedAchievements)
            latestAchievement = msg
            confettiType = .milestone
            triggerConfetti()
            showAchievementToast = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { showAchievementToast = false }
        }
    }

    // Live status section that uses live scraped data
    private var liveCurrentStatusSection: some View {
        Text(liveStatus)
            .font(.system(size: 17, weight: .semibold, design: .rounded))
            .foregroundColor(.goldPrimary)
            .multilineTextAlignment(.center)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity)
            .background(Color.white.opacity(0.06))
            .cornerRadius(12)
            .padding(.horizontal)
            .transition(.opacity)
            .animation(.easeIn(duration: 0.7), value: liveStatus)
            .overlay(liveShimmerOverlay)
            .onAppear {
                withAnimation(Animation.linear(duration: 1.6).repeatForever(autoreverses: false)) {
                    shimmerPhase = 1.0
                }
            }
    }

    // Shimmer overlay for live status
    private var liveShimmerOverlay: some View {
        LinearGradient(
            gradient: Gradient(colors: [Color.white.opacity(0.0), Color.white.opacity(0.45), Color.white.opacity(0.0)]),
            startPoint: .leading, endPoint: .trailing
        )
        .blendMode(.plusLighter)
        .mask(
            Text(liveStatus)
                .font(.system(size: 17, weight: .semibold, design: .rounded))
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity)
        )
        .offset(x: shimmerPhase * 120)
        .opacity(0.7)
    }

    // Extract current status section to reduce complexity
    private var currentStatusSection: some View {
        Text(match.currentStatus)
            .font(.system(size: 17, weight: .semibold, design: .rounded))
            .foregroundColor(.goldPrimary)
            .multilineTextAlignment(.center)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity)
            .background(Color.white.opacity(0.06))
            .cornerRadius(12)
            .padding(.horizontal)
            .transition(.opacity)
            .animation(.easeIn(duration: 0.7), value: match.currentStatus)
            .overlay(shimmerOverlay)
            .onAppear {
                withAnimation(Animation.linear(duration: 1.6).repeatForever(autoreverses: false)) {
                    shimmerPhase = 1.0
                }
            }
    }

    // Extract shimmer overlay to reduce complexity
    private var shimmerOverlay: some View {
        LinearGradient(
            gradient: Gradient(colors: [Color.white.opacity(0.0), Color.white.opacity(0.45), Color.white.opacity(0.0)]),
            startPoint: .leading, endPoint: .trailing
        )
        .blendMode(.plusLighter)
        .mask(
            Text(match.currentStatus)
                .font(.system(size: 17, weight: .semibold, design: .rounded))
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity)
        )
        .offset(x: shimmerPhase * 120)
        .opacity(0.7)
    }

    // Add a toast or badge overlay for achievements
    private var achievementOverlay: some View {
        Group {
            if showAchievementToast, let achievement = latestAchievement {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Text(achievement)
                            .padding()
                            .background(BlurView(style: .systemMaterialDark))
                            .cornerRadius(16)
                            .shadow(radius: 8)
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                            .zIndex(20)
                        Spacer()
                    }
                    .padding(.bottom, 40)
                }
                .animation(.easeInOut, value: showAchievementToast)
            }
        }
    }
}

#if DEBUG
struct AddBallDemoButton: View {
    @ObservedObject var matchViewModel: MatchViewModel
    @State private var ballNumber = 0
    var body: some View {
        Button("Add Random Ball Event") {
            let eventTypes: [ProbabilityEvent.EventType] = [.boundary, .wicket, .dotBall, .other]
            let type = eventTypes.randomElement() ?? .other
            let desc: String
            switch type {
            case .boundary: desc = "Boundary! 4 runs."
            case .wicket: desc = "Wicket! Bowled."
            case .dotBall: desc = "Dot ball."
            case .other: desc = "Single taken."
            default: desc = "Ball played."
            }
            let ball = BallEvent(number: ballNumber, over: ballNumber / 6, eventType: type, description: desc)
            matchViewModel.addBallEvent(ball: ball)
            ballNumber += 1
        }
        .padding()
        .background(Color.blue.opacity(0.2))
        .cornerRadius(8)
    }
}
#endif

struct StatisticsTab: View {
    let match: CricketMatch
    let colorFetcher: TeamLogoColorFetcher
    let matchViewModel: MatchViewModel
    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                WinProbabilityCard(match: match, colorFetcher: colorFetcher, matchViewModel: matchViewModel)
                // Add more statistics here if needed
            }
            .padding(.top, 24)
        }
    }
}

// MARK: - Gold Color Extension
extension Color {
    static let goldPrimary = Color(hex: "FFD700") // Signature gold
    static let goldSecondary = Color(hex: "FFC300") // Slightly lighter
    static let goldAccent = Color(hex: "FFB300") // Slightly deeper
}

struct EventOverlayView: View {
    let type: EventOverlayType
    @State private var animate = false
    var body: some View {
        ZStack {
            Color.black.opacity(0.001)
            VStack(spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(Color.black.opacity(0.85))
                        .background(.ultraThinMaterial)
                        .shadow(color: .goldPrimary.opacity(0.18), radius: 18, y: 6)
                    VStack(spacing: 8) {
                        Group {
                            switch type {
                            case .fifty(_):
                                Text("5️⃣0️⃣")
                                    .font(.system(size: 54, weight: .heavy, design: .rounded))
                                    .foregroundColor(.goldPrimary)
                            case .hundred(_):
                                Text("💯")
                                    .font(.system(size: 54, weight: .heavy, design: .rounded))
                                    .foregroundColor(.goldPrimary)
                            case .hatTrick(_):
                                Text("🎩")
                                    .font(.system(size: 54, weight: .heavy, design: .rounded))
                                    .foregroundColor(.goldPrimary)
                            }
                        }
                        Text(eventText)
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(.goldPrimary)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 6)
                            .background(Color.goldPrimary.opacity(0.13))
                            .cornerRadius(12)
                    }
                    .padding(.vertical, 24)
                    .padding(.horizontal, 32)
                }
            }
        }
        .edgesIgnoringSafeArea(.all)
        .onAppear {
            print("[DEBUG] EventOverlayView .onAppear called")
            withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) { animate = true }
        }
    }
    var eventText: String {
        switch type {
        case .fifty(_): return "Fifty!"
        case .hundred(_): return "Century!"
        case .hatTrick(_): return "Hat-trick!"
        }
    }
}
