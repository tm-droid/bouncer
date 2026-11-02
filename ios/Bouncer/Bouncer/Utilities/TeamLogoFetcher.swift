import Foundation
import SwiftSoup

let inputFile = "teamlogos.json"
let outputFile = "teamlogos.json" // Overwrite same file

// MARK: - Fetch the real Cricinfo logo
func fetchLogo(for teamName: String, completion: @escaping (String?) -> Void) {
    let slug = teamName.lowercased()
        .replacingOccurrences(of: " ", with: "-")
        .replacingOccurrences(of: "[^a-z0-9\\-]", with: "", options: .regularExpression)

    let guessedURL = "https://www.espncricinfo.com/team/\(slug)"

    resolveRedirect(urlString: guessedURL) { resolvedURL in
        guard let resolvedURL = resolvedURL else {
            completion(nil)
            return
        }

        URLSession.shared.dataTask(with: resolvedURL) { data, _, error in
            guard let data = data,
                  let html = String(data: data, encoding: .utf8),
                  error == nil else {
                completion(nil)
                return
            }

            do {
                let doc = try SwiftSoup.parse(html)
                if let img = try doc.select("img[alt~=(?i)\(teamName)]").first() {
                    let logoURL = try img.attr("src")
                    completion(logoURL)
                } else {
                    completion(nil)
                }
            } catch {
                completion(nil)
            }
        }.resume()
    }
}

// MARK: - Resolve redirected team URL
func resolveRedirect(urlString: String, completion: @escaping (URL?) -> Void) {
    guard let url = URL(string: urlString) else {
        completion(nil)
        return
    }

    var request = URLRequest(url: url)
    request.httpMethod = "HEAD"

    URLSession.shared.dataTask(with: request) { _, response, _ in
        completion(response?.url)
    }.resume()
}

// MARK: - Load JSON file as [String: String]
func loadTeamLogos() -> [String: String]? {
    let fileURL = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent(inputFile)
    guard let data = try? Data(contentsOf: fileURL),
          let dict = try? JSONDecoder().decode([String: String].self, from: data) else {
        return nil
    }
    return dict
}

// MARK: - Save updated JSON
func saveTeamLogos(_ dict: [String: String]) {
    let fileURL = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent(outputFile)
    if let data = try? JSONEncoder().encode(dict) {
        try? data.write(to: fileURL)
    }
}

// MARK: - Orchestrate bulk update
func updateAllLogos() {
    guard var teams = loadTeamLogos() else {
        print("❌ Failed to load \(inputFile)")
        return
    }

    let total = teams.count
    var updated: [String: String] = [:]
    let group = DispatchGroup()

    for (i, (name, cricbuzzURL)) in teams.enumerated() {
        group.enter()
        fetchLogo(for: name) { logo in
            DispatchQueue.main.async {
                if let logo = logo {
                    updated[name] = logo
                    print("[\(i + 1)/\(total)] \(name) → ✅")
                } else {
                    updated[name] = cricbuzzURL
                    print("[\(i + 1)/\(total)] \(name) → ⛔️ (kept old)")
                }
                group.leave()
            }
        }
    }

    group.notify(queue: .main) {
        saveTeamLogos(updated)
        print("✅ Done. Saved to \(outputFile)")
        CFRunLoopStop(CFRunLoopGetMain())
    }
}

updateAllLogos()
CFRunLoopRun()
