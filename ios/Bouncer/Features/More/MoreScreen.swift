import SwiftUI

struct MoreScreen: View {
    @Environment(AppEnvironment.self) private var env
    @AppStorage("appearance") private var appearance = "system"
    @State private var editingTeams = false
    @State private var confirmClear = false
    @State private var cleared = false
    @ObservedObject private var logos = TeamLogoLoader.shared

    private var version: String {
        let info = Bundle.main.infoDictionary
        let v = info?["CFBundleShortVersionString"] as? String ?? "?"
        let b = info?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                teams
                section("Appearance") {
                    Picker("Appearance", selection: $appearance) {
                        Text("System").tag("system"); Text("Light").tag("light"); Text("Dark").tag("dark")
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
                }
                section("Data") {
                    Button { confirmClear = true } label: {
                        row("Clear cache", subtitle: cleared ? "Cleared" : "Remove saved responses and images")
                    }.buttonStyle(.plain)
                }
                section("About") {
                    row("Version", trailing: version)
                    Divider().overlay(BouncerColor.divider)
                    row("Bouncer", subtitle: "Live cricket scores, made to be fast and quiet.")
                }
                Text("Made for cricket fans").font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary)
                    .frame(maxWidth: .infinity).padding(.top, Spacing.sm)
            }
            .padding(.vertical, Spacing.md)
        }
        .background(BouncerColor.bg)
        .navigationTitle("More")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $editingTeams) { TeamPickerSheet().environment(env) }
        .confirmationDialog("Clear cache?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Clear cache", role: .destructive) {
                URLCache.shared.removeAllCachedResponses(); cleared = true
            }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Saved responses and images will be downloaded again.") }
    }

    private var teams: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader(title: "Your teams", action: "Edit") { editingTeams = true }
            if env.favourites.teams.isEmpty {
                Text("Add favourite teams to see their matches first.")
                    .font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary)
                    .padding(.horizontal, Spacing.lg).padding(.bottom, Spacing.md)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.lg) {
                        ForEach(env.favourites.teams, id: \.self) { name in
                            NavigationLink(value: Route.team(name: name)) {
                                VStack(spacing: Spacing.xs) {
                                    TeamCrest(name: name, imageURL: logos.getLogoUrl(for: name).flatMap(URL.init(string:)), size: 56)
                                    Text(name).font(.labelSmall).foregroundStyle(BouncerColor.inkSecondary)
                                        .lineLimit(1).frame(width: 64)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, Spacing.lg).padding(.bottom, Spacing.md)
                }
            }
        }
        .background(BouncerColor.surface)
        .overlay(alignment: .bottom) { Divider().overlay(BouncerColor.divider) }
        .overlay(alignment: .top) { Divider().overlay(BouncerColor.divider) }
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader(title: title)
            VStack(spacing: 0) { content() }
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(BouncerColor.surface)
                .overlay(alignment: .top) { Divider().overlay(BouncerColor.divider) }
                .overlay(alignment: .bottom) { Divider().overlay(BouncerColor.divider) }
        }
    }

    private func row(_ title: String, subtitle: String? = nil, trailing: String? = nil) -> some View {
        HStack(spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(title).font(.titleMedium).foregroundStyle(BouncerColor.inkPrimary)
                if let subtitle { Text(subtitle).font(.bodySmall).foregroundStyle(BouncerColor.inkSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let trailing { Text(trailing).font(.bodyMedium).foregroundStyle(BouncerColor.inkSecondary).tabularFigures() }
        }
        .padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
        .frame(minHeight: 56).contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}
