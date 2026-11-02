import SwiftUI

struct ChipsView: View {
    @Binding var selectedChip: String
    @EnvironmentObject var liveActivityManager: LiveActivityManager
    
    let chips = ["All", "International", "Domestic", "League", "Women"]
    
    func updateMatches(for chip: String) async {
        selectedChip = chip
        await liveActivityManager.pollForUpdates()
    }
    
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(chips, id: \.self) { chip in
                    Button(action: {
                        Task {
                            await updateMatches(for: chip)
                        }
                    }) {
                        HStack {
                            Text(chip)
                            if case .refreshing = liveActivityManager.loadingState,
                               selectedChip == chip {
                                ProgressView()
                                    .scaleEffect(0.7)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(selectedChip == chip ? Color.green : Color.gray.opacity(0.2))
                        .foregroundColor(selectedChip == chip ? .white : .black)
                        .cornerRadius(20)
                    }
                    .disabled(liveActivityManager.loadingState == .refreshing)
                }
            }
            .padding()
        }
    }
}