import Foundation

/// Circuit breaker for Appwrite's plan quotas. A 402 latches the matching quota in UserDefaults (so a
/// relaunch does not hammer a spent budget); one probe is allowed every `probeInterval`. A 429 only
/// sets a short in-memory pause and never latches.
final class QuotaBreaker: @unchecked Sendable {
    static let shared = QuotaBreaker()
    static let defaultProbeInterval: TimeInterval = 6 * 3600
    static let defaultRatePause: TimeInterval = 10
    static let maxRatePause: TimeInterval = 60

    private let defaults: UserDefaults
    private let probeInterval: TimeInterval
    private let clock: @Sendable () -> Date
    private let lock = NSLock()
    private var pausedUntil: Date?
    /// A probe handed out but not yet resolved, so concurrent callers do not all probe.
    private var probeOutstandingUntil: [Quota: Date] = [:]

    init(defaults: UserDefaults = .standard,
         probeInterval: TimeInterval = QuotaBreaker.defaultProbeInterval,
         clock: @escaping @Sendable () -> Date = { Date() }) {
        self.defaults = defaults
        self.probeInterval = probeInterval
        self.clock = clock
    }

    private func key(_ q: Quota) -> String { "appwrite.breaker.\(q.rawValue)" }

    /// When this quota was latched, nil if closed.
    func latchedAt(_ q: Quota) -> Date? {
        let t = defaults.double(forKey: key(q))
        return t > 0 ? Date(timeIntervalSince1970: t) : nil
    }

    /// nil when the request may go ahead; otherwise the reason it may not.
    func check(_ q: Quota) -> AppwriteError? {
        lock.lock(); defer { lock.unlock() }
        let now = clock()
        if let until = pausedUntil, now < until {
            return .rateLimited(retryAfter: until.timeIntervalSince(now))
        }
        guard let latched = latchedAt(q) else { return nil }
        if now.timeIntervalSince(latched) >= probeInterval {
            if let out = probeOutstandingUntil[q], now < out { return .breakerOpen(q) }
            probeOutstandingUntil[q] = now.addingTimeInterval(30)
            return nil
        }
        return .breakerOpen(q)
    }

    func recordSuccess(_ q: Quota) {
        lock.lock(); defer { lock.unlock() }
        probeOutstandingUntil[q] = nil
        if latchedAt(q) != nil { defaults.removeObject(forKey: key(q)) }
    }

    func recordQuotaExceeded(_ q: Quota) {
        lock.lock(); defer { lock.unlock() }
        probeOutstandingUntil[q] = nil
        defaults.set(clock().timeIntervalSince1970, forKey: key(q))
    }

    func recordRateLimited(retryAfter: TimeInterval?) {
        lock.lock(); defer { lock.unlock() }
        let pause = min(max(retryAfter ?? Self.defaultRatePause, 1), Self.maxRatePause)
        pausedUntil = clock().addingTimeInterval(pause)
    }

    func reset() {
        lock.lock(); defer { lock.unlock() }
        pausedUntil = nil
        probeOutstandingUntil = [:]
        Quota.allCases.forEach { defaults.removeObject(forKey: key($0)) }
    }
}
