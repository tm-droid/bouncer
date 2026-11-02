import SwiftUI
import UIKit

/// One-time flat UIKit chrome for tab and navigation bars, from theme tokens.
enum ChromeAppearance {
    @MainActor static func configure() {
        let bg = UIColor(BouncerColor.topBar)
        let divider = UIColor(BouncerColor.divider)
        let ink = UIColor(BouncerColor.inkPrimary)
        let secondary = UIColor(BouncerColor.inkSecondary)
        let gold = UIColor(BouncerColor.goldInk)

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = bg
        tab.shadowColor = divider
        let label = UIFont(name: BouncerFonts.medium, size: 11) ?? .systemFont(ofSize: 11, weight: .medium)
        for item in [tab.stackedLayoutAppearance, tab.inlineLayoutAppearance, tab.compactInlineLayoutAppearance] {
            item.normal.iconColor = secondary
            item.normal.titleTextAttributes = [.foregroundColor: secondary, .font: label]
            item.selected.iconColor = gold
            item.selected.titleTextAttributes = [.foregroundColor: gold, .font: label]
        }
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab

        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = bg
        nav.shadowColor = divider
        nav.titleTextAttributes = [.foregroundColor: ink,
                                   .font: UIFont(name: BouncerFonts.semibold, size: 17) ?? .systemFont(ofSize: 17, weight: .semibold)]
        nav.largeTitleTextAttributes = [.foregroundColor: ink,
                                        .font: UIFont(name: BouncerFonts.bold, size: 28) ?? .systemFont(ofSize: 28, weight: .bold)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
    }
}
