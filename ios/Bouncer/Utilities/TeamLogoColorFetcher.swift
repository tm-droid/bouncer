import Foundation
import SwiftUI
import UIKit // For UIImage
import UIImageColors // Assumed to be available in your project

class TeamLogoColorFetcher: ObservableObject {
    @Published var team1Color: Color = .orange // Default fallback color
    @Published var team2Color: Color = .blue  // Default fallback color

    func fetchColors(for team1LogoUrl: String?, team2LogoUrl: String?) async {
        await withTaskGroup(of: (Int, Color?).self) { group in
            if let urlString1 = team1LogoUrl, let url1 = URL(string: urlString1) {
                group.addTask {
                    return (1, await self.extractPrimaryColor(from: url1))
                }
            }

            if let urlString2 = team2LogoUrl, let url2 = URL(string: urlString2) {
                group.addTask {
                    return (2, await self.extractPrimaryColor(from: url2))
                }
            }

            for await (teamIndex, color) in group {
                await MainActor.run {
                    if let extractedColor = color {
                        if teamIndex == 1 {
                            self.team1Color = extractedColor
                        } else {
                            self.team2Color = extractedColor
                        }
                    }
                }
            }
        }
    }

    private func extractPrimaryColor(from url: URL) async -> Color? {
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            if let uiImage = UIImage(data: data) {
                return await withCheckedContinuation { continuation in
                    uiImage.getColors { colors in
                        if let primary = colors?.primary {
                            continuation.resume(returning: Color(uiColor: primary))
                        } else {
                            continuation.resume(returning: nil)
                        }
                    }
                }
            }
        } catch {
            print("Error fetching or processing image from \(url): \\(error.localizedDescription)")
        }
        return nil
    }
} 