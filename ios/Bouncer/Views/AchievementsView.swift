import SwiftUI

struct AchievementsView: View {
    let achievements: [Achievement]
    let unlockedCount: Int
    let totalCount: Int
    
    private var sections: [(title: String, items: [Achievement])] {
        [
            ("Batting", achievements.filter { $0.id.rawValue.hasPrefix("runs") || $0.id == .fastestFifty || $0.id == .fastestHundred || $0.id == .mostSixes }),
            ("Bowling", achievements.filter { $0.id.rawValue.hasPrefix("wickets") || $0.id == .maidenOver || $0.id == .hatTrick || $0.id == .bestEconomy }),
            ("Team", achievements.filter { $0.id.rawValue.hasPrefix("team") || $0.id == .successfulChase }),
            ("Special", achievements.filter { $0.id == .playerOfMatch || $0.id == .recordPartnership })
        ]
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Progress summary
                VStack(spacing: 8) {
                    Text("\(unlockedCount)/\(totalCount) Achievements Unlocked")
                        .font(.headline)
                        .padding(.top, 16)
                    ProgressView(value: Double(unlockedCount), total: Double(totalCount))
                        .accentColor(.green)
                        .padding(.horizontal)
                }
                .background(BlurView(style: .systemMaterial))
                .cornerRadius(16)
                .padding(.bottom, 8)
                
                List {
                    ForEach(sections, id: \.title) { section in
                        if !section.items.isEmpty {
                            Section(header: Text(section.title).font(.title3).bold().foregroundColor(.accentColor)) {
                                ForEach(section.items) { achievement in
                                    HStack(spacing: 16) {
                                        ZStack {
                                            Circle()
                                                .fill(achievement.unlocked ? Color.green.opacity(0.18) : Color.gray.opacity(0.08))
                                                .frame(width: 48, height: 48)
                                            Text(achievement.icon)
                                                .font(.system(size: 28))
                                                .scaleEffect(achievement.unlocked ? 1.15 : 1.0)
                                                .opacity(achievement.unlocked ? 1 : 0.5)
                                                .animation(.spring(response: 0.5, dampingFraction: 0.7), value: achievement.unlocked)
                                                .overlay(
                                                    achievement.unlocked ?
                                                        LinearGradient(gradient: Gradient(colors: [.white.opacity(0.7), .clear]), startPoint: .top, endPoint: .bottom)
                                                            .clipShape(Circle())
                                                            .opacity(0.5)
                                                    : nil
                                                )
                                        }
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(achievement.title)
                                                .font(.headline)
                                                .foregroundColor(achievement.unlocked ? .primary : .secondary)
                                            Text(achievement.description)
                                                .font(.subheadline)
                                                .foregroundColor(.secondary)
                                        }
                                        Spacer()
                                        if achievement.unlocked {
                                            Image(systemName: "checkmark.seal.fill")
                                                .foregroundColor(.green)
                                                .transition(.scale.combined(with: .opacity))
                                                .animation(.spring(), value: achievement.unlocked)
                                        }
                                    }
                                    .padding(.vertical, 8)
                                    .background(
                                        (achievement.unlocked ? Color.green.opacity(0.07) : Color.clear).cornerRadius(12)
                                    )
                                }
                            }
                        }
                    }
                }
                .listStyle(InsetGroupedListStyle())
            }
            .navigationTitle("Achievements")
        }
    }
}

// Preview
#Preview {
    AchievementsView(
        achievements: Achievement.all.map { a in
            var copy = a
            copy.unlocked = Bool.random()
            return copy
        },
        unlockedCount: 7,
        totalCount: Achievement.all.count
    )
} 