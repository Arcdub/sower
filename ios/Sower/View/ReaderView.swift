import SwiftUI

/// A chapter, one verse per paragraph. Tapping a verse offers to share it,
/// holding it highlights. Where it was left is remembered so the front page can
/// offer to carry on.
struct ReaderView: View {
    let bookFile: String
    let chapter: Int
    let scrollTo: Int?
    @Binding var path: [Route]

    @EnvironmentObject private var bible: BibleStore

    @State private var textSize = Prefs.textSize
    @State private var showingTextSize = false
    @State private var shareText: SharePayload?
    @State private var highlighted: [Int: [Range<Int>]] = [:]

    private var loaded: BookText? { bible.text(for: bookFile) }
    private var verses: [String] {
        guard let loaded, chapter >= 1, chapter <= loaded.chapters.count else { return [] }
        return loaded.chapters[chapter - 1]
    }
    private var bookName: String { loaded?.name ?? "" }
    private var chapterCount: Int { loaded?.chapters.count ?? 0 }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    ForEach(Array(verses.enumerated()), id: \.offset) { index, raw in
                        verseView(number: index + 1, raw: raw)
                            .id(index + 1)
                    }
                    chapterNavigation
                }
                .padding(20)
            }
            .onAppear {
                Prefs.setLastRead(book: bookFile, chapter: chapter)
                refreshHighlights()
                if let scrollTo, scrollTo > 1, scrollTo <= verses.count {
                    proxy.scrollTo(scrollTo, anchor: .top)
                }
            }
        }
        .background(Theme.page)
        .navigationTitle("\(bookName) \(chapter)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.toolbar, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button {
                    shareText = SharePayload(text: wholeChapterText())
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                Button {
                    showingTextSize = true
                } label: {
                    Image(systemName: "textformat.size")
                }
            }
        }
        .sheet(item: $shareText) { payload in
            ShareSheet(items: [payload.text])
        }
        .sheet(isPresented: $showingTextSize) {
            textSizeSlider
                .presentationDetents([.height(120)])
        }
    }

    // MARK: - Verses

    private func verseView(number: Int, raw: String) -> some View {
        let spans = RedLetter.spans(raw)
        return Text(verseNumberText(number) + attributed(spans: spans, verse: number))
            .font(.system(size: textSize))
            .lineSpacing(6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture {
                shareText = SharePayload(text: verseShareText(verse: number, raw: raw))
            }
            .contextMenu {
                Button {
                    toggleHighlight(verse: number, length: spans.plain.count)
                } label: {
                    Label(highlighted[number]?.isEmpty == false ? "Remove highlight" : "Highlight",
                          systemImage: "highlighter")
                }
                Button {
                    UIPasteboard.general.string = spans.plain
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
                Button {
                    shareText = SharePayload(text: verseShareText(verse: number, raw: raw))
                } label: {
                    Label("Share verse", systemImage: "square.and.arrow.up")
                }
            }
    }

    private func verseNumberText(_ number: Int) -> AttributedString {
        var text = AttributedString("\(number) ")
        text.foregroundColor = Theme.verseNumber
        text.font = .system(size: max(11, textSize * 0.62))
        return text
    }

    /// One pass over the verse that paints the words of Jesus and whatever of it
    /// is highlighted. Both sets of offsets count characters of the plain text.
    private func attributed(spans: (plain: String, red: [Range<Int>]), verse: Int) -> AttributedString {
        var text = AttributedString(spans.plain)
        let length = spans.plain.count

        if Prefs.wordsOfJesusStyle != .none {
            for range in spans.red {
                guard let bounds = indices(of: range, in: text, length: length) else { continue }
                switch Prefs.wordsOfJesusStyle {
                case .red:
                    text[bounds].foregroundColor = Theme.redLetter
                case .bold:
                    text[bounds].inlinePresentationIntent = .stronglyEmphasized
                case .none:
                    break
                }
            }
        }
        for range in highlighted[verse] ?? [] {
            guard let bounds = indices(of: range, in: text, length: length) else { continue }
            text[bounds].backgroundColor = Theme.highlight
        }
        return text
    }

    /// Clamps a stored range to the text actually on screen. A legacy
    /// whole-verse highlight arrives as 0..<Int.max and has to land somewhere.
    private func indices(of range: Range<Int>,
                         in text: AttributedString,
                         length: Int) -> Range<AttributedString.Index>? {
        let start = min(max(0, range.lowerBound), length)
        let end = min(max(start, range.upperBound == Highlights.wholeVerse ? length : range.upperBound),
                      length)
        guard start < end else { return nil }
        let lower = text.index(text.startIndex, offsetByCharacters: start)
        let upper = text.index(text.startIndex, offsetByCharacters: end)
        return lower..<upper
    }

    // MARK: - Actions

    private func toggleHighlight(verse: Int, length: Int) {
        let translation = bible.current.id
        if highlighted[verse]?.isEmpty == false {
            Highlights.remove(translation: translation, book: bookFile, chapter: chapter,
                              verse: verse, range: 0..<length, verseLength: length)
        } else {
            Highlights.add(translation: translation, book: bookFile, chapter: chapter,
                           verse: verse, range: 0..<length, verseLength: length)
        }
        refreshHighlights()
    }

    private func refreshHighlights() {
        highlighted = Highlights.ranges(translation: bible.current.id,
                                        book: bookFile,
                                        chapter: chapter)
    }

    /// The same shape the Android edition shares, crediting whichever
    /// translation is open.
    private func verseShareText(verse: Int, raw: String) -> String {
        "\u{201C}\(RedLetter.plain(raw))\u{201D}\n\(bookName) \(chapter):\(verse)"
            + bible.current.shareCredit
    }

    private func wholeChapterText() -> String {
        let body = verses.enumerated()
            .map { "\($0.offset + 1) \(RedLetter.plain($0.element))" }
            .joined(separator: "\n")
        return "\(bookName) \(chapter)\(bible.current.shareCredit)\n\n\(body)"
    }

    // MARK: - Chrome

    private var chapterNavigation: some View {
        HStack {
            if chapter > 1 {
                Button("Previous chapter") {
                    path.append(.reader(book: bookFile, chapter: chapter - 1, scrollTo: nil))
                }
            }
            Spacer()
            Text("Chapter \(chapter) of \(chapterCount)")
                .font(.footnote)
                .foregroundStyle(Theme.bookText.opacity(0.6))
            Spacer()
            if chapter < chapterCount {
                Button("Next chapter") {
                    path.append(.reader(book: bookFile, chapter: chapter + 1, scrollTo: nil))
                }
            }
        }
        .font(.callout.weight(.semibold))
        .padding(.top, 24)
    }

    private var textSizeSlider: some View {
        HStack(spacing: 12) {
            Text("A").font(.footnote)
            Slider(value: $textSize, in: Prefs.minTextSize...Prefs.maxTextSize, step: 1)
                .frame(width: 160)
                .onChange(of: textSize) { _ in Prefs.textSize = textSize }
            Text("A").font(.title2)
        }
        .padding(16)
    }
}

/// A shared string, wrapped so `.sheet(item:)` can key off it.
struct SharePayload: Identifiable {
    let text: String
    var id: String { text }
}

/// The system share sheet. SwiftUI's ShareLink cannot be triggered from a tap
/// on a run of text, so the reader presents this instead.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
