import Foundation

/// Character-range verse highlights, stored as
/// "translationId:bookFile:chapter:verse:start:end". Offsets index the plain
/// verse text (red-letter markers stripped), so ranges are per translation.
/// Legacy whole-verse keys ("bookFile:chapter:verse") are still honoured, which
/// is also what lets a reader's Android highlights make sense here.
enum Highlights {

    /// Sentinel end for a legacy whole-verse highlight; renderers clamp it to
    /// the length of the text they are drawing.
    static let wholeVerse = Int.max

    /// verse number -> merged [start, end) ranges, for one chapter.
    static func ranges(translation: String,
                       book: String,
                       chapter: Int) -> [Int: [Range<Int>]] {
        let rangePrefix = "\(translation):\(book):\(chapter):"
        let legacyPrefix = "\(book):\(chapter):"
        var found: [Int: [Range<Int>]] = [:]

        for key in Prefs.highlights {
            let parts = key.split(separator: ":", omittingEmptySubsequences: false)
            var verse: Int
            var range: Range<Int>

            if parts.count == 6, key.hasPrefix(rangePrefix) {
                guard let v = Int(parts[3]),
                      let start = Int(parts[4]),
                      let end = Int(parts[5]), start < end else { continue }
                verse = v
                range = start..<end
            } else if parts.count == 3, key.hasPrefix(legacyPrefix) {
                guard let v = Int(parts[2]) else { continue }
                verse = v
                range = 0..<wholeVerse
            } else {
                continue
            }
            found[verse, default: []].append(range)
        }

        // Merge, not just sort: overlapping stored keys (an interrupted write,
        // a restored backup) must never draw a doubled highlight.
        return found.mapValues(merged)
    }

    /// Adds [start, end) to a verse, merging anything it overlaps or touches.
    static func add(translation: String,
                    book: String,
                    chapter: Int,
                    verse: Int,
                    range: Range<Int>,
                    verseLength: Int) {
        var ranges = collect(translation: translation, book: book, chapter: chapter,
                             verse: verse, verseLength: verseLength)
        ranges.append(range)
        write(translation: translation, book: book, chapter: chapter,
              verse: verse, ranges: merged(ranges))
    }

    /// Removes [start, end), splitting any range it cuts through.
    static func remove(translation: String,
                       book: String,
                       chapter: Int,
                       verse: Int,
                       range: Range<Int>,
                       verseLength: Int) {
        let ranges = collect(translation: translation, book: book, chapter: chapter,
                             verse: verse, verseLength: verseLength)
        var kept: [Range<Int>] = []
        for existing in ranges {
            if existing.upperBound <= range.lowerBound || existing.lowerBound >= range.upperBound {
                kept.append(existing)
                continue
            }
            if existing.lowerBound < range.lowerBound {
                kept.append(existing.lowerBound..<range.lowerBound)
            }
            if existing.upperBound > range.upperBound {
                kept.append(range.upperBound..<existing.upperBound)
            }
        }
        write(translation: translation, book: book, chapter: chapter,
              verse: verse, ranges: merged(kept))
    }

    static func isHighlighted(translation: String,
                              book: String,
                              chapter: Int,
                              verse: Int) -> Bool {
        !(ranges(translation: translation, book: book, chapter: chapter)[verse] ?? []).isEmpty
    }

    /// Every highlighted verse, newest first is not knowable, so this keeps
    /// canonical order for the highlights-only search.
    static func allKeys() -> Set<String> {
        Prefs.highlights
    }

    // MARK: - Storage

    /// Existing ranges for one verse, with a legacy whole-verse key resolved to
    /// a real range now that the verse length is known.
    private static func collect(translation: String,
                                book: String,
                                chapter: Int,
                                verse: Int,
                                verseLength: Int) -> [Range<Int>] {
        (ranges(translation: translation, book: book, chapter: chapter)[verse] ?? [])
            .map { $0.upperBound == wholeVerse ? $0.lowerBound..<verseLength : $0 }
            .filter { $0.lowerBound < $0.upperBound }
    }

    private static func write(translation: String,
                              book: String,
                              chapter: Int,
                              verse: Int,
                              ranges: [Range<Int>]) {
        let rangePrefix = "\(translation):\(book):\(chapter):\(verse):"
        let legacyKey = "\(book):\(chapter):\(verse)"
        var keys = Prefs.highlights
        keys = keys.filter { !$0.hasPrefix(rangePrefix) && $0 != legacyKey }
        for range in ranges {
            keys.insert("\(rangePrefix)\(range.lowerBound):\(range.upperBound)")
        }
        Prefs.highlights = keys
    }

    /// Sorts ranges and merges any that overlap or touch.
    private static func merged(_ ranges: [Range<Int>]) -> [Range<Int>] {
        var out: [Range<Int>] = []
        for range in ranges.sorted(by: { $0.lowerBound < $1.lowerBound }) {
            if let last = out.last, range.lowerBound <= last.upperBound {
                out[out.count - 1] = last.lowerBound..<max(last.upperBound, range.upperBound)
            } else {
                out.append(range)
            }
        }
        return out
    }
}
