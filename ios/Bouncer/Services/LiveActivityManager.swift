import Foundation
import SwiftUI
import ActivityKit

enum LoadingState: Equatable {
    case idle
    case refreshing
    case error(String)
}

@MainActor
internal class LiveActivityManager: ObservableObject {
    @Published private(set) var currentMatches: [CricketMatch] = []
    @Published private(set) var loadingState: LoadingState = .idle
    @Published private(set) var activeActivities: Set<String> = []
    @Published private(set) var currentURL: String?
    @Published var selectedStatus: String = "Live"
    // Latest fused AI commentary per match id; UI can observe this map to present the most recent AI commentary
    @Published private(set) var aiCommentary: [String: String] = [:]
    
    private var currentTask: Task<Void, Never>?
    private let apiService: CricketAPIService
    private let cricBuzzScraper: CricBuzzScraper
    private let cacheService = MatchCacheService.shared
    private var lastFetchTime: [String: Date] = [:] // Track last fetch time for each status
    private var cachedMatches: [String: [CricketMatch]] = [:] // Cache matches for each status
    private var pollingTasks: [String: Task<Void, Never>] = [:]
    private var isPollingActive = false
    private let liveCommentaryManager = LiveCommentaryManager()
    private var matchViewModels: [String: MatchViewModel] = [:]
    
    private func getPollingInterval(for status: String) -> TimeInterval {
        switch status {
        case "Live":
            return 6.0 // Poll every 6 seconds for live matches
        case "Upcoming", "Recent":
            return 60.0 // Poll every 60 seconds for upcoming and recent matches
        default:
            return 30.0 // Default fallback
        }
    }
    
    private enum EventType {
        case wicket
        case six
    }
    
    init(apiService: CricketAPIService = CricketAPIService(apiKey: "REDACTED_ROTATE_ME"),
         cricBuzzScraper: CricBuzzScraper = CricBuzzScraper()) {
        self.apiService = apiService
        self.cricBuzzScraper = cricBuzzScraper
    }
    
    private func setLoadingState(_ state: LoadingState) {
        loadingState = state
    }
    
    func startPolling() {
        print("LiveActivityManager: Starting poll for updates")
        isPollingActive = true
        
        // Cancel only non-live polling tasks
        for (status, task) in pollingTasks {
            if status != "Live" {
                print("LiveActivityManager: Cancelling polling task for \(status)")
                task.cancel()
                pollingTasks.removeValue(forKey: status)
            }
        }
        
        // Start polling for live matches if not already running
        if pollingTasks["Live"] == nil {
            startPollingForStatus("Live")
        }
        
        // Start polling for the selected status if not live
        if selectedStatus != "Live" && pollingTasks[selectedStatus] == nil {
            startPollingForStatus(selectedStatus)
        }
        
        // Initial data fetch for all statuses
        Task {
            for status in ["Live", "Upcoming", "Recent"] {
                do {
                    try await updateMatches(for: status)
                } catch {
                    print("LiveActivityManager: Initial fetch error for \(status): \(error)")
                    setLoadingState(.error(error.localizedDescription))
                }
            }
        }
    }
    
    func stopPolling() {
        print("LiveActivityManager: Stopping all polling tasks")
        isPollingActive = false
        for (status, task) in pollingTasks {
            print("LiveActivityManager: Cancelling polling task for \(status)")
            task.cancel()
        }
        pollingTasks.removeAll()
    }
    
