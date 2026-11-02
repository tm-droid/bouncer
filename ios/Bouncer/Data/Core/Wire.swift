import Foundation

/// A coding key for any string, so models can decode loosely without a `CodingKeys` enum.
struct WireKey: CodingKey {
    var stringValue: String
    var intValue: Int? { nil }
    init(_ string: String) { stringValue = string }
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { nil }
}

/// Tolerant reader over a JSON object. Every accessor returns nil rather than throwing when a key is
/// missing, null, or of an unexpected type, so one odd field never drops a whole payload. Numbers and
/// strings are interchangeable because the backend mixes them ("runs":"11" vs "runs":11).
struct Wire {
    let container: KeyedDecodingContainer<WireKey>

    init(_ decoder: Decoder) throws {
        container = try decoder.container(keyedBy: WireKey.self)
    }

    func has(_ key: String) -> Bool { container.contains(WireKey(key)) }

    func string(_ key: String) -> String? {
        let k = WireKey(key)
        if let s = try? container.decodeIfPresent(String.self, forKey: k) { return s }
        if let i = try? container.decodeIfPresent(Int64.self, forKey: k) { return String(i) }
        if let d = try? container.decodeIfPresent(Double.self, forKey: k) { return Wire.format(d) }
        if let b = try? container.decodeIfPresent(Bool.self, forKey: k) { return b ? "true" : "false" }
        return nil
    }

    func int64(_ key: String) -> Int64? {
        let k = WireKey(key)
        if let i = try? container.decodeIfPresent(Int64.self, forKey: k) { return i }
        if let d = try? container.decodeIfPresent(Double.self, forKey: k), d.isFinite { return Int64(d) }
        if let s = try? container.decodeIfPresent(String.self, forKey: k) {
            return Int64(s.trimmingCharacters(in: .whitespaces)) ?? Double(s).map { Int64($0) }
        }
        return nil
    }

    func int(_ key: String) -> Int? { int64(key).map { Int($0) } }

    func double(_ key: String) -> Double? {
        let k = WireKey(key)
        if let d = try? container.decodeIfPresent(Double.self, forKey: k) { return d }
        if let s = try? container.decodeIfPresent(String.self, forKey: k) {
            return Double(s.trimmingCharacters(in: .whitespaces))
        }
        return nil
    }

    func bool(_ key: String) -> Bool? {
        let k = WireKey(key)
        if let b = try? container.decodeIfPresent(Bool.self, forKey: k) { return b }
        if let s = try? container.decodeIfPresent(String.self, forKey: k) {
            switch s.lowercased() {
            case "true", "1": return true
            case "false", "0": return false
            default: return nil
            }
        }
        if let i = try? container.decodeIfPresent(Int.self, forKey: k) { return i != 0 }
        return nil
    }

    /// A nested object or any `Decodable`, nil when absent or malformed.
    func value<T: Decodable>(_ key: String, as type: T.Type = T.self) -> T? {
        (try? container.decodeIfPresent(T.self, forKey: WireKey(key))) ?? nil
    }

    /// An array whose malformed elements are skipped instead of failing the whole array.
    func array<T: Decodable>(_ key: String, of type: T.Type = T.self) -> [T] {
        let lossy = (try? container.decodeIfPresent([Lossy<T>].self, forKey: WireKey(key))) ?? nil
        return (lossy ?? []).compactMap(\.value)
    }

    static func format(_ d: Double) -> String {
        if d == d.rounded(), abs(d) < 1e15 { return String(Int64(d)) }
        return String(d)
    }
}

struct Lossy<T: Decodable>: Decodable {
    let value: T?
    init(from decoder: Decoder) throws {
        value = try? T(from: decoder)
    }
}

enum WireJSON {
    /// Shared decoder; kept in one place so every model sees the same configuration.
    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        try JSONDecoder().decode(T.self, from: data)
    }

    static func decode<T: Decodable>(_ type: T.Type, from string: String) throws -> T {
        try decode(type, from: Data(string.utf8))
    }

    /// JSON-escape one string (no surrounding handling of `/`), used when hand-building query JSON.
    static func quote(_ s: String) -> String {
        var out = "\""
        for scalar in s.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if scalar.value < 0x20 { out += String(format: "\\u%04x", scalar.value) } else { out.unicodeScalars.append(scalar) }
            }
        }
        return out + "\""
    }
}
