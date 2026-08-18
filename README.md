# Sower

**The Word travels hand to hand.**

An offline Bible app designed to spread: it carries a complete Bible inside the APK and can
send *itself* to a nearby phone over Quick Share or Bluetooth — no internet, no account, no
app store. One phone can seed a whole village. Nine language editions, each named in its own
tongue: **Sower · Sembrador · Semeur · Semeador · Сеятель · الزارع · बोनेवाला · Mpanzi · 撒种者**.

## Features

- Complete 66-book Bible (World English Bible translation), fully offline — the app requests
  **zero permissions** and never touches the network.
- Red-letter edition: the words of Jesus render in red, driven by the `\wj` markup in the
  official USFM source (accurate to the sub-verse level).
- Verse of the day on the home screen (30 gospel-centered verses, rotating by day of year).
- Continue-reading card that remembers your last book and chapter.
- Reader with adjustable text size; tap a verse to share it as text, long-press to copy.
- Full-text search across all 31,098 verses (quote-mark agnostic); results jump to the verse,
  scrolled into view and highlighted.
- **Pass It On**: shares the app's own installed APK through the system share sheet so it can
  travel phone-to-phone completely offline.

## Languages

Sower ships as **one small APK per Bible language** (product flavors, installable side by
side) so every edition stays a quick Bluetooth/Quick Share transfer:

- `en` — World English Bible (red-letter, public domain)
- `es` — Reina-Valera 1909 (public domain)
- `fr` — Louis Segond 1910 (red-letter, public domain)
- `pt` — Bíblia Livre 2018 (CC BY 4.0)
- `ru` — Синодальный перевод / Synodal (public domain)
- `ar` — ترجمة فان دايك / Van Dyck (public domain)
- `hi` — Indian Revised Version 2017 (red-letter, CC BY-SA 4.0)
- `sw` — Unlocked Literal Bible Kiswahili (CC BY-SA 4.0)
- `zh` — 和合本 / Chinese Union Version 1919 (public domain)

Each non-`en` flavor uses an `applicationId` suffix (`.es`, `.fr`, …) so editions
install side by side.

The **UI translates itself automatically** to the phone's language (English, Spanish,
French, Portuguese, Russian, Chinese, Arabic, Hindi, Indonesian, Swahili) in every flavor,
and search is accent-insensitive ("corazon" finds "corazón").

**Adding a language**: download a freely-licensed USFM translation from
https://ebible.org/find/ , run `node tools/transform.js <usfmDir> app/src/<code>/assets/bible`
(book names come from the USFM's own `\h` headers), and add a flavor block in
`app/build.gradle` with an `applicationIdSuffix` and an `about_translation` resValue.

## Project layout

- `app/src/main/assets/bible/` — 66 per-book JSON files plus `index.json`, generated from the
  official WEB USFM distribution at https://ebible.org/Scriptures/engwebp_usfm.zip. Words of
  Jesus are embedded as U+0001/U+0002 sentinel spans in the verse strings (see
  `RedLetter.java`).
- `tools/transform.js` — regenerates those assets: download and extract the USFM zip, then
  `node transform.js <usfmDir> <path-to-assets/bible>`.
- Toolchain: Gradle 9.6.1, AGP 9.3.1, Java 17 sources, compileSdk 37, minSdk 21,
  targetSdk 36. AndroidX/Material versions are pinned to the last releases that
  support minSdk 21 (see gradle/libs.versions.toml) so the app runs on Android 5.0 (2014)
  and newer — including old hand-me-down phones and devices without Google services.

## Building

```
gradlew.bat assembleDebug
```

Release builds are signed with a keystore configured via a local `keystore.properties`
file (never committed — see `.gitignore`). Signed release APKs for every edition are
published on the GitHub Releases page.

## Roadmap ideas

- In-app Wi-Fi Direct / Nearby Connections beaming with a receiver flow (works even where
  Quick Share is unavailable; no Play Services needed with raw Wi-Fi Direct).
- Anonymous "generation counter" showing how many hands a copy has passed through.
- Additional public-domain translations (other languages) as downloadable or bundled modules.
- Audio scripture for oral cultures / low literacy, with picture-based navigation.
- Bookmarks and reading plans.
