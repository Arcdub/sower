# IzzyOnDroid inclusion request

IzzyOnDroid mirrors the APKs published on GitHub rather than building them, so
it is a much shorter road than the main F-Droid repository and the two can run
side by side. This file holds the text to post, so it does not have to be
rewritten each time.

Open a new issue at https://gitlab.com/IzzyOnDroid/repo/-/issues/new and choose
the inclusion request template, then paste the body below. Posting it requires
being signed in to GitLab as Arcdub.

## What the repository already satisfies

- MIT licensed, full source at https://github.com/Arcdub/sower
- Signed release APKs attached to a tagged GitHub release, one per edition
- `fastlane/metadata/android/en-US/` with title, descriptions, icon, four
  screenshots, and a changelog
- Zero `uses-permission` entries in the manifest, so there is nothing to scan
- No third party SDKs at all, so no trackers and no proprietary dependencies

## Body to paste

**App name:** Sower

**Source code:** https://github.com/Arcdub/sower

**Releases:** https://github.com/Arcdub/sower/releases

**License:** MIT

**Package ID:** `arcsky.steph.sower`

**APK to track:** `Sower-<version>-en.apk` from each release, for example
`Sower-1.0-en.apk`. The same release also carries an unversioned `Sower-en.apk`
so that QR codes already printed keep working; the versioned filename is the one
to follow.

Sower is an offline Bible. The complete text ships inside the APK, the manifest
declares no permissions at all, and the app makes no network connections of any
kind, so nothing can be collected or sent anywhere. It is built to be passed
from phone to phone, which matters where data is expensive or absent.

There are no third party SDKs, no analytics, no advertising, and no proprietary
dependencies. Only AndroidX and Material Components are used. The build is a
plain Gradle Android build with no non-free build steps.

Fastlane metadata is at `fastlane/metadata/android/en-US/` with a title, short
and full descriptions, an icon, four phone screenshots, and a changelog per
version code.

Releases are signed with a single key that does not change between versions. Its
SHA-256 is recorded in `fdroid/arcsky.steph.sower.yml` in the repository.

One note on the other files in a release: the app ships nine language editions,
each a Gradle product flavour with its own translation bundled and its own
package ID (`arcsky.steph.sower.es`, `.fr`, `.pt`, `.ru`, `.ar`, `.hi`, `.sw`,
`.zh`). Only the English edition is being submitted here. If you would rather
list the others as well, say so and I will send the details; otherwise please
track `arcsky.steph.sower` alone.

A merge request for the main F-Droid repository is also open
(fdroiddata !46150). It is still under review and does not depend on this one.

Thank you for the work you put into the repository.
