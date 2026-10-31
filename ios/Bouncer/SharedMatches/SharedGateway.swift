import Foundation
import Appwrite

/// All billable reads, executions, and stream connections pass through this gate.
/// 402 latches survive relaunch; 429 uses a short transient cooldown, per quota.
@MainActor
final class SharedGateway {
    enum Quota: String, CaseIterable { case reads, bandwidth, executions, realtime }
    struct Record: Codable { let data: Data; let savedAt: Date }
    private var cache: [String: Record] = [:]
    private final class Flight {
        let id = UUID()
        let task: Task<Data, Error>
        var waiters: Set<UUID> = []
        init(task: Task<Data, Error>) { self.task = task }
    }
    private var flights: [String: Flight] = [:]
    private var attempts: [Quota: [Date]] = [:]
    private var transientUntil: [Quota: Date] = [:]
    private let defaults: UserDefaults
    private let directory: URL
    private let now: () -> Date

    init(directory: URL? = nil, defaults: UserDefaults = .standard, now: @escaping () -> Date = Date.init) {
        self.defaults = defaults
        self.now = now
        self.directory = directory ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("SharedMatches", isDirectory: true)
        try? FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
        // Bound disk usage; last-good results survive ordinary offline launches.
        let files = (try? FileManager.default.contentsOfDirectory(at: self.directory, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        let sorted = files.sorted { ((try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast) > ((try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast) }
        for (index, file) in sorted.enumerated() {
            let date = (try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if index >= 256 || now().timeIntervalSince(date) > 14 * 86400 { try? FileManager.default.removeItem(at: file) }
        }
    }
    private func url(_ key: String) -> URL {
        directory.appendingPathComponent(Data(key.utf8).base64EncodedString().replacingOccurrences(of: "/", with: "_") + ".json")
    }
    func saved(_ key: String) -> Record? {
        if let value = cache[key] { return value }
        guard let data = try? Data(contentsOf: url(key)), let value = try? JSONDecoder().decode(Record.self, from: data) else { return nil }
        cache[key] = value
        return value
    }
    func remember(_ data: Data, key: String) {
        let value = Record(data: data, savedAt: now())
        cache[key] = value
        if cache.count > 256, let oldest = cache.min(by: { $0.value.savedAt < $1.value.savedAt })?.key { cache.removeValue(forKey: oldest) }
        if let encoded = try? JSONEncoder().encode(value) { try? encoded.write(to: url(key), options: .atomic) }
    }
    func permit(_ quota: Quota, cost: Int = 1) throws {
        let instant = now()
        if defaults.double(forKey: "shared.402.\(quota.rawValue)") > instant.timeIntervalSince1970 { throw SharedBackendError.http(402) }
        if (transientUntil[quota] ?? .distantPast) > instant { throw SharedBackendError.http(429) }
        let hour = (attempts[quota] ?? []).filter { instant.timeIntervalSince($0) < 3600 }
        let minute = hour.filter { instant.timeIntervalSince($0) < 60 }.count
        let limits: (Int, Int) = switch quota { case .executions: (12, 120); case .reads: (360, 2400); case .bandwidth: (30, 300); case .realtime: (12, 120) }
        guard minute + cost <= limits.0, hour.count + cost <= limits.1 else { throw SharedBackendError.paused }
        attempts[quota] = hour + Array(repeating: instant, count: cost)
    }
    func failure(_ error: Error, quota: Quota) {
        let code: Int? = if let e = error as? AppwriteError { e.code } else if case SharedBackendError.http(let status) = error { status } else { nil }
        if code == 402 { defaults.set(now().addingTimeInterval(6 * 3600).timeIntervalSince1970, forKey: "shared.402.\(quota.rawValue)") }
        if code == 429 { transientUntil[quota] = now().addingTimeInterval(60) }
    }
    func load(_ key: String, quota: Quota, cost: Int = 1, ttl: TimeInterval, force: Bool = false, operation: @escaping () async throws -> Data) async throws -> Data {
        if !force, let record = saved(key), now().timeIntervalSince(record.savedAt) < ttl { return record.data }
        let flight: Flight
        if let running = flights[key] { flight = running }
        else {
            try permit(quota, cost: cost)
            flight = Flight(task: Task { try Task.checkCancellation(); return try await operation() })
            flights[key] = flight
        }
        let waiter = UUID()
        flight.waiters.insert(waiter)
        defer { release(key, flight: flight, waiter: waiter) }
        return try await withTaskCancellationHandler {
            do {
                let data = try await flight.task.value
                try Task.checkCancellation()
                remember(data, key: key)
                return data
            } catch {
                failure(error, quota: quota)
                throw error
            }
        } onCancel: {
            Task { @MainActor in self.release(key, flight: flight, waiter: waiter) }
        }
    }
    private func release(_ key: String, flight: Flight, waiter: UUID) {
        flight.waiters.remove(waiter)
        guard flight.waiters.isEmpty else { return }
        flight.task.cancel()
        if flights[key]?.id == flight.id { flights[key] = nil }
    }
}