    private func startPollingForStatus(_ status: String) {
        print("LiveActivityManager: Starting polling task for \(status)")
        let task = Task { [weak self] in
            guard let self = self else { return }
            
            while !Task.isCancelled && self.isPollingActive {
                do {
                    let interval = self.getPollingInterval(for: status)
                    print("LiveActivityManager: Waiting \(interval) seconds before next update for \(status)")
                    try await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
                    
                    if Task.isCancelled || !self.isPollingActive {
                        print("LiveActivityManager: Polling task cancelled for \(status)")
                        break
                    }
                    
                    // Always fetch new data for live matches, check cache for others
                    if status == "Live" {
                        print("LiveActivityManager: Fetching new data for Live matches")
                        async let scores: Void = try self.updateMatches(for: status)
                        async let commentary: Void = self.updateAllLiveCommentaries()
                        do {
                            _ = try await (scores, commentary)
                        } catch {
                            print("LiveActivityManager: Error in polling loop for \(status): \(error)")
                        }
                    } else if !(await self.cacheService.isCacheValid(for: status)) {
                        print("LiveActivityManager: Cache expired, fetching new data for \(status)")
                        try await self.updateMatches(for: status)
                    } else {
                        print("LiveActivityManager: Cache still valid for \(status)")
                    }
                } catch {
                    print("LiveActivityManager: Error in polling loop for \(status): \(error)")
                }
            }
        }
        pollingTasks[status] = task
    }
    
    /// Fetch and update commentary for all live matches
    private func updateAllLiveCommentaries() async {
        // For each current live match, fetch and fuse commentary
        for match in currentMatches {
            let cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/\(match.id)"
            let cricinfoUrl = "https://www.espncricinfo.com/series/\(match.id)/commentary"
            let keyNames = match.teams.map { $0.name }
            let commentary = await liveCommentaryManager.getBestCommentary(
                cricbuzzUrl: cricbuzzUrl,
                cricinfoUrl: cricinfoUrl,
                keyNames: keyNames,
                recentBalls: match.score?.recentBalls?.components(separatedBy: " ")
            )
            // Store latest AI commentary for the match so UI can present it
            aiCommentary[match.id] = commentary
            print("[AI Commentary] \(match.id): \(commentary)")
        }
    }
    
    func setAndFetchMatches(for statusType: String) async {
        print("LiveActivityManager: Setting status to \(statusType)")
        selectedStatus = statusType
        
        // Start polling for the new status if not already running (except for Live, which is always running)
        if statusType != "Live" && pollingTasks[statusType] == nil {
            startPollingForStatus(statusType)
        }
        
        // Always fetch fresh data for live matches
        if statusType == "Live" {
            do {
                try await updateMatches(for: statusType)
            } catch {
                print("LiveActivityManager: Error fetching live matches: \(error)")
                setLoadingState(.error(error.localizedDescription))
            }
            return
        }
        
        // For other statuses, try cache first
        if let cachedData = await cacheService.getMatches(for: statusType) {
            print("LiveActivityManager: Using cached data for \(statusType)")
            await updateMatches(cachedData)
        } else {
            do {
                try await updateMatches(for: statusType)
            } catch {
                print("LiveActivityManager: Error fetching matches for \(statusType): \(error)")
                setLoadingState(.error(error.localizedDescription))
            }
        }
    }
    
    private func updateMatches(for statusType: String) async throws {
        print("LiveActivityManager: Fetching matches for status: \(statusType)")
        
        // First, try to get cached data
        if let cachedMatches = await cacheService.getMatches(for: statusType) {
            print("LiveActivityManager: Using cached data for \(statusType)")
            if statusType == selectedStatus {
                await updateMatches(cachedMatches)
            }
            return
        }
        
        // If no valid cache, fetch from API
        setLoadingState(.refreshing)
        let startTime = Date()
        
        do {
            let newMatches = try await apiService.fetchMatches(for: statusType)
            let fetchDuration = Date().timeIntervalSince(startTime)
            print("LiveActivityManager: API call for \(statusType) took \(String(format: "%.2f", fetchDuration)) seconds")
            
            // Update the shared cache
            await cacheService.updateCache(matches: newMatches, for: statusType)
            
            print("LiveActivityManager: Successfully fetched \(newMatches.count) matches for \(statusType)")
            
            if Task.isCancelled {
                print("LiveActivityManager: Update cancelled for \(statusType)")
                return
            }
            
            // Only update UI if this is the currently selected status
            if statusType == selectedStatus {
                await updateMatches(newMatches)
            }
            
            // Create live activities for live matches and update ball-by-ball info
            if statusType == "Live" {
                for match in newMatches {
                    if Task.isCancelled { break }
                    
                    // Create activity if it doesn't exist
                    let hasActiveActivity = Activity<CricketMatchAttributes>.activities.contains { $0.attributes.matchId == match.id }
                    if !hasActiveActivity {
                        await addActivity(for: match)
                    }
                    
                    // Update ball-by-ball info for active matches
                    if hasActiveActivity {
                    await updateMatchWithBallByBall(match: match)
                    }
                }
            }
        } catch {
            if Task.isCancelled {
                print("LiveActivityManager: Fetch cancelled for \(statusType)")
                return
            }
            print("LiveActivityManager: Error fetching matches: \(error)")
            throw error
        }
    }
    
