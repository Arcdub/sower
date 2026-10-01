import SwiftUI

/// The front page: a verse for today, wherever reading was left off, and the
/// sixty-six books. The share button is pinned so passing the app on is never
/// more than one tap away.
struct BookListView: View {
    @EnvironmentObject private var bible: BibleStore
    @State private var path: [Route] = []
    @State private var showingTranslations = false
    @State private var showingAccessibility = false
    @State private var showingPrivacy = false

    /// Word for word the text the Android edition shows.
    private static let privacyPolicy = """
        Sower respects your privacy completely. It collects no personal information, requests no permissions, and makes no network connections, so it cannot send your data anywhere. Everything you read and search stays only on your device. Nothing is tracked, stored on a server, or shared with anyone.

        Freely you received; freely give.
        """

    private let columns = [GridItem(.flexible(), spacing: 12),
                           GridItem(.flexible(), spacing: 12)]

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    verseOfTheDay
                    continueReading
                    testament(title: "Old Testament", books: bible.books.filter { !$0.nt })
                    testament(title: "New Testament", books: bible.books.filter(\.nt))
                }
                .padding(20)
                .padding(.bottom, 72)
            }
            .background(Theme.page)
            .navigationTitle("Sower")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Theme.toolbar, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar { toolbarItems }
            .overlay(alignment: .bottom) { passItOnButton }
            .navigationDestination(for: Route.self) { destination(for: $0) }
            .confirmationDialog("Translation", isPresented: $showingTranslations) {
                ForEach(bible.translations) { translation in
                    Button(translation.displayName) { bible.select(translation) }
                }
                Button("Cancel", role: .cancel) {}
            }
            .sheet(isPresented: $showingAccessibility) { AccessibilityView() }
            .alert("Privacy policy", isPresented: $showingPrivacy) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(Self.privacyPolicy)
            }
        }
    }

    // MARK: - Pieces

    @ViewBuilder
    private var verseOfTheDay: some View {
        let reference = DailyVerse.today()
        if let found = bible.verse(book: reference.book,
                                   chapter: reference.chapter,
                                   verse: reference.verse) {
            Button {
                path.append(.reader(book: reference.book,
                                    chapter: reference.chapter,
                                    scrollTo: reference.verse))
            } label: {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Verse of the day")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.bookText)
                    Text(RedLetter.styled(found.text,
                                          style: Prefs.wordsOfJesusStyle,
                                          color: Theme.redLetter))
                        .font(.body)
                        .foregroundStyle(Theme.bookText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(found.bookName) \(reference.chapter):\(reference.verse)")
                        .font(.footnote.italic())
                        .foregroundStyle(Theme.bookText.opacity(0.8))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .padding(16)
                .background(Theme.verseCard)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.gold, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var continueReading: some View {
        if let file = Prefs.lastBook,
           let book = bible.book(withFile: file),
           let loaded = bible.text(for: file),
           Prefs.lastChapter <= loaded.chapters.count {
            let chapter = Prefs.lastChapter
            let preview = Array(loaded.chapters[chapter - 1].prefix(3).enumerated())
            Button {
                path.append(.reader(book: file, chapter: chapter, scrollTo: Prefs.lastVerse))
            } label: {
                VStack(alignment: .leading, spacing: 8) {
                    Label("CONTINUE READING", systemImage: "bookmark.fill")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Theme.gold)
                    Text("\(book.name) \(chapter)")
                        .font(.title2)
                        .foregroundStyle(Theme.bookText)
                    // Three verses, the last one fading out, so the card reads
                    // as the top of a page rather than a truncated paragraph.
                    ForEach(preview, id: \.offset) { index, verse in
                        (Text("\(index + 1) ")
                            .font(.caption2)
                            .foregroundColor(Theme.verseNumber)
                         + Text(RedLetter.plain(verse))
                            .font(.callout)
                            .foregroundColor(Theme.bookText))
                            .opacity(index == 2 ? 0.45 : 1)
                            .lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Theme.tile)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.tileBorder, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private func testament(title: String, books: [Book]) -> some View {
        Text(title)
            .font(.title3.weight(.bold))
            .foregroundStyle(Theme.green)
            .padding(.top, 12)

        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(books) { book in
                Button {
                    path.append(.chapters(book))
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "book")
                            .foregroundStyle(Theme.bookIcon)
                        Text(book.name)
                            .font(.callout)
                            .foregroundStyle(Theme.bookText)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 16)
                    .background(Theme.tile)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.tileBorder, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var passItOnButton: some View {
        Button {
            path.append(.passItOn)
        } label: {
            Label("Pass it on", systemImage: "square.and.arrow.up")
                .font(.headline)
                .foregroundStyle(Theme.bookText)
                .padding(.horizontal, 22)
                .padding(.vertical, 14)
                .background(Theme.gold)
                .clipShape(Capsule())
                .shadow(radius: 6, y: 2)
        }
        .padding(.bottom, 16)
    }

    @ToolbarContentBuilder
    private var toolbarItems: some ToolbarContent {
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            Button {
                path.append(.search)
            } label: {
                Image(systemName: "magnifyingglass")
            }
            Button {
                path.append(.passItOn)
            } label: {
                Image(systemName: "square.and.arrow.up")
            }
            Menu {
                // The picker only makes sense when this edition bundles more than one.
                if bible.translations.count > 1 {
                    Button("Translation") { showingTranslations = true }
                }
                Button("Accessibility") { showingAccessibility = true }
                // Shown in the app, not linked out: an offline Bible should
                // never need the network to explain that it does not use it.
                Button("Privacy policy") { showingPrivacy = true }
            } label: {
                Image(systemName: "ellipsis")
            }
        }
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .chapters(let book):
            ChapterGridView(book: book, path: $path)
        case .reader(let book, let chapter, let scrollTo):
            ReaderView(bookFile: book, chapter: chapter, scrollTo: scrollTo, path: $path)
        case .search:
            SearchView(path: $path)
        case .passItOn:
            PassItOnView()
        }
    }
}
