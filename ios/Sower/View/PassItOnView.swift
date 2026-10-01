import CoreImage.CIFilterBuiltins
import SwiftUI

/// Passing the app on, iPhone style. iOS will not let an app hand its own
/// binary to another phone the way the Android edition does, so this shares the
/// website instead: the friend can read the Bible there straight away, or
/// install from the App Store link on the page.
struct PassItOnView: View {
    @State private var sharing: SharePayload?

    private static let siteURL = URL(string: "https://arcdub.github.io/sower/")!
    private var site: String { Self.siteURL.absoluteString }
    private let siteLabel = "arcdub.github.io/sower"

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    illustration

                    Text("This app carries the complete Bible: no internet, no account, no subscription. Pass it on and it keeps going, like a seed passed hand to hand.")
                        .font(.body)

                    Text("The quickest way")
                        .font(.title3.weight(.semibold))
                        .padding(.top, 10)

                    step(1, "Tap **Send this app** below, then pick any chat app: **WhatsApp**, **Telegram**, **Signal**, email, anything that can send a link.")
                    step(2, "Your friend opens the link and can start reading in their browser immediately, with nothing to install.")
                    step(3, "If they want it on their home screen, the page offers the app for their phone.")

                    Text("No internet at all?")
                        .font(.title3.weight(.semibold))
                        .padding(.top, 14)

                    Text("An iPhone cannot hand over an app on its own, the way an Android phone can over Bluetooth. If your friend is offline, open the Bible here and read it together, or share the verse you are on by tapping it.")
                        .font(.footnote)
                        .opacity(0.75)

                    Text("Or have them scan this with their phone camera:")
                        .font(.body)
                        .padding(.top, 8)

                    qrCode

                    Text(siteLabel)
                        .font(.callout.weight(.medium))
                        .foregroundStyle(Theme.gold)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .center)

                    Text("\u{201C}A farmer went out to sow his seed\u{2026} Other seed fell into the good ground and grew and produced one hundred times as much fruit.\u{201D} Luke 8:5,8")
                        .font(.footnote.italic())
                        .opacity(0.7)
                        .padding(.top, 10)

                    Text("World English Bible and Berean Standard Bible: public domain, freely shareable.")
                        .font(.caption)
                        .opacity(0.5)
                }
                .padding(20)
            }

            Button {
                sharing = SharePayload(text: site)
            } label: {
                Text("Send this app to another phone")
                    .font(.headline)
                    .foregroundStyle(Theme.filledButtonText)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Theme.filledButton)
                    .clipShape(RoundedRectangle(cornerRadius: 28))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 10)
        }
        .background(Theme.page)
        .foregroundStyle(Theme.bookText)
        .navigationTitle("Pass it on")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.toolbar, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .sheet(item: $sharing) { _ in
            // Sharing the URL rather than the string so Messages and the like
            // show a proper link preview.
            ShareSheet(items: [Self.siteURL])
        }
    }

    private var illustration: some View {
        HStack(spacing: 18) {
            phone(seed: true)
            Image(systemName: "arrow.right")
                .foregroundStyle(Theme.gold)
            phone(seed: false)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(Theme.verseCard)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private func phone(seed: Bool) -> some View {
        RoundedRectangle(cornerRadius: 10)
            .stroke(Theme.green, lineWidth: 4)
            .frame(width: 58, height: 92)
            .overlay {
                Image(systemName: seed ? "leaf.fill" : "circle.fill")
                    .foregroundStyle(seed ? Theme.green : Theme.gold)
                    .font(seed ? .title3 : .caption)
            }
    }

    private func step(_ number: Int, _ markdown: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.footnote.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 26, height: 26)
                .background(Theme.green)
                .clipShape(Circle())
            Text((try? AttributedString(markdown: markdown)) ?? AttributedString(markdown))
                .font(.callout)
        }
    }

    private var qrCode: some View {
        Group {
            if let image = Self.qr(for: site) {
                Image(uiImage: image)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: 220, height: 220)
                    .padding(14)
                    .background(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }

    /// Drawn on the phone rather than shipped as an image, so the address and
    /// the code can never drift apart.
    private static func qr(for string: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
