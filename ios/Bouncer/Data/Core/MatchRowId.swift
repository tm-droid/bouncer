import Foundation

/// Row/file ids the backend writes a match under. Must stay byte-for-byte equivalent to
/// `sanitizeMatchId` in `appwrite/functions/match-sync/src/main.js`:
/// `String(id).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 36)`.
/// A mismatch does not throw; it produces a Realtime subscription that never fires.
enum MatchRowId {
    static let rowIdLength = 36
    static let contextIdLength = 32

    private static func strip(_ id: String) -> String {
        var out = String.UnicodeScalarView()
        for s in id.unicodeScalars {
            switch s.value {
            case 0x30...0x39, 0x41...0x5A, 0x61...0x7A, 0x2E, 0x5F, 0x2D: out.append(s)
            default: break
            }
        }
        return String(out)
    }

    /// Row id for `match-live-detail` / `match-scorecard`.
    static func sanitize(_ id: String) -> String {
        String(strip(id).prefix(rowIdLength))
    }

    /// Id used in the `mc-{id}` match-context file name.
    static func contextId(_ id: String) -> String {
        String(strip(id).prefix(contextIdLength))
    }

    static func contextFile(_ id: String) -> String { "mc-" + contextId(id) }
}
