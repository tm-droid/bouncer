import Foundation

/// Typed navigation destinations, shared by every tab's NavigationStack.
enum Route: Hashable {
    case match(id: String)
    case news(id: Int)
    case series(id: String)
}
