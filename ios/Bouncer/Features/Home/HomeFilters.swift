import Foundation

/// Home's list filters ("All" = off). Competition/gender/format are derived from the series text the
/// same way the Android scraper and `matchGender` do, since the list files carry no such fields.
struct HomeFilters: Equatable {
    static let all = "All"
    static let competitions = [all, "International", "League", "Domestic"]
    static let genders = [all, "Men", "Women"]
    static let formats = [all, "T20", "ODI", "Test", "First Class"]

    var competition = all
    var gender = all
    var format = all
    var myTeams = false

    var isActive: Bool { competition != Self.all || gender != Self.all || format != Self.all || myTeams }

    /// How the empty state names the filter: "women's league T20", "your teams'".
    var label: String {
        [gender != Self.all ? "\(gender.lowercased())'s" : nil,
         competition != Self.all ? competition.lowercased() : nil,
         format != Self.all ? format : nil,
         myTeams ? "your teams'" : nil].compactMap { $0 }.joined(separator: " ")
    }

    @MainActor func accepts(_ m: MatchSummary, favourites: FavouritesStore) -> Bool {
        (competition == Self.all || MatchClassifier.competition(m) == competition)
            && (gender == Self.all || MatchClassifier.gender(m) == gender)
            && (format == Self.all || MatchClassifier.format(m).localizedCaseInsensitiveContains(format))
            && (!myTeams || favourites.matches(m))
    }
}

/// Mirrors CricBuzzScraper.classifyCompetitionType / mapMatchFormat and SeriesClassification.isWomensSeries.
enum MatchClassifier {
    private static let international = ["tour of", "world cup", "champions trophy", "asia cup", "icc ", "tri-series",
                                        "tri series", "bilateral", #"\d+(?:st|nd|rd|th) test"#,
                                        #"\d+(?:st|nd|rd|th) odi"#, #"\d+(?:st|nd|rd|th) t20i"#]
    private static let league = ["ipl", "bbl", "big bash", "psl", "cpl", "caribbean premier", "sa20", "lpl", "bpl",
                                 "ilt20", "mlc", "major league cricket", "the hundred", "hundred", "vitality blast",
                                 "t20 blast", "syed mushtaq ali", "vijay hazare"]

    static func competition(_ m: MatchSummary) -> String {
        let s = "\(m.seriesName) \(m.matchDesc)".lowercased()
        if international.contains(where: { $0.contains(#"\d"#) ? s.range(of: $0, options: .regularExpression) != nil : s.contains($0) }) {
            return "International"
        }
        return league.contains(where: s.contains) ? "League" : "Domestic"
    }

    static func gender(_ m: MatchSummary) -> String {
        let women = #"(?i)\bwomen(?:'?s)?\b|\bwpl\b"#
        let texts = [m.seriesName] + m.teams.prefix(2).map(\.name)
        return texts.contains { $0.range(of: women, options: .regularExpression) != nil } ? "Women" : "Men"
    }

    static func format(_ m: MatchSummary) -> String {
        switch m.matchFormat.uppercased() {
        case "TEST": "Test"
        case "ODI": "ODI"
        case "T20", "T20I": "T20"
        case "T10": "T10"
        case "HUN": "The Hundred"
        case "FC": "First Class"
        case "LIST_A": "List A"
        default: ""
        }
    }
}
