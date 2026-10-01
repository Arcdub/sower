import SwiftUI

/// Verse strings from the shared assets mark the words of Jesus with sentinel
/// characters (U+0001 opens a span, U+0002 closes it, see tools/transform.js).
/// This renders those spans, or strips them for plain-text share and copy.
enum RedLetter {

    static let open: Character = "\u{0001}"
    static let close: Character = "\u{0002}"

    /// How the words of Jesus are distinguished.
    enum Style: Int, CaseIterable, Identifiable {
        case red = 0
        /// Colour-blind friendly: distinguish by weight instead of hue.
        case bold = 1
        case none = 2

        var id: Int { rawValue }
    }

    /// The verse with the sentinel markers removed.
    static func plain(_ raw: String) -> String {
        guard raw.contains(open) || raw.contains(close) else { return raw }
        return String(raw.filter { $0 != open && $0 != close })
    }

    /// The verse stripped of its markers, together with where the words of
    /// Jesus fall in the stripped text. Offsets count characters, which is what
    /// the highlight ranges count too, so the two can be drawn in one pass.
    static func spans(_ raw: String) -> (plain: String, red: [Range<Int>]) {
        guard raw.contains(open) || raw.contains(close) else { return (raw, []) }

        var plain = ""
        var red: [Range<Int>] = []
        var spanStart: Int?
        var index = 0

        for character in raw {
            switch character {
            case open:
                spanStart = index
            case close:
                if let start = spanStart, start < index {
                    red.append(start..<index)
                }
                spanStart = nil
            default:
                plain.append(character)
                index += 1
            }
        }
        // An unclosed span runs to the end of the verse, as on Android.
        if let start = spanStart, start < index {
            red.append(start..<index)
        }
        return (plain, red)
    }

    /// The verse with the words of Jesus in the reader's chosen style.
    static func styled(_ raw: String, style: Style, color: Color) -> AttributedString {
        guard style != .none, raw.contains(open) || raw.contains(close) else {
            return AttributedString(plain(raw))
        }

        var result = AttributedString()
        var span = AttributedString()
        var inside = false

        for character in raw {
            switch character {
            case open:
                inside = true
            case close:
                result.append(decorate(span, style: style, color: color))
                span = AttributedString()
                inside = false
            default:
                if inside {
                    span.append(AttributedString(String(character)))
                } else {
                    result.append(AttributedString(String(character)))
                }
            }
        }
        // An unclosed span runs to the end of the verse, as on Android.
        if inside {
            result.append(decorate(span, style: style, color: color))
        }
        return result
    }

    private static func decorate(_ span: AttributedString,
                                 style: Style,
                                 color: Color) -> AttributedString {
        guard !span.characters.isEmpty else { return span }
        var copy = span
        switch style {
        case .red:
            copy.foregroundColor = color
        case .bold:
            copy.inlinePresentationIntent = .stronglyEmphasized
        case .none:
            break
        }
        return copy
    }
}
