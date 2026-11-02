import SwiftUI

struct StatusChipView: View {
    let chip: String
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        ZStack {
            Capsule()
                .fill(
                    isSelected 
                    ? LinearGradient(
                        gradient: Gradient(colors: [Color.blue.opacity(0.7), Color.purple.opacity(0.7)]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    : LinearGradient(
                        gradient: Gradient(colors: [Color.gray.opacity(0.18), Color.gray.opacity(0.18)]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(height: 40)
                .shadow(color: isSelected ? Color.blue.opacity(0.4) : .clear, radius: 8, y: 2)
            Text(chip)
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(isSelected ? .white : .gray)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
        }
        .clipShape(Capsule())
        .onTapGesture(perform: onTap)
    }
}

struct CategoryChipView: View {
    let chip: String
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        ZStack {
            Capsule()
                .fill(
                    isSelected 
                    ? LinearGradient(
                        gradient: Gradient(colors: [Color.blue.opacity(0.6), Color.green.opacity(0.6)]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    : LinearGradient(
                        gradient: Gradient(colors: [Color.gray.opacity(0.13), Color.gray.opacity(0.13)]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(height: 38)
                .shadow(color: isSelected ? Color.blue.opacity(0.3) : .clear, radius: 7, y: 2)
            HStack(spacing: 7) {
                AnimatedCheckmark(isChecked: isSelected)
                Text(chip)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(isSelected ? .white : .gray)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .clipShape(Capsule())
        .onTapGesture(perform: onTap)
    }
}

struct ContentView: View {
    @EnvironmentObject private var liveActivityManager: LiveActivityManager
    @State private var selectedCategories: Set<String> = ["International", "League", "Domestic", "Women"]
    private let winProbabilityCalculator = WinProbabilityCalculator()
    
    let statusChips = ["Live", "Upcoming", "Recent"]
    let categoryChips = ["International", "League", "Domestic", "Women"]
    
    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()
                VStack(spacing: 18) {
                    // Status chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(statusChips, id: \.self) { chip in
                                StatusChipView(
                                    chip: chip,
                                    isSelected: liveActivityManager.selectedStatus == chip,
                                    onTap: {
                                        Task {
                                            await liveActivityManager.setAndFetchMatches(for: chip)
                                        }
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    }
                                )
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 10)
                    }
                    .padding(.bottom, 10)
                    // Category toggles
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(categoryChips, id: \.self) { chip in
                                CategoryChipView(
                                    chip: chip,
                                    isSelected: selectedCategories.contains(chip),
                                    onTap: {
                                        withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) {
                                            if selectedCategories.contains(chip) {
                                                selectedCategories.remove(chip)
                                            } else {
                                                selectedCategories.insert(chip)
                                            }
                                        }
                                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    }
                                )
                            }
                        }
                        .padding(.horizontal)
                    }
                    .padding(.bottom, 18)
                    // Match list
                    if liveActivityManager.currentMatches.isEmpty {
                        Spacer()
                        Text("No matches available.")
                            .multilineTextAlignment(.center)
                            .foregroundColor(.gray)
                            .font(.title3)
                        Spacer()
                    } else if !categoryFilteredMatches.isEmpty {
                        List {
                            ForEach(categoryFilteredMatches) { match in
                                NavigationLink(destination: MatchDetailView(match: match)) {
                                    GlassyMatchView(match: match)
                                }
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                            }
                        }
                        .listStyle(.plain)
                        .background(Color.black)
                    } else {
                         Spacer()
                         Text("No matches found for selected filters.")
                             .foregroundColor(.gray)
                             .font(.title3)
                         Spacer()
                    }
                }
            }
            .navigationTitle("Live Matches")
            .navigationBarTitleDisplayMode(.large)
        }
        .preferredColorScheme(.dark)
        .onAppear { Task { await liveActivityManager.setAndFetchMatches(for: liveActivityManager.selectedStatus) } }
        .onDisappear { liveActivityManager.stopPolling() }
    }

    var categoryFilteredMatches: [CricketMatch] {
        let allCategoriesSelected = selectedCategories.count == categoryChips.count
        return allCategoriesSelected ? liveActivityManager.currentMatches : liveActivityManager.currentMatches.filter { match in
            selectedCategories.contains(match.category ?? "International")
        }
    }

    private func calculateWinProbability(for match: CricketMatch) -> Double? {
        // Only calculate win probability for live matches
        // The filtering by status is now handled by LiveActivityManager fetching from appropriate API
        guard liveActivityManager.selectedStatus == "Live" else {
            return nil
        }
        // Get the batting team
        guard let battingTeam = match.teams.first(where: { $0.isBatting }) else {
            return nil
        }
        // Parse the score string to get runs and wickets
        let scoreParts = battingTeam.score.components(separatedBy: "-")
        guard scoreParts.count == 2,
              let runsNeeded = Int(scoreParts[0]),
              let wicketsLost = Int(scoreParts[1]) else {
            return nil
        }
        // Extract overs from the match status or score
        let oversBowled: Int
        if let score = match.score {
            // Try to extract overs from currentInnings
            let components = score.currentInnings.components(separatedBy: " ")
            if let overStr = components.first(where: { $0.contains(".") }),
               let over = Double(overStr) {
                oversBowled = Int(over * 6) // Convert overs to balls
            } else {
                return nil
            }
        } else {
            return nil
        }
        return winProbabilityCalculator.calculateWinProbability(
            runsNeeded: runsNeeded,
            ballsBowled: oversBowled,
            wicketsLost: wicketsLost
        )
    }
}

struct AnimatedCheckmark: View {
    var isChecked: Bool
    @State private var animate = false
    var body: some View {
        ZStack {
            Circle()
                .strokeBorder(isChecked ? Color.green : Color.gray.opacity(0.3), lineWidth: 2)
                .frame(width: 20, height: 20)
                .background(Circle().fill(isChecked ? Color.green.opacity(0.7) : Color.clear))
            if isChecked {
                Image(systemName: "checkmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                    .scaleEffect(animate ? 1.2 : 0.8)
                    .opacity(animate ? 1 : 0.7)
                    .onAppear {
                        withAnimation(.spring(response: 0.4, dampingFraction: 0.5)) {
                            animate = true
                        }
                    }
            }
        }
    }
}