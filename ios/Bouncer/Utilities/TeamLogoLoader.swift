import Foundation

class TeamLogoLoader: ObservableObject {
    static let shared = TeamLogoLoader()
    @Published var teamLogos: [String: String] = [:]
    
    private init() {
        loadTeamLogos()
    }
    
    private func loadTeamLogos() {
        guard let url = Bundle.main.url(forResource: "TeamLogos", withExtension: "json") else {
            print("TeamLogos.json not found in bundle")
            return
        }
        
        do {
            let data = try Data(contentsOf: url)
            let logos = try JSONDecoder().decode([String: String].self, from: data)
            DispatchQueue.main.async {
                self.teamLogos = logos
            }
        } catch {
            print("Error loading team logos: \(error)")
        }
    }
    
    func getLogoUrl(for teamName: String) -> String? {
        return teamLogos[teamName]
    }
} 