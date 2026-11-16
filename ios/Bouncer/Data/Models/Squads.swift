import Foundation

/// One player on a match's team sheet. `id` is Cricbuzz's profile id.
struct SquadPlayer: Equatable, Hashable, Sendable, Identifiable {
    var id: String
    var name: String
    var role: String
    var captain: Bool
    var keeper: Bool
    /// Cricbuzz head-shot; nil when the player has none.
    var imageURL: URL? = nil
}

/// One side's team sheet. `playingXI`/`bench` exist once the toss is done; before it only `squad`.
struct TeamSquad: Equatable, Hashable, Sendable {
    var teamId: String
    var name: String
    var shortName: String
    var playingXI: [SquadPlayer]
    var bench: [SquadPlayer]
    var squad: [SquadPlayer]

    var hasPlayingXI: Bool { !playingXI.isEmpty }
}

struct Squads: Equatable, Sendable {
    var team1: TeamSquad
    var team2: TeamSquad

    var hasPlayingXI: Bool { team1.hasPlayingXI && team2.hasPlayingXI }
}
