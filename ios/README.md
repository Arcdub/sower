# Sower for iOS

The same Bible, the same reader, the same words in red. This is a SwiftUI port
of the Android app living in the same repository, reading the very same JSON
assets, so a change to the text ships to both.

## Build it

You need a Mac with Xcode 15 or later.

```bash
brew install xcodegen
cd ios
xcodegen generate
open Sower.xcodeproj
```

`Sower.xcodeproj` and `Sower/Support/Info.plist` are generated from
`project.yml` and are deliberately not committed, so a 20,000 line project file
never has to be merged. Regenerate after adding or removing a source file.

To run on a real device, open the target's Signing and Capabilities tab and
pick your team, or set `DEVELOPMENT_TEAM` in `project.yml`.

## Where the Bible comes from

Nothing is copied into this folder. `project.yml` points at the Android source
set:

- `../app/src/en/assets/bible` (World English Bible)
- `../app/src/en/assets/bible_bsb` (Berean Standard Bible)
- `../app/src/en/assets/translations.json`

They are folder references, so regenerating the assets with
`tools/transform.js` updates both apps at once, and the words of Jesus keep
arriving as the same U+0001 and U+0002 sentinel spans described in
`RedLetter.swift`.

Only the English edition is wired up. The other eight translations are separate
Android product flavours; on iOS they would be separate targets, each pointing
at its own `app/src/<flavor>/assets` folder, which is a small addition to
`project.yml` when the time comes.

## What is here

- Book list with the verse of the day and a continue reading card. The verse of
  the day is chosen by day of the year from the same thirty references the
  Android app uses, so both phones show the same verse on the same date.
- Chapter grid, reader with red letters, highlights, adjustable text size, and
  previous and next chapter navigation.
- Tap a verse to share it. The shared text is the same shape Android produces,
  crediting whichever translation is open, for example `(BSB)`.
- Search across the whole Bible, folded for case and accents, plus a highlights
  only mode. It reads highlight keys in the Android format, including the older
  whole-verse ones.
- Pass it on, which shares the website with a QR code drawn on the phone.

## Where it differs from Android, and why

- **Passing the app itself.** Android can hand its own APK to another phone over
  Bluetooth or Quick Share. iOS does not allow that, so Pass it on shares
  `arcdub.github.io/sower` instead: the person can read in the browser at once,
  or install from there. This is the one place the two apps genuinely cannot
  behave alike.
- **Highlighting is per verse, not per word.** Android lets you drag across part
  of a verse. Doing that in SwiftUI needs a TextKit backed `UITextView` rather
  than `Text`, so for now holding a verse highlights all of it. The storage
  format is already the character-range one, so word level highlighting can be
  added later without migrating anything, and ranges made on Android render
  correctly here.
- **One edition.** See above.

## Not yet built or run

This was written on a Windows machine with no Swift toolchain, so it has never
been compiled. Expect to fix a few things on the first build. The model layer
(`Bible`, `RedLetter`, `Highlights`, `Prefs`, `DailyVerse`) is a close
translation of the Java and is the part most worth trusting; the views are where
surprises will be.

The app icon in `Sower/Resources/Assets.xcassets` was rendered from the Android
launcher vector (`app/src/main/res/drawable/ic_launcher_foreground.xml`), cropped
to the square a launcher mask actually shows, so the two icons match.
