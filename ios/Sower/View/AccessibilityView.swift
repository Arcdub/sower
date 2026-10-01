import SwiftUI

/// How the words of Jesus are distinguished. Red is traditional, bold reads
/// the same way for someone who cannot tell the red from the black, and off
/// leaves the page plain.
struct AccessibilityView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var style = Prefs.wordsOfJesusStyle

    private let sample = "\u{0001}I am the good shepherd.\u{0002} The good shepherd lays down his life for the sheep."

    var body: some View {
        NavigationStack {
            Form {
                Section("Words of Jesus") {
                    Picker("Style", selection: $style) {
                        Text("Red").tag(RedLetter.Style.red)
                        Text("Bold").tag(RedLetter.Style.bold)
                        Text("Plain").tag(RedLetter.Style.none)
                    }
                    .pickerStyle(.segmented)

                    Text(RedLetter.styled(sample, style: style, color: Theme.redLetter))
                        .font(.body)
                        .padding(.vertical, 4)
                }
            }
            .navigationTitle("Accessibility")
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: style) { _ in Prefs.wordsOfJesusStyle = style }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
