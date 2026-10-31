import SwiftUI
import Charts

struct ProbabilityGraphView: View {
    let events: [ProbabilityEvent]
    @State private var selectedEvent: ProbabilityEvent?
    @State private var scale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var isShowingPlayerImpact = false
    @State private var isShowingPartnershipAnalysis = false
    @State private var isShowingBowlingImpact = false
    @State private var isShowingRunRateAnalysis = false
    @State private var animationProgress: CGFloat = 0
    @State private var particleEffects: [ParticleEffect] = []
    @State private var currentEvents: [ProbabilityEvent] = []
    @State private var timer: Timer?
    
    // Team names and colors passed in as parameters
    let team1Name: String
    let team2Name: String
    let team1Color: Color
    let team2Color: Color
    
    // Ideally, this should come from match data
    let firstInningsEndBall: Int

    // New parameters for match details
    let currentMatchScore: String
    let latestEventDescription: String
    let matchResult: String
    
    var body: some View {
        VStack(spacing: 0) { // Remove default spacing between VStack elements
            // Top Legend
            HStack {
                Text("Win Probability")
                    .font(.headline)
                    .foregroundColor(.white)
                
                Spacer()
                
                Circle()
                    .fill(team1Color)
                    .frame(width: 10, height: 10)
                Text("\(team1Name) \(String(format: "%.1f", currentEvents.last?.probability ?? 0.0))%")
                    .foregroundColor(.white)
                
                Circle()
                    .fill(team2Color)
                    .frame(width: 10, height: 10)
                Text("\(team2Name) \(String(format: "%.1f", 100.0 - (currentEvents.last?.probability ?? 0.0)))%") // Assuming GT is 100-RR
                    .foregroundColor(.white)
            }
            .padding()
            .background(Color.black) // Dark background for the legend area
            
            // Interactive Probability Graph
            ZStack {
                Chart {
                    // Line for Team 1 (e.g., RR - Orange)
                    ForEach(currentEvents, id: \.timestamp) { event in
                        LineMark(
                            x: .value("Ball", event.ball),
                            y: .value("Probability", event.probability)
                        )
                        .foregroundStyle(team1Color)
                        .interpolationMethod(.catmullRom) // Smooth lines
                        .opacity(animationProgress)
                    }

                    // Line for Team 2 (e.g., GT - Blue)
                    ForEach(currentEvents, id: \.timestamp) { event in
                        LineMark(
                            x: .value("Ball", event.ball),
                            y: .value("Probability", 100.0 - event.probability) // Assuming GT is 100-RR
                        )
                        .foregroundStyle(team2Color)
                        .interpolationMethod(.catmullRom) // Smooth lines
                        .opacity(animationProgress)
                    }
                    
                    // Event Markers (Wicket 'W')
                    ForEach(currentEvents.filter { $0.eventType == .wicket }, id: \.timestamp) { event in
                        PointMark(
                            x: .value("Ball", event.ball),
                            y: .value("Probability", event.probability)
                        )
                        .symbol {
                            Image(systemName: "w.circle.fill") // Using SF Symbol for 'W'
                                .font(.caption)
                                .foregroundColor(.red)
                                .background(Circle().fill(.white).frame(width: 20, height: 20)) // Larger circle for visibility
                        }
                    }
                }
                .frame(height: 200)
                .chartXAxis {
                    AxisMarks(values: [0, Double(firstInningsEndBall), 240]) { value in // Explicitly mark 0, innings end, and end of match (240 balls for T20)
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [2])) // Dashed grid lines
                        AxisValueLabel {
                            if let ball = value.as(Int.self) {
                                if ball == 0 {
                                    Text("Start")
                                        .foregroundColor(.white)
                                } else if ball == firstInningsEndBall {
                                    Text("\(team1Name) Innings")
                                        .foregroundColor(.white)
                                } else if ball == 240 {
                                    Text("\(team2Name) Innings") // End of 2nd innings / match
                                        .foregroundColor(.white)
                                } else {
                                    // Only show labels for specific markers or if we want more detail
                                    EmptyView() // Hide other labels for cleaner look
                                }
                            }
                        }
                    }
                }
                .chartYAxis {
                    AxisMarks(values: [0, 50, 100]) { value in // Only 0%, 50%, 100%
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [2]))
                        AxisValueLabel {
                            if let prob = value.as(Double.self) {
                                Text("\(Int(prob))%")
                                    .foregroundColor(.white)
                            }
                        }
                    }
                }
                .chartYScale(domain: 0...100)
                .background(Color.black.opacity(0.8)) // Dark background for chart
                .scaleEffect(scale)
                .offset(offset)
                // Removed gestures to prevent dragging and zooming
                // .gesture(
                //     MagnificationGesture()
                //         .onChanged { value in
                //             scale = value.magnitude
                //         }
                // )
                // .gesture(
                //     DragGesture()
                //         .onChanged { value in
                //             offset = CGSize(
                //                 width: lastOffset.width + value.translation.width,
                //                 height: lastOffset.height + value.translation.height
                //             )
                //         }
                //         .onEnded { value in
                //             lastOffset = offset
                //         }
                // )
                
                // Particle Effects (Keep for now, but consider if still needed for this UI)
                ForEach(particleEffects) { effect in
                    ParticleEffectView(effect: effect)
                }
            }
            .padding(.horizontal, 0) // Remove horizontal padding for chart
            
            // Bottom Match Details and Event Details
            VStack(alignment: .leading, spacing: 4) {
                Text(currentMatchScore) // Use dynamic current match score
                    .font(.subheadline)
                    .foregroundColor(.white)
                
                HStack {
                    Text(latestEventDescription) // Use dynamic latest event description
                        .font(.caption)
                        .foregroundColor(.white)
                    Spacer()
                    if latestEventDescription.contains("Wicket") { // Only show W if it's a wicket
                        Image(systemName: "w.circle.fill") // Using SF Symbol for 'W'
                            .font(.caption)
                            .foregroundColor(.red)
                            .background(Circle().fill(.white).frame(width: 16, height: 16))
                    }
                }
                
                Text(matchResult) // Use dynamic match result
                    .font(.caption)
                    .foregroundColor(.white)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.black) // Dark background for bottom section

            // Analysis Toggle Buttons (keeping them below for now)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    AnalysisToggleButton(
                        title: "Player Impact",
                        isSelected: isShowingPlayerImpact,
                        action: { isShowingPlayerImpact.toggle() }
                    )
                    
                    AnalysisToggleButton(
                        title: "Partnerships",
                        isSelected: isShowingPartnershipAnalysis,
                        action: { isShowingPartnershipAnalysis.toggle() }
                    )
                    
                    AnalysisToggleButton(
                        title: "Bowling Impact",
                        isSelected: isShowingBowlingImpact,
                        action: { isShowingBowlingImpact.toggle() }
                    )
                    
                    AnalysisToggleButton(
                        title: "Run Rate",
                        isSelected: isShowingRunRateAnalysis,
                        action: { isShowingRunRateAnalysis.toggle() }
                    )
                }
                .padding(.vertical, 8)
            }
            .background(Color.black.opacity(0.8)) // Dark background for toggle buttons

            // Phase Analysis with Animations
            PhaseAnalysisView(events: currentEvents)
                .transition(.slide)
                .background(Color.black.opacity(0.8))
            
            // Additional Analysis Views
            Group { // Group to apply background to all
                if isShowingPlayerImpact {
                    PlayerImpactView(events: currentEvents)
                        .transition(.move(edge: .bottom))
                }
                
                if isShowingPartnershipAnalysis {
                    PartnershipAnalysisView(events: currentEvents)
                        .transition(.move(edge: .bottom))
                }
                
                if isShowingBowlingImpact {
                    BowlingImpactView(events: currentEvents)
                        .transition(.move(edge: .bottom))
                }
                
                if isShowingRunRateAnalysis {
                    RunRateAnalysisView(events: currentEvents)
                        .transition(.move(edge: .bottom))
                }
            }
            .background(Color.black.opacity(0.8)) // Dark background for analysis views
        }
        .background(Color.black) // Overall background for the entire view
        .onAppear {
            currentEvents = events
            withAnimation(.easeInOut(duration: 1.0)) {
                animationProgress = 1.0
            }
            startPolling()
        }
        .onDisappear {
            stopPolling()
        }
        .onChange(of: events) { _, newValue in
            withAnimation(.easeInOut(duration: 1.0)) {
                currentEvents = newValue
                animationProgress = 1.0
            }
        }
    }
    
    private func startPolling() {
        timer = Timer.scheduledTimer(withTimeInterval: 6.0, repeats: true) { _ in
            Task {
                await fetchLatestEvents()
            }
        }
    }
    
    private func stopPolling() {
        timer?.invalidate()
        timer = nil
    }
    
    private func fetchLatestEvents() async {
        // Minimal implementation: refresh the chart from the current `events` prop.
        // Later: replace with a network call to fetch events for the given match id.
        if !events.isEmpty {
            withAnimation(.easeInOut(duration: 0.5)) {
                currentEvents = events
                animationProgress = 1.0
            }
        }
        // If no events are provided, we keep currentEvents as-is and retry on next tick.
    }
}

