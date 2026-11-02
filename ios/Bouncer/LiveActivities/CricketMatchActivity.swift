import Foundation
import SwiftUI
import ActivityKit
import WidgetKit
// import ColorArt // Uncomment if using SwiftUI-ColorArt or similar
import UIImageColors 

struct CricketMatchActivityView: View {
    let context: ActivityViewContext<CricketMatchAttributes>
    
    // Helper to get logo URL from team name
    func logoUrl(for teamName: String) -> String? {
        TeamLogoLoader.shared.getLogoUrl(for: teamName)
    }
    
    // Color extraction state
    @State private var colorArt1: Color? = nil // Replace with UIImageColors if using
    @State private var colorArt2: Color? = nil
    @State private var animateLive = false
    
    var batsmen: [BatsmanWidget] { context.state.batsmen ?? [] }
    var bowler: BowlerWidget? { context.state.bowler }
    var recentBalls: [String] { context.state.recentBalls ?? [] }
    
    var body: some View {
        ZStack {
            // Glassy dark background
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.black.opacity(0.92))
                .background(
                    LinearGradient(
                        gradient: Gradient(colors: [Color.blue.opacity(0.22), Color.green.opacity(0.22)]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .shadow(color: .black.opacity(0.32), radius: 18, x: 0, y: 8)
        VStack(spacing: 0) {
                HStack(alignment: .center, spacing: 18) {
                    // Team 1 logo
                    if let urlStr = logoUrl(for: context.attributes.team1Name), let url = URL(string: urlStr) {
                        AsyncImage(url: url) { image in
                            image.resizable()
                                .onAppear {
                                    // Load UIImage and extract colors when image appears
                                    Task {
                                        if let uiImage = await loadImageFromURL(url) {
                                            uiImage.getColors { colors in
                                                if let primary = colors?.primary {
                                                    self.colorArt1 = Color(uiColor: primary)
                                                }
                                            }
                                        }
                                    }
                                }
                        } placeholder: {
                            Color.gray
                        }
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(colorArt1 ?? Color.blue, lineWidth: 2)) // Use extracted color, fallback to blue
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(context.attributes.team1Name) + Text(" logo"))
                    } else {
                        ZStack {
                            Circle().fill(Color.gray.opacity(0.3))
                            Image(systemName: "cricket.ball.fill").resizable().scaledToFit().padding(8).foregroundColor(.white)
                        }
                        .frame(width: 44, height: 44)
                        .overlay(Circle().stroke(colorArt1 ?? Color.blue, lineWidth: 2)) // Use extracted color, fallback to blue
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(context.attributes.team1Name) + Text(" logo"))
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.attributes.team1Name)
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                        Text(context.state.team1Score.replacingOccurrences(of: "-", with: "/"))
                            .font(.system(size: 32, weight: .heavy, design: .rounded))
                            .foregroundColor(.white)
                            .transition(.scale)
                            .minimumScaleFactor(0.7)
                            .accessibilityLabel(Text("Score: ") + Text(context.state.team1Score))
                    }
                Spacer()
                    VStack(spacing: 6) {
                        if !context.state.currentInnings.isEmpty {
                            HStack(spacing: 6) {
                                Image(systemName: "cricket.ball.fill")
                                    .foregroundColor(.yellow)
                Text(context.state.currentInnings)
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.white.opacity(0.9))
                                    .minimumScaleFactor(0.7)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Capsule())
                        }
                        if let overs = context.state.currentInnings.components(separatedBy: " ").first(where: { $0.contains(".") }) {
                            HStack(spacing: 6) {
                                Image(systemName: "circle.grid.cross")
                                    .foregroundColor(.gray)
                                Text("\(overs) Overs")
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.white.opacity(0.8))
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.10))
                            .clipShape(Capsule())
                        }
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(context.attributes.team2Name)
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                        Text(context.state.team2Score.replacingOccurrences(of: "-", with: "/"))
                            .font(.system(size: 32, weight: .heavy, design: .rounded))
                            .foregroundColor(.white.opacity(0.7))
                            .transition(.scale)
                            .minimumScaleFactor(0.7)
                            .accessibilityLabel(Text("Score: ") + Text(context.state.team2Score))
                    }
                    if let urlStr = logoUrl(for: context.attributes.team2Name), let url = URL(string: urlStr) {
                        AsyncImage(url: url) { image in
                            image.resizable()
                                .onAppear {
                                    // Load UIImage and extract colors when image appears
                                    Task {
                                        if let uiImage = await loadImageFromURL(url) {
                                            uiImage.getColors { colors in
                                                if let primary = colors?.primary {
                                                    self.colorArt2 = Color(uiColor: primary)
                                                }
                                            }
                                        }
                                    }
                                }
                        } placeholder: {
                            Color.gray
                        }
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(colorArt2 ?? Color.green, lineWidth: 2)) // Use extracted color, fallback to green
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(context.attributes.team2Name) + Text(" logo"))
                    } else {
                        ZStack {
                            Circle().fill(Color.gray.opacity(0.3))
                            Image(systemName: "cricket.ball.fill").resizable().scaledToFit().padding(8).foregroundColor(.white)
                        }
                        .frame(width: 44, height: 44)
                        .overlay(Circle().stroke(colorArt2 ?? Color.green, lineWidth: 2)) // Use extracted color, fallback to green
                        .shadow(radius: 3)
                        .accessibilityLabel(Text("") + Text(context.attributes.team2Name) + Text(" logo"))
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 18)
                // Batsmen info (reference style)
                if !batsmen.isEmpty {
                    VStack(spacing: 6) {
                        ForEach(batsmen.sorted { $0.isStriker && !$1.isStriker }, id: \.name) { batsman in
            HStack {
                                if batsman.isStriker {
                                    Circle().fill(Color.blue).frame(width: 8, height: 8)
                                } else {
                                    Circle().fill(Color.clear).frame(width: 8, height: 8)
                                }
                                Text(batsman.name)
                                    .font(.system(size: 18, weight: batsman.isStriker ? .bold : .medium))
                                    .foregroundColor(batsman.isStriker ? .white : .gray)
                                Spacer()
                                Text("\(batsman.runs) (\(batsman.balls))")
                                    .font(.system(size: 18, weight: batsman.isStriker ? .bold : .medium))
                                    .foregroundColor(batsman.isStriker ? .white : .gray)
                            }
                        }
                    }
                    .padding(.vertical, 10)
                    .padding(.horizontal, 16)
                    .background(Color.white.opacity(0.07))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                // Bowler info
                if let bowler = bowler {
                    HStack(spacing: 8) {
                        Image(systemName: "person.fill").foregroundColor(.white)
                        Text("\(bowler.name) • \(bowler.overs) ov, \(bowler.runs) runs, \(bowler.wickets) wkts, \(bowler.economy) econ")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.white)
                Spacer()
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)
                }
                // Recent balls
                if !recentBalls.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(recentBalls, id: \.self) { ball in
                            Text(ball)
                                .font(.system(size: 14, weight: .bold))
                                .frame(width: 24, height: 24)
                                .background(ball == "W" ? Color.red : (ball == "4" || ball == "6" ? Color.green : Color.gray.opacity(0.3)))
                                .foregroundColor(.white)
                                .clipShape(Circle())
                        }
                    }
                    .padding(.vertical, 10)
                }
                // Pulsing LIVE badge
                if context.state.currentInnings.lowercased().contains("live") {
            HStack {
                        Text("LIVE")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 4)
                            .background(
                                Capsule()
                                    .fill(Color.red)
                                    .shadow(color: .red.opacity(animateLive ? 0.7 : 0.3), radius: animateLive ? 8 : 2)
                            )
                            .scaleEffect(animateLive ? 1.1 : 1.0)
                            .onAppear {
                                withAnimation(Animation.easeInOut(duration: 1).repeatForever(autoreverses: true)) {
                                    animateLive.toggle()
                                }
                            }
                            .accessibilityLabel("Live match")
                        Spacer()
                    }
                    .padding(.bottom, 14)
                    .padding(.top, 6)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .activitySystemActionForegroundColor(.white)
    }
    
    // Helper function to load UIImage from URL
    private func loadImageFromURL(_ url: URL) async -> UIImage? {
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            return UIImage(data: data)
        } catch {
            print("Error loading image from URL: \(error)")
            return nil
        }
    }
    
    /// Call this to start a live activity for a cricket match
    static func startLiveActivity(matchId: String, team1Name: String, team2Name: String, matchType: String, team1Score: String, team2Score: String, currentInnings: String, alertType: String? = nil, batsmen: [BatsmanWidget]? = nil, bowler: BowlerWidget? = nil, recentBalls: [String]? = nil) {
        let attributes = CricketMatchAttributes(matchId: matchId, team1Name: team1Name, team2Name: team2Name, matchType: matchType)
        let contentState = CricketMatchAttributes.ContentState(
            matchId: matchId,
            team1Score: team1Score,
            team2Score: team2Score,
            currentInnings: currentInnings,
            lastUpdated: Date(),
            alertType: alertType,
            batsmen: batsmen,
            bowler: bowler,
            recentBalls: recentBalls
        )
        let content = ActivityContent(state: contentState, staleDate: Date().addingTimeInterval(60*60))
        do {
            _ = try Activity<CricketMatchAttributes>.request(attributes: attributes, content: content, pushType: nil)
        } catch {
            print("Failed to start live activity: \(error)")
        }
    }
}

struct CricketMatchActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CricketMatchAttributes.self) { context in
            CricketMatchActivityView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(context.attributes.team1Name)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.team1Score)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.state.currentInnings.isEmpty ? "Upcoming Match" : context.state.currentInnings)
                }
            } compactLeading: {
                Text(context.attributes.team1Name.prefix(3))
            } compactTrailing: {
                if context.state.team1Score.isEmpty {
                    Text("Soon")
                } else {
                    Text(context.state.team1Score)
                }
            } minimal: {
                if context.state.currentInnings.lowercased().contains("upcoming") {
                    Text("Soon")
                } else {
                    Text("Live")
                }
            }
        }
    }
}
