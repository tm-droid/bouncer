import SwiftUI

@main
struct BouncerApp: App {
    @AppStorage("appearance") private var appearance = "system"
    @State private var env: AppEnvironment

    init() {
        BouncerFonts.register()
        ChromeAppearance.configure()
        _env = State(initialValue: AppEnvironment())
    }

    private var scheme: ColorScheme? {
        switch appearance {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(env)
                .environment(env.favourites)
                .preferredColorScheme(scheme)
        }
    }
}
