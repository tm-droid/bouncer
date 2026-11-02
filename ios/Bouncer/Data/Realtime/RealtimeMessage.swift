import Foundation

/// One Appwrite Realtime `event` message.
struct RealtimeMessage: Equatable, Sendable {
    /// e.g. `databases.bouncer-db.tables.match-live-detail.rows.171297.update`
    var events: [String]
    /// Channels the event matched.
    var channels: [String]
    /// The changed row/document as JSON bytes (re-serialised from the envelope).
    var payload: Data

    var isWrite: Bool { events.contains { $0.contains(".create") || $0.contains(".update") } }

    func matches(channel: String) -> Bool { channels.contains(channel) }
}

enum RealtimeEvent: Equatable, Sendable {
    /// The socket (re)connected. `isRecovery` is true when the previous connection was lost, in which
    /// case events may have been missed and the subscriber should catch up.
    case connected(isRecovery: Bool)
    case message(RealtimeMessage)
}

enum RealtimeFrame: Equatable, Sendable {
    case connected
    case pong
    case message(RealtimeMessage)
    case error(code: Int, message: String)
    case ignored

    /// Parses a text frame: `{"type":"connected"|"pong"|"event"|"error","data":{...}}`.
    static func parse(_ text: String) -> RealtimeFrame {
        guard let obj = try? JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
              let type = obj["type"] as? String else { return .ignored }
        let data = obj["data"] as? [String: Any]
        switch type {
        case "connected": return .connected
        case "pong": return .pong
        case "error":
            return .error(code: (data?["code"] as? Int) ?? 0, message: (data?["message"] as? String) ?? "")
        case "event":
            guard let data, let payload = data["payload"],
                  JSONSerialization.isValidJSONObject(payload),
                  let bytes = try? JSONSerialization.data(withJSONObject: payload) else { return .ignored }
            return .message(RealtimeMessage(events: data["events"] as? [String] ?? [],
                                            channels: data["channels"] as? [String] ?? [],
                                            payload: bytes))
        default: return .ignored
        }
    }
}

enum RealtimeBackoff {
    static let minDelay: TimeInterval = 2
    static let maxDelay: TimeInterval = 60

    /// 2s, 4s, 8s ... capped at 60s, then +/-20% jitter. `attempt` counts failures from 1.
    static func delay(attempt: Int, jitter: Double = Double.random(in: -0.2...0.2)) -> TimeInterval {
        let exp = min(maxDelay, minDelay * pow(2, Double(max(0, attempt - 1))))
        return min(maxDelay, max(minDelay, exp * (1 + jitter)))
    }
}
