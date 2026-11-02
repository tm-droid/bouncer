import Foundation

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