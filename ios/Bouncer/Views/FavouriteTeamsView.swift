import SwiftUI

struct FavouriteTeamsView: View {
    @ObservedObject var manager: FavouriteTeamsManager
    let allTeams: [String] // e.g. ["IND", "AUS", ...]
    let logoLoader: TeamLogoLoader

    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Your Favourites (drag to reorder)")) {
                    ForEach(manager.favourites, id: \.self) { team in
                        HStack {
                            if let urlString = logoLoader.getLogoUrl(for: team), let url = URL(string: urlString) {
                                AsyncImage(url: url) { image in
                                    image.resizable().scaledToFit()
                                } placeholder: {
                                    Color.gray.opacity(0.2)
                                }
                                .frame(width: 32, height: 32)
                                .clipShape(Circle())
                            }
                            Text(team)
                                .font(.headline)
                            Spacer()
                            Button(action: { manager.remove(team: team) }) {
                                Image(systemName: "minus.circle.fill")
                                    .foregroundColor(.red)
                            }
                        }
                    }
                    .onMove(perform: manager.move)
                }

                Section(header: Text("Add More Teams")) {
                    if manager.favourites.count >= 10 {
                        Text("You can only select up to 10 favourite teams.")
                            .foregroundColor(.secondary)
                            .font(.caption)
                    }
                    ForEach(allTeams.filter { !manager.favourites.contains($0) }, id: \.self) { team in
                        Button(action: { manager.add(team: team) }) {
                            HStack {
                                if let urlString = logoLoader.getLogoUrl(for: team), let url = URL(string: urlString) {
                                    AsyncImage(url: url) { image in
                                        image.resizable().scaledToFit()
                                    } placeholder: {
                                        Color.gray.opacity(0.2)
                                    }
                                    .frame(width: 28, height: 28)
                                    .clipShape(Circle())
                                }
                                Text(team)
                                Spacer()
                                Image(systemName: "plus.circle.fill")
                                    .foregroundColor(.gold)
                            }
                        }
                        .disabled(manager.favourites.count >= 10)
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("Favourite Teams")
            .toolbar {
                EditButton()
            }
        }
    }
}

extension Color {
    static let gold = Color("GoldPrimary")
} 