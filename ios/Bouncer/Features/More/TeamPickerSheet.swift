import SwiftUI

/// Searchable team list: tap to add/remove, drag the handles in "Your teams" to reorder.
struct TeamPickerSheet: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var logos = TeamLogoLoader.shared
    @State private var query = ""
    @State private var editMode: EditMode = .inactive

    private var others: [String] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return logos.teamLogos.keys.sorted()
            .filter { !env.favourites.isFavourite($0) && (q.isEmpty || $0.localizedCaseInsensitiveContains(q)) }
    }

    var body: some View {
        NavigationStack {
            List {
                if query.isEmpty && !env.favourites.teams.isEmpty {
                    Section {
                        ForEach(env.favourites.teams, id: \.self) { name in
                            teamRow(name, selected: true)
                        }
                        .onMove { env.favourites.move(fromOffsets: $0, toOffset: $1) }
                        .onDelete { $0.map { env.favourites.teams[$0] }.forEach(env.favourites.remove) }
                    } header: { header("Your teams (\(env.favourites.teams.count)/\(FavouritesStore.maxTeams))") }
                }
                Section {
                    ForEach(others, id: \.self) { teamRow($0, selected: false) }
                } header: { header("All teams") }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(BouncerColor.bg)
            .searchable(text: $query, prompt: "Search teams")
            .environment(\.editMode, $editMode)
            .navigationTitle("Your teams")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !env.favourites.teams.isEmpty { EditButton().tint(BouncerColor.goldInk) }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }.tint(BouncerColor.goldInk)
                }
            }
        }
    }

    private func header(_ t: String) -> some View {
        Text(t).font(.titleSmall.weight(.semibold)).foregroundStyle(BouncerColor.inkPrimary).textCase(nil)
    }

    private func teamRow(_ name: String, selected: Bool) -> some View {
        Button { env.favourites.toggle(name) } label: {
            HStack(spacing: Spacing.md) {
                TeamCrest(name: name, imageURL: logos.getLogoUrl(for: name).flatMap(URL.init(string:)), size: 28)
                Text(name).font(.bodyLarge).foregroundStyle(BouncerColor.inkPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: selected ? "checkmark.circle.fill" : "plus.circle")
                    .foregroundStyle(selected ? BouncerColor.goldInk : BouncerColor.inkSecondary)
            }
            .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
        .listRowBackground(BouncerColor.surface)
        .listRowSeparatorTint(BouncerColor.divider)
        .accessibilityValue(selected ? "Selected" : "Not selected")
        .sensoryFeedback(.selection, trigger: selected)
    }
}
