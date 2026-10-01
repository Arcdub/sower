import Foundation

struct Translation: Identifiable, Hashable, Decodable {
    let id: String
    let name: String?
    let dir: String

    /// What a shared verse is credited to. An edition that bundles a single
    /// translation has no id worth printing.
    var shareCredit: String {
        id == "default" ? "" : " (\(id.uppercased()))"
    }

    var displayName: String { name ?? id.uppercased() }
}

struct Book: Identifiable, Hashable, Decodable {
    let file: String
    let name: String
    let chapters: Int
    let nt: Bool

    var id: String { file }
}

struct BookText: Decodable {
    let name: String
    let chapters: [[String]]
}

struct SearchResult: Identifiable, Hashable {
    let bookFile: String
    let bookName: String
    let chapter: Int
    let verse: Int
    /// The raw verse, red-letter markers and all.
    let text: String

    var id: String { "\(bookFile):\(chapter):\(verse)" }
    var reference: String { "\(bookName) \(chapter):\(verse)" }
}

/// Loads the bundled Bible(s) from the app bundle, with no network, ever.
/// The JSON is the very same the Android edition ships; project.yml references
/// those folders rather than copying them.
@MainActor
final class BibleStore: ObservableObject {

    @Published private(set) var books: [Book] = []
    @Published private(set) var translations: [Translation] = []
    @Published private(set) var current: Translation

    /// A small cache. Four books is enough for cross-referencing without
    /// holding the whole Bible in memory on an old device.
    private var cache: [String: BookText] = [:]
    private var cacheOrder: [String] = []

    private var corpus: [(book: Book, chapters: [[String]], normalized: [[String]])] = []

    init() {
        let loaded = Self.loadTranslations()
        let saved = Prefs.translation
        // current has no default, so it has to be set before self is touched.
        current = loaded.first { $0.id == saved } ?? loaded[0]
        translations = loaded
        books = Self.loadIndex(dir: current.dir)
    }

    // MARK: - Translations

    private static func loadTranslations() -> [Translation] {
        if let url = Bundle.main.url(forResource: "translations", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let list = try? JSONDecoder().decode([Translation].self, from: data),
           !list.isEmpty {
            return list
        }
        return [Translation(id: "default", name: nil, dir: "bible")]
    }

    /// Switches the reading translation, dropping everything cached for the old
    /// one. Chapter and verse numbering is shared, so the reader stays put.
    func select(_ translation: Translation) {
        guard translation.id != current.id else { return }
        Prefs.translation = translation.id
        current = translation
        cache.removeAll()
        cacheOrder.removeAll()
        corpus.removeAll()
        books = Self.loadIndex(dir: translation.dir)
    }

    // MARK: - Books

    private static func loadIndex(dir: String) -> [Book] {
        guard let url = Bundle.main.url(forResource: "index",
                                        withExtension: "json",
                                        subdirectory: dir),
              let data = try? Data(contentsOf: url),
              let list = try? JSONDecoder().decode([Book].self, from: data) else {
            assertionFailure("Missing or corrupt index.json in \(dir)")
            return []
        }
        return list
    }

    func book(withFile file: String) -> Book? {
        books.first { $0.file == file }
    }

    func text(for file: String) -> BookText? {
        if let hit = cache[file] {
            return hit
        }
        guard let url = Bundle.main.url(forResource: file,
                                        withExtension: "json",
                                        subdirectory: current.dir),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(BookText.self, from: data) else {
            return nil
        }
        cache[file] = decoded
        cacheOrder.append(file)
        if cacheOrder.count > 4, let oldest = cacheOrder.first {
            cacheOrder.removeFirst()
            cache.removeValue(forKey: oldest)
        }
        return decoded
    }

    func verse(book file: String, chapter: Int, verse: Int) -> (text: String, bookName: String)? {
        guard let loaded = text(for: file),
              chapter >= 1, chapter <= loaded.chapters.count else { return nil }
        let verses = loaded.chapters[chapter - 1]
        guard verse >= 1, verse <= verses.count else { return nil }
        return (verses[verse - 1], loaded.name)
    }

    // MARK: - Search

    /// Case and accent folded, with curly quotes flattened, so a search for
    /// "shepherd's" also finds the typographic apostrophe in the text.
    static func normalize(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
            .replacingOccurrences(of: "\u{2019}", with: "'")
            .replacingOccurrences(of: "\u{2018}", with: "'")
            .replacingOccurrences(of: "\u{201C}", with: "\"")
            .replacingOccurrences(of: "\u{201D}", with: "\"")
    }

    /// Builds the folded copy of the whole Bible that searching scans. It costs
    /// a second or two once, then every later search is instant.
    func buildCorpus() {
        guard corpus.isEmpty else { return }
        var built: [(Book, [[String]], [[String]])] = []
        for book in books {
            guard let loaded = text(for: book.file) else { continue }
            let plain = loaded.chapters.map { $0.map(RedLetter.plain) }
            let folded = plain.map { $0.map(Self.normalize) }
            built.append((book, plain, folded))
        }
        corpus = built
    }

    func search(_ query: String, limit: Int = 300) -> [SearchResult] {
        buildCorpus()
        let needle = Self.normalize(query.trimmingCharacters(in: .whitespacesAndNewlines))
        guard !needle.isEmpty else { return [] }

        var results: [SearchResult] = []
        for entry in corpus {
            for (chapterIndex, verses) in entry.normalized.enumerated() {
                for (verseIndex, folded) in verses.enumerated() where folded.contains(needle) {
                    results.append(SearchResult(
                        bookFile: entry.book.file,
                        bookName: entry.book.name,
                        chapter: chapterIndex + 1,
                        verse: verseIndex + 1,
                        text: entry.chapters[chapterIndex][verseIndex]))
                    if results.count >= limit { return results }
                }
            }
        }
        return results
    }

    /// Highlighted verses, in canonical order, narrowed by a query when there
    /// is one. Keys written by the Android edition are read as they are.
    func searchHighlights(_ query: String, limit: Int = 300) -> [SearchResult] {
        let needle = Self.normalize(query.trimmingCharacters(in: .whitespacesAndNewlines))
        var wanted: [String: Set<Int>] = [:]
        let rangePrefix = "\(current.id):"

        for key in Highlights.allKeys() {
            let parts = key.split(separator: ":", omittingEmptySubsequences: false)
            if parts.count == 6, key.hasPrefix(rangePrefix) {
                guard let chapter = Int(parts[2]), let verse = Int(parts[3]) else { continue }
                wanted["\(parts[1]):\(chapter)", default: []].insert(verse)
            } else if parts.count == 3 {
                guard let chapter = Int(parts[1]), let verse = Int(parts[2]) else { continue }
                wanted["\(parts[0]):\(chapter)", default: []].insert(verse)
            }
        }
        guard !wanted.isEmpty else { return [] }

        var results: [SearchResult] = []
        for book in books {
            guard let loaded = text(for: book.file), !loaded.chapters.isEmpty else { continue }
            for chapter in 1...loaded.chapters.count {
                guard let verses = wanted["\(book.file):\(chapter)"]?.sorted() else { continue }
                for verse in verses where verse <= loaded.chapters[chapter - 1].count {
                    let raw = loaded.chapters[chapter - 1][verse - 1]
                    if !needle.isEmpty,
                       !Self.normalize(RedLetter.plain(raw)).contains(needle) { continue }
                    results.append(SearchResult(
                        bookFile: book.file,
                        bookName: book.name,
                        chapter: chapter,
                        verse: verse,
                        text: raw))
                    if results.count >= limit { return results }
                }
            }
        }
        return results
    }
}
