import SwiftUI

/// The palette, carried over from the Android resources so the two editions
/// look like one app. Each colour names its light value first and its night
/// value second; night is a deeper page with surfaces that sit clearly above
/// it, and gold stays an accent rather than an outline on everything.
enum Theme {

    static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }

    static let green = dynamic(light: 0x2E6B34, dark: 0x2E6B34)
    static let gold = dynamic(light: 0xC8A24B, dark: 0xC8A24B)

    static let toolbar = dynamic(light: 0x2E6B34, dark: 0x131C17)
    static let page = dynamic(light: 0xF6F4EE, dark: 0x17211C)
    static let tile = dynamic(light: 0xFFFFFF, dark: 0x222E28)
    static let tileBorder = dynamic(light: 0xDED9CE, dark: 0x314038)
    static let bookText = dynamic(light: 0x2B3A2C, dark: 0xE8E0CF)
    static let bookIcon = dynamic(light: 0xC8A24B, dark: 0xC8A24B)
    static let chapterRing = dynamic(light: 0xC8A24B, dark: 0x7A6534)

    static let redLetter = dynamic(light: 0xC62828, dark: 0xEF5350)
    static let verseNumber = dynamic(light: 0x2E6B34, dark: 0x8CBF94)

    static let verseCard = dynamic(light: 0xD8E8D4, dark: 0x1E2C24)
    static let highlight = dynamic(light: 0xF0E2B8, dark: 0x4A3F22)

    static let filledButton = dynamic(light: 0x2E6B34, dark: 0x8CBF94)
    static let filledButtonText = dynamic(light: 0xFFFFFF, dark: 0x10241A)
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1)
    }
}