    private func hasMatchChanges(_ newMatches: [CricketMatch]) -> Bool {
        // If the number of matches is different, there's definitely a change
        if newMatches.count != currentMatches.count {
            return true
        }
        
        // Create a dictionary of current matches for quick lookup
        let currentMatchesDict = Dictionary(uniqueKeysWithValues: 
            currentMatches.map { ($0.id, $0) })
        
        // Check each new match against its current version
        for newMatch in newMatches {
            guard let currentMatch = currentMatchesDict[newMatch.id] else {
                return true // New match found
            }
            
            // Compare relevant fields that indicate a change
            if newMatch.status != currentMatch.status ||
               newMatch.teams[0].score != currentMatch.teams[0].score ||
               newMatch.teams[1].score != currentMatch.teams[1].score ||
               newMatch.score?.currentInnings != currentMatch.score?.currentInnings {
                return true
            }
        }
        
        return false
    }
    
    func addActivity(for match: CricketMatch) async {
        print("LiveActivityManager: Adding live activity for match \(match.id)")
        
        // Fetch fresh ball-by-ball data
        var recentBalls: [String] = []
        do {
            recentBalls = try await cricBuzzScraper.fetchRecentBallsFromScorecard(matchId: match.id)
            print("LiveActivityManager: Fetched recent balls for new activity: \(recentBalls)")
        } catch {
            print("LiveActivityManager: Error fetching recent balls for new activity: \(error)")
            // Fallback to existing data
            recentBalls = match.score?.recentBalls?.components(separatedBy: " ") ?? []
        }
        
        let attributes = CricketMatchAttributes(
            matchId: match.id,
            team1Name: match.teams[0].name,
            team2Name: match.teams[1].name,
            matchType: match.matchType
        )
        
        let contentState = CricketMatchAttributes.ContentState(
            matchId: match.id,
            team1Score: match.teams[0].score,
            team2Score: match.teams[1].score,
            currentInnings: match.score?.currentInnings ?? "",
            lastUpdated: Date(),
            alertType: nil,
            batsmen: match.score?.batsmen.map { batsman in
                BatsmanWidget(name: batsman.name, runs: String(batsman.runs), balls: String(batsman.balls), isStriker: false)
            } ?? [],
            bowler: match.score?.bowlers.first.map { bowler in
                BowlerWidget(name: bowler.name, overs: String(format: "%.1f", bowler.overs), runs: String(bowler.runs), wickets: String(bowler.wickets), economy: String(format: "%.2f", bowler.economy))
            },
            recentBalls: recentBalls
        )
        
        let activityContent = ActivityContent(state: contentState, staleDate: Calendar.current.date(byAdding: .minute, value: 30, to: Date())!)
        
        do {
            let activity = try Activity.request(attributes: attributes, content: activityContent)
            print("LiveActivityManager: Successfully added live activity: \(activity.id)")
            activeActivities.insert(match.id) // Use match.id instead of activity.id
        } catch {
            print("LiveActivityManager: Error adding live activity: \(error.localizedDescription)")
        }
    }
    
