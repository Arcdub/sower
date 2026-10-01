import Foundation

/// Reading state, stored in UserDefaults under the same key names the Android
/// edition uses in its SharedPreferences, so the two stay readable side by side.
enum Prefs {

    private enum Key {
        static let lastBook = "lastBook"
        static let lastChapter = "lastChapter"
        static let lastVerse = "lastVerse"
        static let textSize = "textSize"
        static let translation = "translation"
        static let wordsOfJesusStyle = "wordsOfJesusStyle"
        static let highlights = "highlights"
    }

    static let minTextSize: Double = 14
    static let maxTextSize: Double = 30
    static let defaultTextSize: Double = 18

    private static var store: UserDefaults { .standard }

    /// Selected translation id when an edition bundles more than one.
    static var translation: String? {
        get { store.string(forKey: Key.translation) }
        set { store.set(newValue, forKey: Key.translation) }
    }

    static var wordsOfJesusStyle: RedLetter.Style {
        get { RedLetter.Style(rawValue: store.integer(forKey: Key.wordsOfJesusStyle)) ?? .red }
        set { store.set(newValue.rawValue, forKey: Key.wordsOfJesusStyle) }
    }

    static var textSize: Double {
        get {
            let stored = store.double(forKey: Key.textSize)
            return stored == 0 ? defaultTextSize : stored
        }
        set { store.set(newValue, forKey: Key.textSize) }
    }

    static var lastBook: String? {
        store.string(forKey: Key.lastBook)
    }

    static var lastChapter: Int {
        max(1, store.integer(forKey: Key.lastChapter))
    }

    /// The verse at the top of the screen when the reader was last left.
    static var lastVerse: Int {
        get { max(1, store.integer(forKey: Key.lastVerse)) }
        set { store.set(newValue, forKey: Key.lastVerse) }
    }

    static func setLastRead(book: String, chapter: Int) {
        store.set(book, forKey: Key.lastBook)
        store.set(chapter, forKey: Key.lastChapter)
        store.set(1, forKey: Key.lastVerse)
    }

    /// Highlight keys. Their format and range maths live in Highlights.swift.
    static var highlights: Set<String> {
        get { Set(store.stringArray(forKey: Key.highlights) ?? []) }
        set { store.set(Array(newValue), forKey: Key.highlights) }
    }
}
