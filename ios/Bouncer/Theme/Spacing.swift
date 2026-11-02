import SwiftUI

/// Spacing and shape tokens (SpacingV2.kt, ShapesV2.kt).
enum Spacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    /// One list row: 16 horizontal x 14 vertical.
    static let rowH: CGFloat = 16
    static let rowV: CGFloat = 14
}

enum Radius {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    /// Fully rounded; use with `Capsule()` or as a corner radius larger than half the height.
    static let pill: CGFloat = 999
}
