import Foundation

/// Coalesces concurrent calls for the same key into one in-flight task, so ten views asking for the
/// live list at once cost one download.
actor SingleFlight<Key: Hashable & Sendable, Value: Sendable> {
    private var tasks: [Key: Task<Value, Error>] = [:]

    func run(_ key: Key, _ operation: @escaping @Sendable () async throws -> Value) async throws -> Value {
        if let existing = tasks[key] { return try await existing.value }
        let task = Task { try await operation() }
        tasks[key] = task
        do {
            let value = try await task.value
            tasks[key] = nil
            return value
        } catch {
            tasks[key] = nil
            throw error
        }
    }
}

/// A value and when it was loaded, for TTL checks.
struct Cached<Value: Sendable>: Sendable {
    var value: Value
    var loadedAt: Date

    func isFresh(ttl: TimeInterval, now: Date) -> Bool { now.timeIntervalSince(loadedAt) < ttl }
}
