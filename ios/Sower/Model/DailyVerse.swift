import Foundation

/// The verse of the day. The same thirty references the Android edition uses,
/// picked by day of the year so both apps show the same verse on the same date.
enum DailyVerse {

    struct Reference {
        let book: String
        let chapter: Int
        let verse: Int
    }

    static let all: [Reference] = [
        Reference(book: "john", chapter: 3, verse: 16),
        Reference(book: "romans", chapter: 5, verse: 8),
        Reference(book: "psalms", chapter: 23, verse: 1),
        Reference(book: "isaiah", chapter: 53, verse: 5),
        Reference(book: "john", chapter: 14, verse: 6),
        Reference(book: "romans", chapter: 8, verse: 38),
        Reference(book: "ephesians", chapter: 2, verse: 8),
        Reference(book: "john", chapter: 1, verse: 1),
        Reference(book: "psalms", chapter: 46, verse: 1),
        Reference(book: "matthew", chapter: 11, verse: 28),
        Reference(book: "romans", chapter: 10, verse: 9),
        Reference(book: "1john", chapter: 1, verse: 9),
        Reference(book: "isaiah", chapter: 40, verse: 31),
        Reference(book: "philippians", chapter: 4, verse: 6),
        Reference(book: "john", chapter: 10, verse: 10),
        Reference(book: "2corinthians", chapter: 5, verse: 17),
        Reference(book: "jeremiah", chapter: 29, verse: 11),
        Reference(book: "psalms", chapter: 121, verse: 1),
        Reference(book: "matthew", chapter: 28, verse: 19),
        Reference(book: "acts", chapter: 4, verse: 12),
        Reference(book: "romans", chapter: 6, verse: 23),
        Reference(book: "revelation", chapter: 3, verse: 20),
        Reference(book: "galatians", chapter: 2, verse: 20),
        Reference(book: "hebrews", chapter: 11, verse: 1),
        Reference(book: "proverbs", chapter: 3, verse: 5),
        Reference(book: "joshua", chapter: 1, verse: 9),
        Reference(book: "micah", chapter: 6, verse: 8),
        Reference(book: "zephaniah", chapter: 3, verse: 17),
        Reference(book: "lamentations", chapter: 3, verse: 22),
        Reference(book: "1peter", chapter: 5, verse: 7),
    ]

    static func today(_ date: Date = Date(), calendar: Calendar = .current) -> Reference {
        let day = calendar.ordinality(of: .day, in: .year, for: date) ?? 1
        return all[day % all.count]
    }
}