struct ParticleEffect: Identifiable {
    let id = UUID()
    let position: CGPoint
    let type: EffectType
    let color: Color
    
    enum EffectType {
        case boundary
        case wicket
        case milestone
    }
}

struct ParticleEffectView: View {
    let effect: ParticleEffect
    @State private var scale: CGFloat = 0
    @State private var opacity: Double = 1
    
    var body: some View {
        Circle()
            .fill(effect.color)
            .frame(width: 8, height: 8)
            .scaleEffect(scale)
            .opacity(opacity)
            .position(effect.position)
            .onAppear {
                withAnimation(.easeOut(duration: 0.5)) {
                    scale = 2
                    opacity = 0
                }
            }
    }
}

struct AnalysisToggleButton: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.blue : Color.gray.opacity(0.2))
                .foregroundColor(isSelected ? .white : .primary)
                .cornerRadius(8)
        }
    }
}

struct EventDetailView: View {
    let event: ProbabilityEvent
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Ball \(event.ball/6).\(event.ball%6)")
                .font(.headline)
                .transition(.scale.combined(with: .opacity))
            Text(event.description)
                .font(.subheadline)
                .transition(.slide)
            Text("Win Probability: \(String(format: "%.1f", event.probability))%")
                .font(.subheadline)
                .transition(.move(edge: .trailing))
            Text("Impact: \(String(format: "%.1f", event.impact))%")
                .font(.subheadline)
                .foregroundColor(event.impact > 0 ? .green : .red)
                .transition(.scale.combined(with: .opacity))
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
        .animation(.spring(), value: event)
    }
}

