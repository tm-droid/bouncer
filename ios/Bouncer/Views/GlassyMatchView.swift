import SwiftUI

struct GlassyMatchView: View {
    let match: CricketMatch
    @EnvironmentObject private var liveActivityManager: LiveActivityManager
    @State private var animateScore = false
    @State private var animateLive = false
    @State private var isActivityEnabled: Bool = false
    
    func teamColor(for team: CricketMatch.Team) -> Color {
        let colors: [String: Color] = [
            "India": .blue, "Australia": .yellow, "England": .red, "Pakistan": .green, "South Africa": .green, "New Zealand": .black, "Sri Lanka": .blue, "Bangladesh": .green, "West Indies": .purple
        ]
        return colors[team.name] ?? .gray
    }
    
    var team1Color: Color {
        match.teams.first.map(teamColor(for:)) ?? .blue
    }
    var team2Color: Color {
        match.teams.count > 1 ? teamColor(for: match.teams[1]) : .green
    }
    
    var isLive: Bool {
        match.status.lowercased().contains("live")
    }
    
    var body: some View {
        ZStack {
            // Glassy dark background
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.black.opacity(0.92))
                .background(
                    LinearGradient(
                        gradient: Gradient(colors: [team1Color.opacity(0.22), team2Color.opacity(0.22)]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .shadow(color: .black.opacity(0.32), radius: 18, x: 0, y: 8)
            VStack(spacing: 0) {
                HStack(alignment: .center, spacing: 4) {
                    // Team 1 logo
                    if let team1 = match.teams.first, let logoUrl = TeamLogoLoader.shared.getLogoUrl(for: team1.name), let url = URL(string: logoUrl) {
                        AsyncImage(url: url) { image in
                            image.resizable()
                        } placeholder: {
                            Color.gray
                        }
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(teamColor(for: team1), lineWidth: 2))
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(team1.name) + Text(" logo"))
                    } else {
                        ZStack {
                            Circle().fill(Color.gray.opacity(0.3))
                            Image(systemName: "cricket.ball.fill").resizable().scaledToFit().padding(8).foregroundColor(.white)
                        }
                        .frame(width: 44, height: 44)
                        .overlay(Circle().stroke(teamColor(for: match.teams.first ?? CricketMatch.Team(name: "", score: "0-0", status: "", isBatting: false, logoUrl: nil)), lineWidth: 2))
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(match.teams.first?.name ?? "") + Text(" logo"))
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(match.teams.first?.name ?? "-")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                        if let score = match.teams.first?.score {
                            Text(score.replacingOccurrences(of: "-", with: "/"))
                                .font(.system(size: 24, weight: .heavy, design: .rounded))
                                .foregroundColor(.goldPrimary)
                                .scaleEffect(animateScore ? 1.1 : 1.0)
                                .animation(.spring(), value: animateScore)
                                .minimumScaleFactor(0.5)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .layoutPriority(1)
                        }
                    }
                    Spacer()
                    VStack(alignment: .center, spacing: 6) {
                        // Simplified Innings/overs pill
                        if let currentInningsText = match.score?.currentInnings, !currentInningsText.isEmpty {
                            Text(currentInningsText) // Display the raw currentInningsText and let it scale/truncate
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.white.opacity(0.9))
                                .minimumScaleFactor(0.7)
                                .lineLimit(1)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Color.white.opacity(0.12))
                                .clipShape(Capsule())
                        }
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(match.teams.count > 1 ? match.teams[1].name : "-")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                        if match.teams.count > 1 {
                            Text(match.teams[1].score.replacingOccurrences(of: "-", with: "/"))
                                .font(.system(size: 24, weight: .heavy, design: .rounded))
                                .foregroundColor(.goldPrimary)
                                .scaleEffect(animateScore ? 1.1 : 1.0)
                                .animation(.spring(), value: animateScore)
                                .minimumScaleFactor(0.5)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .layoutPriority(1)
                        }
                    }
                    // Team 2 logo
                    if match.teams.count > 1, let logoUrl = TeamLogoLoader.shared.getLogoUrl(for: match.teams[1].name), let url = URL(string: logoUrl) {
                        AsyncImage(url: url) { image in
                            image.resizable()
                        } placeholder: {
                            Color.gray
                        }
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(teamColor(for: match.teams[1]), lineWidth: 2))
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(match.teams[1].name) + Text(" logo"))
                    } else if match.teams.count > 1 {
                        ZStack {
                            Circle().fill(Color.gray.opacity(0.3))
                            Image(systemName: "cricket.ball.fill").resizable().scaledToFit().padding(8).foregroundColor(.white)
                        }
                        .frame(width: 44, height: 44)
                        .overlay(Circle().stroke(teamColor(for: match.teams[1]), lineWidth: 2))
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(match.teams[1].name) + Text(" logo"))
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 18)
                // Batsmen info (reference style)
                if let batsmen = liveActivityManager.currentMatches.first(where: { $0.id == match.id })?.score?.batsmen, !batsmen.isEmpty {
                    HStack {
                        Image(systemName: "person.fill").font(.caption2).foregroundColor(.white.opacity(0.7))
                        Text("Batsmen:")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            ForEach(batsmen.prefix(2), id: \.name) { batsman in
                                Text("\(batsman.name) (\(batsman.runs) runs, \(batsman.balls) balls)")
                                    .font(.system(size: 14, weight: .regular, design: .rounded))
                                    .foregroundColor(.white)
                                    .minimumScaleFactor(0.7)
                                    .lineLimit(1)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(10)
                }
                
                // Bowler info (reference style)
                if let bowler = liveActivityManager.currentMatches.first(where: { $0.id == match.id })?.score?.bowlers.first {
                    HStack {
                        Image(systemName: "figure.bowling").font(.caption2).foregroundColor(.white.opacity(0.7))
                        Text("Bowler:")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        Spacer()
                        Text("\(bowler.name) (\(bowler.overs) overs, \(bowler.wickets) wickets)")
                            .font(.system(size: 14, weight: .regular, design: .rounded))
                            .foregroundColor(.white)
                            .minimumScaleFactor(0.7)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(10)
                }
                
                // Match status and notes
                VStack(alignment: .leading, spacing: 4) {
                    Text(match.status)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                    
                    if !match.info.isEmpty {
                        Text(match.info)
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(.white.opacity(0.6))
                            .multilineTextAlignment(.leading)
                            .lineLimit(2)
                    }
                    // Display last fused AI commentary if available
                    if let ai = liveActivityManager.aiCommentary[match.id], !ai.isEmpty {
                        Text(ai)
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(.mint.opacity(0.9))
                            .multilineTextAlignment(.leading)
                            .lineLimit(2)
                            .padding(.top, 6)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 18)
                
                // Live Activity Button
                Button(action: {
                    Task {
                        if isActivityEnabled {
                            await liveActivityManager.endActivity(for: match.id)
                            isActivityEnabled = false
                        } else {
                            await liveActivityManager.addActivity(for: match)
                            isActivityEnabled = true
                        }
                    }
                }) {
                    HStack {
                        Image(systemName: isActivityEnabled ? "bell.slash.fill" : "bell.badge.fill")
                            .foregroundColor(.white)
                        Text(isActivityEnabled ? "Stop Updates" : "Live Updates")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(isActivityEnabled ? Color.red.opacity(0.15) : Color.white.opacity(0.15))
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(isActivityEnabled ? Color.red.opacity(0.3) : Color.white.opacity(0.3), lineWidth: 1)
                            )
                    )
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 8)
        .onAppear {
            isActivityEnabled = liveActivityManager.activeActivities.contains(match.id)
        }
    }
} 
