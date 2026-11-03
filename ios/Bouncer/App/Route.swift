import Foundation

/// Typed navigation destinations, shared by every tab's NavigationStack.
enum Route: Hashable {
    case match(id: String)
    case news(id: Int)
    case series(id: String)
    /// `id` is Cricbuzz's profile id when known; scorecard rows only carry a name.
    case player(id: String?, name: String)
    case team(name: String)
    case search
}
