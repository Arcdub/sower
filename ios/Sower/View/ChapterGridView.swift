import SwiftUI

/// Chapter numbers in rings of gold. A book of 150 chapters has to stay calm,
/// so the ring is thin and the fill only arrives on the one being pressed.
struct ChapterGridView: View {
    let book: Book
    @Binding var path: [Route]

    private let columns = [GridItem(.adaptive(minimum: 58), spacing: 14)]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 14) {
                ForEach(1...max(1, book.chapters), id: \.self) { chapter in
                    Button {
                        path.append(.reader(book: book.file, chapter: chapter, scrollTo: nil))
                    } label: {
                        Text("\(chapter)")
                            .font(.body)
                            .foregroundStyle(Theme.bookText)
                            .frame(width: 52, height: 52)
                            .overlay(Circle().stroke(Theme.chapterRing, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
        }
        .background(Theme.page)
        .navigationTitle(book.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.toolbar, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }
}