struct BowlingImpactView: View {
    let events: [ProbabilityEvent]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Bowling Impact Analysis")
                .font(.headline)
            
            ForEach(getBowlingImpacts(), id: \.bowler) { impact in
                HStack {
                    Text(impact.bowler)
                        .font(.subheadline)
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("Impact: \(String(format: "%.1f", impact.impact))%")
                            .foregroundColor(impact.impact > 0 ? .green : .red)
                        Text("Wickets: \(impact.wickets)")
                            .font(.caption)
                        Text("Economy: \(String(format: "%.1f", impact.economy))")
                            .font(.caption)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
    }
    
    private func getBowlingImpacts() -> [(bowler: String, impact: Double, wickets: Int, economy: Double)] {
        // Group events by bowler and calculate impact
        let bowlerImpacts = Dictionary(grouping: events) { $0.description.components(separatedBy: " ").first ?? "" }
            .mapValues { events in
                let impact = events.reduce(0.0) { $0 + $1.impact }
                let wickets = events.filter { $0.description.contains("wicket") }.count
                let runs = events.filter { $0.description.contains("runs") }
                    .compactMap { Int($0.description.components(separatedBy: " ").first { $0.contains("runs") } ?? "0") }
                    .reduce(0, +)
                let balls = events.count
                let economy = Double(runs) / Double(balls) * 6.0
                
                return (impact: impact, wickets: wickets, economy: economy)
            }
            .map { (bowler: $0.key, impact: $0.value.impact, wickets: $0.value.wickets, economy: $0.value.economy) }
            .sorted { abs($0.impact) > abs($1.impact) }
        
        return bowlerImpacts
    }
}

struct RunRateAnalysisView: View {
    let events: [ProbabilityEvent]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Run Rate Analysis")
                .font(.headline)
            
