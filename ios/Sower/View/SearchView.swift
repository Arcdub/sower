import SwiftUI

/// Plain substring search over the whole Bible, folded for case and accents.
/// The chip narrows it to highlighted verses, which with an empty query is just
/// a list of everything marked so far.
struct SearchView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var bible: BibleStore

    @State private var query = ""
    @State private var highlightsOnly = false
    @State private var results: [SearchResult] = []
    @State private var status = "Type a word or phrase, then press search."
    @State private var searching = false

    private let limit = 300

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Toggle("Highlights only", isOn: $highlightsOnly)
                .toggleStyle(.button)
                .tint(Theme.gold)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .onChange(of: highlightsOnly) { _ in run() }

            if searching {
                ProgressView().padding(.horizontal, 20)
            }

            if results.isEmpty {
                Text(status)
                    .font(.callout)
                    .foregroundStyle(Theme.bookText.opacity(0.7))
                    .padding(.horizontal, 20)
                Spacer()
            } else {
                List(results) { result in
                    Button {
                        path.append(.reader(book: result.bookFile,
                                            chapter: result.chapter,
                                            scrollTo: result.verse))
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(result.reference)
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Theme.green)
                            Text(RedLetter.plain(result.text))
                                .font(.callout)
                                .foregroundStyle(Theme.bookText)
                        }
                    }
                    .listRowBackground(Theme.page)
                }
                .listStyle(.plain)
                .scrollDismissesKeyboard(.immediately)
            }
        }
        .background(Theme.page)
        .searchable(text: $query, prompt: "Search the Bible")
        .onSubmit(of: .search) { run() }
        .navigationTitle("Search")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.toolbar, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }

    private func run() {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        // With the chip on, an empty query lists every highlight; otherwise a
        // single letter would match most of the Bible, so ask for two.
        if !highlightsOnly && trimmed.count < 2 {
            results = []
            status = "Type at least two characters."
            return
        }

        searching = true
        status = "Searching\u{2026}"
        // The store is main-actor isolated, so this does not leave the main
        // thread; the Task only lets the status line paint before the first
        // search builds the folded corpus.
        Task {
            let found = highlightsOnly
                ? bible.searchHighlights(trimmed, limit: limit)
                : bible.search(trimmed, limit: limit)
            results = found
            searching = false
            if found.isEmpty {
                status = highlightsOnly ? "No highlighted verses yet." : "No verses found."
            } else if found.count >= limit {
                status = "Showing the first \(limit) matches. Try a more specific search."
            } else {
                status = ""
            }
        }
    }
}