    func updateActivity(for match: CricketMatch) async {
        print("LiveActivityManager: Updating activity for match \(match.id)")
        
        // Check if we have an active activity for this match
        let hasActiveActivity = Activity<CricketMatchAttributes>.activities.contains { $0.attributes.matchId == match.id }
        guard hasActiveActivity else {
            print("LiveActivityManager: No active activity found for match \(match.id)")
            return
        }

        // Fetch fresh ball-by-ball data
        var recentBalls: [String] = []
        do {
            recentBalls = try await cricBuzzScraper.fetchRecentBallsFromScorecard(matchId: match.id)
            print("LiveActivityManager: Updated recent balls for activity: \(recentBalls)")
        } catch {
            print("LiveActivityManager: Error fetching recent balls for activity: \(error)")
            // Fallback to existing data
            recentBalls = match.score?.recentBalls?.components(separatedBy: " ") ?? []
        }

        let contentState = CricketMatchAttributes.ContentState(
            matchId: match.id,
            team1Score: match.teams[0].score,
            team2Score: match.teams[1].score,
            currentInnings: match.score?.currentInnings ?? "",
            lastUpdated: Date(),
            alertType: nil,
            batsmen: match.score?.batsmen.map { batsman in
                BatsmanWidget(name: batsman.name, runs: String(batsman.runs), balls: String(batsman.balls), isStriker: false)
            } ?? [],
            bowler: match.score?.bowlers.first.map { bowler in
                BowlerWidget(name: bowler.name, overs: String(format: "%.1f", bowler.overs), runs: String(bowler.runs), wickets: String(bowler.wickets), economy: String(format: "%.2f", bowler.economy))
            },
            recentBalls: recentBalls
        )

        for activity in Activity<CricketMatchAttributes>.activities {
            if activity.attributes.matchId == match.id {
                await activity.update(ActivityContent(state: contentState, staleDate: Calendar.current.date(byAdding: .minute, value: 30, to: Date())!))
                print("LiveActivityManager: Successfully updated activity for match \(match.id)")
                break
            }
        }
    }
    
    func endActivity(for matchId: String) async {
        print("LiveActivityManager: Ending activity for match \(matchId)")
        for activity in Activity<CricketMatchAttributes>.activities {
            if activity.attributes.matchId == matchId {
                let contentState = CricketMatchAttributes.ContentState(
                    matchId: matchId,
                    team1Score: "",
                    team2Score: "",
                    currentInnings: "",
                    lastUpdated: Date(),
                    alertType: nil,
                    batsmen: [],
                    bowler: nil,
                    recentBalls: []
                )
                let activityContent = ActivityContent(state: contentState, staleDate: Calendar.current.date(byAdding: .minute, value: 30, to: Date())!)
                await activity.end(activityContent, dismissalPolicy: .immediate)
                activeActivities.remove(matchId)
                print("LiveActivityManager: Successfully ended live activity: \(matchId)")
                break
            }
        }
    }

    func updateMatchDetails(for match: CricketMatch) async {
        guard let activity = Activity<CricketMatchAttributes>.activities.first(where: { $0.attributes.matchId == match.id }) else {
            return
        }

        let batsmenWidgets = match.score?.batsmen.map { batsman in
            BatsmanWidget(name: batsman.name, runs: String(batsman.runs), balls: String(batsman.balls), isStriker: false)
        } ?? []

        let bowlerWidget = match.score?.bowlers.first.map { bowler in
            BowlerWidget(name: bowler.name, overs: String(format: "%.1f", bowler.overs), runs: String(bowler.runs), wickets: String(bowler.wickets), economy: String(format: "%.2f", bowler.economy))
        }

        let contentState = CricketMatchAttributes.ContentState(
            matchId: match.id,
            team1Score: match.teams[0].score,
            team2Score: match.teams[1].score,
            currentInnings: match.score?.currentInnings ?? "",
            lastUpdated: Date(),
            alertType: nil,
            batsmen: batsmenWidgets,
            bowler: bowlerWidget,
            recentBalls: match.score?.recentBalls?.components(separatedBy: " ") ?? []
        )

        let activityContent = ActivityContent(state: contentState, staleDate: Calendar.current.date(byAdding: .minute, value: 30, to: Date())!)
        await activity.update(activityContent)
    }

    private func convertUrlToMatchId(_ url: URL) -> String {
        return url.lastPathComponent
    }

    private func convertMatchIdToUrl(_ matchId: String) -> URL {
        return URL(string: "https://example.com/matches/\(matchId)")!
    }

    private func updateMatches(_ newMatches: [CricketMatch]) async {
        await MainActor.run {
            self.currentMatches = newMatches
        }
    }
    
