import SwiftUI

@main
struct SowerApp: App {
    @StateObject private var bible = BibleStore()

    var body: some Scene {
        WindowGroup {
            BookListView()
                .environmentObject(bible)
                .tint(Theme.green)
        }
    }
}

/// Everywhere the app can navigate to. Kept in one place so the reader can push
/// another chapter without each screen knowing how the stack is built.
enum Route: Hashable {
    case chapters(Book)
    case reader(book: String, chapter: Int, scrollTo: Int?)
    case search
    case passItOn
}
