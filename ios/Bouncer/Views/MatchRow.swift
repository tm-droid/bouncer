import SwiftUI
import ActivityKit

struct MatchRow: View {
    let match: CricketMatch
    @EnvironmentObject private var liveActivityManager: LiveActivityManager
    @StateObject private var matchViewModel = MatchViewModel()
    @State private var isActivityEnabled: Bool = false
    @State private var selectedMatch: CricketMatch?
    
    private var matchStatus: (text: String, color: Color, priority: Int) {
        print("Processing match status: \(match.status)") // Debug
        if match.status.contains("Stumps") || match.status.contains("won") || match.status.contains("drawn") {
            return ("Completed", .mint, 25)
        } else if match.status.contains("delayed") || match.status.contains("postponed") || match.status.contains("stops"){
            return ("Delayed", .orange, 15)
        } else if match.status.contains("abandoned") || match.status.contains("No result") {
            return ("Abandoned", .red, -15)
        } else if match.status.contains("Tea") || match.status.contains("Break") || match.status.contains("Innings Break") || match.status.contains("Lunch") {
            return ("Break", .yellow, 35)
        } else if match.status.lowercased().contains("upcoming") || match.status.lowercased().contains("preview") || match.status.isEmpty {
            return ("Upcoming", .blue, 20)
        } else {
            return ("Live", .green, 50)
        }
    }

    private var matchCategory: String {
        switch match.matchType.lowercased() {
        case "international":
            return "International"
        case "league":
            return "League"
        case "domestic":
            return "Domestic"
        case "women":
            return "Women"
        default:
            return "Other"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Match header with detailed info
            VStack(alignment: .leading, spacing: 2) {
                Text([match.teams.map { $0.name }.joined(separator: " vs "), match.matchType].filter { !$0.isEmpty }.joined(separator: ", "))
                    .font(.caption)
                    .foregroundColor(.primary)
                
                if !match.venue.isEmpty {
                    Text(match.venue)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                if !match.date.isEmpty {
                    Text(match.date)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Text(match.info)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .padding(.bottom, 2)
            
            // Status text
            Text(match.status)
                .font(.caption2)
                .foregroundColor(.secondary)
                .padding(.bottom, 2)
        
            HStack(spacing: 0) {
                // Team names with fixed width
                VStack(alignment: .leading, spacing: 8) {
                    if let team1: CricketMatch.Team = match.teams.first {
                        Text(team1.name)
                            .fontWeight(team1.isBatting ? .bold : .regular)
                            .font(.title3)
                            .foregroundColor(.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .frame(minWidth: 100, alignment: .leading)
                    }
                    if let team2: CricketMatch.Team = match.teams.dropFirst().first {
                        Text(team2.name)
                            .fontWeight(team2.isBatting ? .bold : .regular)
                            .font(.title3)
                            .foregroundColor(.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .frame(minWidth: 100, alignment: .leading)
                    }
                }
                
                Spacer()
                
                // Status indicator with colored background
                Text(matchStatus.text)
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(matchStatus.color.opacity(0.8))
                    .cornerRadius(4)
                    .fixedSize()
                
                Spacer()
                
                // Scores with fixed width
                VStack(alignment: .trailing, spacing: 8) {
                    if match.teams.count > 0 {
                        Text(match.teams[0].score.replacingOccurrences(of: "-", with: "/"))
                            .fontWeight(.semibold)
                            .font(.title3)
                            .foregroundColor(.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .frame(minWidth: 80, alignment: .trailing)
                    }
                    if match.teams.count > 1 {
                        Text(match.teams[1].score.replacingOccurrences(of: "-", with: "/"))
                            .fontWeight(.semibold)
                            .font(.title3)
                            .foregroundColor(.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .frame(minWidth: 80, alignment: .trailing)
                    }
                }
            }
        }
        .padding()
        .swipeActions {
            if Activity<CricketMatchAttributes>.activities.contains(where: { $0.attributes.matchId == match.id }) {
                Button(role: .destructive) {
                    Task {
                        await liveActivityManager.endActivity(for: match.id)
                    }
                } label: {
                    Label("Remove Live Activity", systemImage: "minus.circle")
                }
            } else {
                Button {
                    Task {
                        await liveActivityManager.addActivity(for: match)
                    }
                } label: {
                    Label("Add Live Activity", systemImage: "plus.circle")
                }
                .tint(.green)
            }
        }
        .background(
            NavigationLink(destination: MatchDetailView(match: match)) {
                EmptyView()
            }
            .opacity(0)
        )
        .contentShape(Rectangle()) // Makes the entire row tappable
    }

    private func handleMatchTap() {
        // This method will no longer be needed if NavigationLink is used for navigation
        // However, if there's other logic here not related to navigation, it should be moved.
        Task {
            await matchViewModel.fetchMatchDetails(matchId: match.id)
            // selectedMatch = match // This is now handled by NavigationLink
        }
    }
}