    private func updateMatchWithBallByBall(match: CricketMatch) async {
        print("LiveActivityManager: Updating ball by ball for match \(match.id)")
        
        do {
            // Use the new method to fetch recent balls from scorecard
            let recentBalls = try await cricBuzzScraper.fetchRecentBallsFromScorecard(matchId: match.id)
            
            // Convert recentBalls to BallEvent array
            let ballEvents: [BallEvent] = recentBalls.enumerated().map { (idx, ballString) in
                let eventType: ProbabilityEvent.EventType
                let desc: String
                switch ballString {
                    case "W": eventType = .wicket; desc = "Wicket!"
                    case "4", "6": eventType = .boundary; desc = "Boundary! \(ballString) runs."
                    case "0": eventType = .dotBall; desc = "Dot ball."
                    case "WD": eventType = .other; desc = "Wide ball."
                    case "NB": eventType = .other; desc = "No ball."
                    case "LB": eventType = .other; desc = "Leg bye."
                    case "B": eventType = .other; desc = "Bye."
                    default: eventType = .other; desc = "\(ballString) run(s)."
                }
                return BallEvent(number: idx, over: idx / 6, eventType: eventType, description: desc)
            }
            
            print("LiveActivityManager: Extracted ball events: \(ballEvents.map { $0.description })")
            
            // Update the probability graph for the correct match
            if let matchViewModel = matchViewModels[match.id] {
                await MainActor.run {
                    matchViewModel.updateWithNewBalls(newBalls: ballEvents)
                }
            }
            
            // Update live activity with new ball data
            let hasActiveActivity = Activity<CricketMatchAttributes>.activities.contains { $0.attributes.matchId == match.id }
            if hasActiveActivity {
                await updateActivity(for: match)
            }
            
            // --- Commentary integration ---
            let cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/\(match.id)"
            let cricinfoUrl = "https://www.espncricinfo.com/series/\(match.id)/commentary"
            let keyNames = match.teams.map { $0.name }
            let bestCommentary = await liveCommentaryManager.getBestCommentary(cricbuzzUrl: cricbuzzUrl, cricinfoUrl: cricinfoUrl, keyNames: keyNames)
            print("[AI Commentary] \(bestCommentary)")
            
            // Update live activity with new commentary
            if hasActiveActivity {
                await updateActivity(for: match)
            }
            
        } catch {
            print("LiveActivityManager: Error scraping ball by ball data for match \(match.id): \(error.localizedDescription)")
        }
    }

    func removeActivity(for match: CricketMatch) async {
        print("LiveActivityManager: Removing activity for match \(match.id)")
        guard let activity = Activity<CricketMatchAttributes>.activities.first(where: { $0.id == match.id }) else {
            print("LiveActivityManager: No active activity found to remove for match \(match.id)")
            return
        }
        
        let contentState = CricketMatchAttributes.ContentState(
            matchId: match.id,
            team1Score: match.teams[0].score,
            team2Score: match.teams[1].score,
            currentInnings: match.score?.currentInnings ?? "",
            lastUpdated: Date(),
            alertType: nil,
            batsmen: match.score?.batsmen.map { batsman in
                BatsmanWidget(name: batsman.name, runs: String(batsman.runs), balls: String(batsman.balls), isStriker: false)
            } ?? [],
            bowler: match.score?.bowlers.first.map { bowler in
                BowlerWidget(name: bowler.name, overs: String(format: "%.1f", bowler.overs), runs: String(bowler.runs), wickets: String(bowler.wickets), economy: String(format: "%.2f", bowler.economy))
            },
            recentBalls: match.score?.recentBalls?.components(separatedBy: " ") ?? []
        )

        await activity.end(ActivityContent(state: contentState, staleDate: Calendar.current.date(byAdding: .minute, value: 30, to: Date())!), dismissalPolicy: .immediate)
        activeActivities.remove(match.id)
        print("LiveActivityManager: Successfully removed live activity: \(activity.id)")
    }

    /// Register a MatchViewModel for a match ID
    func registerMatchViewModel(_ viewModel: MatchViewModel, for matchId: String) {
        matchViewModels[matchId] = viewModel
    }
}
