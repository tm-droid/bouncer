import Foundation
import SwiftUI

class TeamDirectory: ObservableObject {
    static let shared = TeamDirectory()
    @Published var teams: [String: (id: String, logoUrl: String)] = [:]
    private let apiKey = "REDACTED_ROTATE_ME"
    private let apiHost = "cricket-api-free-data.p.rapidapi.com"
    private let endpoint = "https://cricket-api-free-data.p.rapidapi.com/cricket-teams"
    
    func fetchTeams() async {
        guard let url = URL(string: endpoint) else { return }
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "X-RapidAPI-Key")
        request.setValue(apiHost, forHTTPHeaderField: "X-RapidAPI-Host")
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let response = json["response"] as? [[String: Any]] {
                var newTeams: [String: (id: String, logoUrl: String)] = [:]
                for team in response {
                    if let id = team["id"] as? String,
                       let title = team["title"] as? String,
                       let image = team["image"] as? String {
                        newTeams[title] = (id: id, logoUrl: image)
                    }
                }
                let teamsCopy = newTeams
                await MainActor.run {
                    self.teams = teamsCopy
                }
            }
        } catch {
            print("Failed to fetch teams: \(error)")
        }
    }
} 