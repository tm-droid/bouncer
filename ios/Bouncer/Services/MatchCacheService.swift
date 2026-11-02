import Foundation

actor MatchCacheService {
    static let shared = MatchCacheService()
    
    private var cache: [String: (matches: [CricketMatch], timestamp: Date)] = [:]
    
    // Different cache expiration intervals for different match types
    private func getCacheExpirationInterval(for status: String) -> TimeInterval {
        switch status {
        case "Live":
            return 6 // Live matches cache expires after 6 seconds
        case "Upcoming", "Recent":
            return 60 // Upcoming and Recent matches cache expires after 60 seconds
        default:
            return 30 // Default fallback
        }
    }
    
    private init() {}
    
    func getMatches(for status: String) async -> [CricketMatch]? {
        if let cached = cache[status],
           Date().timeIntervalSince(cached.timestamp) < getCacheExpirationInterval(for: status) {
            print("MatchCacheService: Using cached data for \(status) (valid for \(getCacheExpirationInterval(for: status))s)")
            return cached.matches
        }
        print("MatchCacheService: Cache expired or missing for \(status)")
        return nil
    }
    
    func updateCache(matches: [CricketMatch], for status: String) async {
        print("MatchCacheService: Updating cache for \(status) with \(matches.count) matches (valid for \(getCacheExpirationInterval(for: status))s)")
        cache[status] = (matches: matches, timestamp: Date())
    }
    
    func clearCache() async {
        print("MatchCacheService: Clearing all cached data")
        cache.removeAll()
    }
    
    func isCacheValid(for status: String) async -> Bool {
        if let cached = cache[status] {
            return Date().timeIntervalSince(cached.timestamp) < getCacheExpirationInterval(for: status)
        }
        return false
    }
} 

// AI Commentary Cache Service
actor AICacheService {
    static let shared = AICacheService()
    
    private var cache: [String: (commentary: String, timestamp: Date)] = [:]
    
    // AI commentary is less volatile, so cache for 1 hour by default
    private let cacheExpirationInterval: TimeInterval = 3600 // 1 hour
    
    private init() {}
    
    func getCommentary(for key: String) async -> String? {
        if let cached = cache[key], Date().timeIntervalSince(cached.timestamp) < cacheExpirationInterval {
            print("AICacheService: Using cached commentary for key \(key) (valid for \(cacheExpirationInterval)s)")
            return cached.commentary
        }
        print("AICacheService: Cache expired or missing for key \(key)")
        return nil
    }
    
    func setCommentary(_ commentary: String, for key: String) async {
        print("AICacheService: Updating cache for key \(key)")
        cache[key] = (commentary: commentary, timestamp: Date())
    }
    
    func clearCache() async {
        print("AICacheService: Clearing all cached AI commentary")
        cache.removeAll()
    }
    
    func isCacheValid(for key: String) async -> Bool {
        if let cached = cache[key] {
            return Date().timeIntervalSince(cached.timestamp) < cacheExpirationInterval
        }
        return false
    }
} 