            ForEach(getRunRatePhases(), id: \.phase) { phase in
                VStack(alignment: .leading) {
                    Text(phase.phase)
                        .font(.subheadline)
                    HStack {
                        Text("Run Rate: \(String(format: "%.1f", phase.runRate))")
                            .font(.caption)
                        Spacer()
                        Text("Required: \(String(format: "%.1f", phase.requiredRate))")
                            .font(.caption)
                            .foregroundColor(phase.runRate >= phase.requiredRate ? .green : .red)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
    }
    
    private func getRunRatePhases() -> [(phase: String, runRate: Double, requiredRate: Double)] {
        let powerPlayEvents = events.filter { $0.ball <= 36 }
        let middleOversEvents = events.filter { $0.ball > 36 && $0.ball < 240 }
        let deathOversEvents = events.filter { $0.ball >= 240 }
        
        return [
            calculatePhaseRunRate("Power Play", events: powerPlayEvents),
            calculatePhaseRunRate("Middle Overs", events: middleOversEvents),
            calculatePhaseRunRate("Death Overs", events: deathOversEvents)
        ]
    }
    
    private func calculatePhaseRunRate(_ phase: String, events: [ProbabilityEvent]) -> (phase: String, runRate: Double, requiredRate: Double) {
        let runs = events.filter { $0.description.contains("runs") }
            .compactMap { Int($0.description.components(separatedBy: " ").first { $0.contains("runs") } ?? "0") }
            .reduce(0, +)
        let balls = events.count
        let runRate = Double(runs) / Double(balls) * 6.0
        
        // Calculate required rate based on remaining runs and balls
        let remainingRuns = 200 - runs // Assuming 200 is the target
        let remainingBalls = 300 - balls // Assuming 50 overs
        let requiredRate = Double(remainingRuns) / Double(remainingBalls) * 6.0
        
        return (phase: phase, runRate: runRate, requiredRate: requiredRate)
    }
}

struct PlayerImpactView: View {
    let events: [ProbabilityEvent]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Player Impact Analysis")
                .font(.headline)
            
            ForEach(getPlayerImpacts(), id: \.player) { impact in
                HStack {
                    Text(impact.player)
                        .font(.subheadline)
                    Spacer()
                    Text("\(String(format: "%.1f", impact.impact))%")
                        .foregroundColor(impact.impact > 0 ? .green : .red)
                }
                .padding(.vertical, 4)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
    }
    
    private func getPlayerImpacts() -> [(player: String, impact: Double)] {
        // Group events by player and calculate impact
        let playerImpacts = Dictionary(grouping: events) { $0.description.components(separatedBy: " ").first ?? "" }
            .mapValues { events in
                events.reduce(0.0) { $0 + $1.impact }
            }
            .map { (player: $0.key, impact: $0.value) }
            .sorted { abs($0.impact) > abs($1.impact) }
        
        return playerImpacts
    }
}

struct PartnershipAnalysisView: View {
    let events: [ProbabilityEvent]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Partnership Analysis")
                .font(.headline)
            
            ForEach(getPartnerships(), id: \.id) { partnership in
                VStack(alignment: .leading) {
                    Text("\(partnership.player1) & \(partnership.player2)")
                        .font(.subheadline)
                    Text("Runs: \(partnership.runs)")
                        .font(.caption)
                    Text("Impact: \(String(format: "%.1f", partnership.impact))%")
                        .font(.caption)
                        .foregroundColor(partnership.impact > 0 ? .green : .red)
                }
                .padding(.vertical, 4)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
    }
    
    private func getPartnerships() -> [(id: Int, player1: String, player2: String, runs: Int, impact: Double)] {
        // Analyze partnerships from events
        var partnerships: [(id: Int, player1: String, player2: String, runs: Int, impact: Double)] = []
        var currentPartnership: (player1: String, player2: String, runs: Int, impact: Double)?
        
        for event in events {
            if event.description.contains("partnership") {
                let components = event.description.components(separatedBy: " ")
                if components.count >= 4 {
                    let player1 = components[0]
                    let player2 = components[2]
                    let runs = Int(components[4]) ?? 0
                    
                    if let existing = currentPartnership,
                       existing.player1 == player1 && existing.player2 == player2 {
                        currentPartnership?.runs += runs
                        currentPartnership?.impact += event.impact
                    } else {
                        if let existing = currentPartnership {
                            partnerships.append((
                                id: partnerships.count,
                                player1: existing.player1,
                                player2: existing.player2,
                                runs: existing.runs,
                                impact: existing.impact
                            ))
                        }
                        currentPartnership = (player1, player2, runs, event.impact)
                    }
                }
            }
        }
        
        if let last = currentPartnership {
            partnerships.append((
                id: partnerships.count,
                player1: last.player1,
                player2: last.player2,
                runs: last.runs,
                impact: last.impact
            ))
        }
        
        return partnerships.sorted { abs($0.impact) > abs($1.impact) }
    }
}

struct PhaseAnalysisView: View {
    let events: [ProbabilityEvent]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Phase Analysis")
                .font(.headline)
            
            let powerPlayEvents = events.filter { $0.ball <= 36 }
            let middleOversEvents = events.filter { $0.ball > 36 && $0.ball < 240 }
            let deathOversEvents = events.filter { $0.ball >= 240 }
            
            PhaseAnalysisRow(
                title: "Power Play",
                events: powerPlayEvents,
                color: .blue
            )
            .transition(.slide)
            
            PhaseAnalysisRow(
                title: "Middle Overs",
                events: middleOversEvents,
                color: .green
            )
            .transition(.slide)
            
            PhaseAnalysisRow(
                title: "Death Overs",
                events: deathOversEvents,
                color: .red
            )
            .transition(.slide)
        }
        .padding()
    }
}

struct PhaseAnalysisRow: View {
    let title: String
    let events: [ProbabilityEvent]
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading) {
            Text(title)
                .font(.subheadline)
            
            if let firstEvent = events.first,
               let lastEvent = events.last {
                let change = lastEvent.probability - firstEvent.probability
                Text("Probability Change: \(String(format: "%.1f", change))%")
                    .font(.caption)
                    .foregroundColor(change > 0 ? .green : .red)
            }
            
            // Event distribution
            HStack {
                ForEach(ProbabilityEvent.EventType.allCases, id: \.self) { type in
                    let count = events.filter { $0.eventType == type }.count
                    if count > 0 {
                        VStack {
                            Text("\(count)")
                                .font(.caption)
                            Text(type.rawValue)
                                .font(.caption2)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

extension ProbabilityEvent.EventType: CaseIterable {
    static var allCases: [ProbabilityEvent.EventType] {
        [.boundary, .wicket, .dotBall, .runOut, .powerPlay, .deathOvers, .milestone, .other]
    }
    
    var rawValue: String {
        switch self {
        case .boundary: return "4s/6s"
        case .wicket: return "W"
        case .dotBall: return "0"
        case .runOut: return "RO"
        case .powerPlay: return "PP"
        case .deathOvers: return "DO"
        case .milestone: return "M"
        case .other: return "O"
        }
    }
} 