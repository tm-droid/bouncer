import SwiftUI
import SwiftSoup

struct SquadView: View {
    let match: CricketMatch
    @State private var team1Squad: [Player] = []
    @State private var team2Squad: [Player] = []
    @State private var isLoading = true
    @State private var error: String? = nil
    
    var body: some View {
        ScrollView {
            VStack(spacing: 36) {
                // Centered team names/logos and VS
                HStack(alignment: .center, spacing: 24) {
                    VStack(spacing: 8) {
                        if let team1 = match.teams.first, let logoUrl = TeamLogoLoader.shared.getLogoUrl(for: team1.name), let url = URL(string: logoUrl) {
                            AsyncImage(url: url) { image in
                                image.resizable()
                            } placeholder: {
                                Color.gray
                            }
                            .frame(width: 48, height: 48)
                            .clipShape(Circle())
                        }
                        Text(match.teams.first?.name ?? "Team 1")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                    }
                    Spacer()
                    Text("vs")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 8)
                    Spacer()
                    VStack(spacing: 8) {
                        Text(match.teams.count > 1 ? match.teams[1].name : "Team 2")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                        if match.teams.count > 1, let logoUrl = TeamLogoLoader.shared.getLogoUrl(for: match.teams[1].name), let url = URL(string: logoUrl) {
                            AsyncImage(url: url) { image in
                                image.resizable()
                            } placeholder: {
                                Color.gray
                            }
                            .frame(width: 48, height: 48)
                            .clipShape(Circle())
                        }
                    }
                }
                .padding(.top, 24)
                .padding(.horizontal)

                // Team 1 Squad
                VStack(alignment: .leading, spacing: 16) {
                    Text("Squad: \(match.teams.first?.name ?? "Team 1")")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .padding(.bottom, 6)
                    if isLoading {
                        ProgressView().padding()
                    } else if let error = error {
                        Text(error).foregroundColor(.red)
                    } else {
                        ForEach(team1Squad, id: \ .name) { player in
                            NavigationLink(destination: PlayerDetailView(player: player)) {
                                PlayerCard(player: player)
                            }
                        }
                    }
                }
                .padding()
                .background(Color.white.opacity(0.10))
                .cornerRadius(20)
                .shadow(radius: 8)

                // Team 2 Squad
                VStack(alignment: .leading, spacing: 16) {
                    Text("Squad: \(match.teams.count > 1 ? match.teams[1].name : "Team 2")")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .padding(.bottom, 6)
                    if isLoading {
                        ProgressView().padding()
                    } else if let error = error {
                        Text(error).foregroundColor(.red)
                    } else {
                        ForEach(team2Squad, id: \ .name) { player in
                            NavigationLink(destination: PlayerDetailView(player: player)) {
                                PlayerCard(player: player)
                            }
                        }
                    }
                }
                .padding()
                .background(Color.white.opacity(0.10))
                .cornerRadius(20)
                .shadow(radius: 8)
            }
            .padding(.bottom, 40)
        }
        .navigationTitle("Squads")
        .background(
            LinearGradient(gradient: Gradient(colors: [.black, .gray.opacity(0.10)]), startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
        )
        .onAppear {
            Task {
                do {
                    let (team1, team2) = try await SquadScraper.fetchSquads(for: match)
                    team1Squad = team1
                    team2Squad = team2
                    isLoading = false
                } catch {
                    self.error = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }
}

struct Player: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let role: String
    let imageUrl: String
}

struct PlayerCard: View {
    let player: Player
    var body: some View {
        HStack(spacing: 14) {
            if let url = URL(string: player.imageUrl), !player.imageUrl.isEmpty {
                AsyncImage(url: url) { image in
                    image.resizable()
                } placeholder: {
                    Color.gray
                }
                .frame(width: 44, height: 44)
                .clipShape(Circle())
                .accessibilityLabel(Text(player.name + " photo"))
            } else {
                ZStack {
                    Circle().fill(Color.gray.opacity(0.3))
                    Image(systemName: "person.fill").resizable().scaledToFit().padding(8).foregroundColor(.white)
                }
                .frame(width: 44, height: 44)
                .accessibilityLabel(Text(player.name + " photo"))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(player.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                Text(player.role)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding(8)
        .background(Color.white.opacity(0.08))
        .cornerRadius(12)
        .shadow(radius: 2)
    }
}

struct PlayerDetailView: View {
    let player: Player
    @State private var playerStats: PlayerStats? = nil
    @State private var isLoading = true
    @State private var error: String? = nil
    // Use a static API key for now (replace with your own or inject from environment)
    private let apiKey = "REDACTED_ROTATE_ME"
    var body: some View {
        VStack(spacing: 18) {
            if let url = URL(string: player.imageUrl), !player.imageUrl.isEmpty {
                AsyncImage(url: url) { image in
                    image.resizable()
                } placeholder: {
                    Color.gray
                }
                .frame(width: 90, height: 90)
                .clipShape(Circle())
                .accessibilityLabel(Text(player.name + " photo"))
            } else {
                ZStack {
                    Circle().fill(Color.gray.opacity(0.3))
                    Image(systemName: "person.fill").resizable().scaledToFit().padding(16).foregroundColor(.white)
                }
                .frame(width: 90, height: 90)
                .accessibilityLabel(Text(player.name + " photo"))
            }
            Text(player.name)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundColor(.white)
            Text(player.role)
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.secondary)
            if isLoading {
                ProgressView()
            } else if let error = error {
                Text(error).foregroundColor(.red)
            } else if let stats = playerStats {
                PlayerStatsView(stats: stats)
            }
            Spacer()
        }
        .padding()
        .background(Color.black.ignoresSafeArea())
        .onAppear {
            Task {
                // let api = CricketAPIService(apiKey: apiKey)
                // playerStats = try await api.fetchPlayerStats(name: player.name)
                // isLoading = false
                // TEMP: Remove player stats loading until implemented
                isLoading = false
            }
        }
    }
}

struct PlayerStats: Codable, Hashable {
    let name: String
    let country: String
    let role: String
    let batting: [String: String]
    let bowling: [String: String]
}

struct PlayerStatsView: View {
    let stats: PlayerStats
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Country: \(stats.country)").font(.headline).foregroundColor(.white)
            Text("Role: \(stats.role)").font(.subheadline).foregroundColor(.secondary)
            Divider().background(Color.white.opacity(0.2))
            Text("Batting Stats").font(.headline).foregroundColor(.white)
            ForEach(stats.batting.sorted(by: { $0.key < $1.key }), id: \.key) { k, v in
                HStack { Text(k).foregroundColor(.secondary); Spacer(); Text(v).foregroundColor(.white) }
            }
            Divider().background(Color.white.opacity(0.2))
            Text("Bowling Stats").font(.headline).foregroundColor(.white)
            ForEach(stats.bowling.sorted(by: { $0.key < $1.key }), id: \.key) { k, v in
                HStack { Text(k).foregroundColor(.secondary); Spacer(); Text(v).foregroundColor(.white) }
            }
        }
        .padding()
        .background(Color.white.opacity(0.06))
        .cornerRadius(14)
    }
}

// SquadScraper implementation
class SquadScraper {
    static func fetchSquads(for match: CricketMatch) async throws -> ([Player], [Player]) {
        // Construct the Cricbuzz squad URL
        let matchId = match.id
        let matchTitleSlug = match.matchTitle.replacingOccurrences(of: " ", with: "-")
        let urlString = "https://www.cricbuzz.com/live-cricket-scores/\(matchId)/\(matchTitleSlug)"
        guard let url = URL(string: urlString) else {
            throw NSError(domain: "Invalid URL", code: 0)
        }
        let (data, _) = try await URLSession.shared.data(from: url)
        guard let html = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "Invalid HTML", code: 0)
        }
        let doc = try SwiftSoup.parse(html)
        // Team 1 (left)
        var team1: [Player] = []
        if let leftCol = try doc.select("div.cb-play11-lft-col").first() {
            let playerCards = try leftCol.select("a.cb-player-card-left")
            for card in playerCards {
                let nameDiv = try card.select("div.cb-player-name-left > div").first()?.html() ?? ""
                let name = nameDiv.components(separatedBy: "<br>").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let role = try card.select("span.cb-font-12.text-gray").text()
                let img = try card.select("img.cb-plyr-img-left").attr("src")
                if !name.isEmpty {
                    team1.append(Player(name: name, role: role, imageUrl: img))
                }
            }
        }
        // Team 2 (right)
        var team2: [Player] = []
        if let rightCol = try doc.select("div.cb-play11-rt-col").first() {
            let playerCards = try rightCol.select("a.cb-player-card-right")
            for card in playerCards {
                let nameDiv = try card.select("div.cb-player-name-right > div").first()?.html() ?? ""
                let name = nameDiv.components(separatedBy: "<br>").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let role = try card.select("span.cb-font-12.text-gray").text()
                let img = try card.select("img.cb-plyr-img-right").attr("src")
                if !name.isEmpty {
                    team2.append(Player(name: name, role: role, imageUrl: img))
                }
            }
        }
        if team1.isEmpty && team2.isEmpty {
            throw NSError(domain: "Error loading squads", code: 0)
        }
        return (team1, team2)
    }
} 