# IzzyOnDroid inclusion request

IzzyOnDroid mirrors the APKs published on GitHub rather than building them, so
it is a much shorter road than the main F-Droid repository, and the two can run
side by side.

**Inclusion requests are no longer taken on GitLab.** The GitLab issue template
now redirects to Codeberg. Open the form here, signed in as Arcdub:

https://codeberg.org/IzzyOnDroid/repodata/issues/new?template=.forgejo%2Fissue_template%2Fapp-inclusion-request.yaml

It is a structured form, so the answers below are per field rather than one
block of prose.

## Checked against their policy

Nothing in the
[App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/)
blocks Sower: MIT licensed with the source public, no proprietary components,
no trackers or analytics of any kind, no cleartext traffic (no network at all),
an end user app rather than a library, a unique package name, not a fork, and
fastlane metadata in the repository.

## Field by field

**Title:** `[AppRequest] Sower`

**Guidelines checkboxes:** tick all four. You are the developer, the app meets
the policy, it is not already listed, and the fastlane folder is present.

**Link to the source code:** `https://github.com/Arcdub/sower`

**Link to app in another app store:** leave empty. Nothing is published
elsewhere yet; the F-Droid merge request is mentioned under Further Notices.

**License used:** `MIT`

**Categories:** `Reading` and `Religion`

**Summary:**

> An offline Bible that asks for no permissions, makes no network connections,
> and can pass itself from one phone to the next.

**Description:**

> Sower carries the complete 66 book Bible inside the APK. It requests zero
> permissions and opens no network connections of any kind, so nothing it is
> used for can be collected, logged, or sent anywhere, and it works the same
> with the SIM removed.
>
> It is built to travel. The Pass it on screen hands the app itself to another
> phone through any chat app, or over Bluetooth or Quick Share where there is
> no connection at all, so one copy can reach a village without anyone needing
> a data plan or an app store account. Reading, search, highlights, adjustable
> text size, and the words of Jesus in red (or in bold, for anyone who cannot
> easily tell red from black) all work offline.
>
> The English edition bundles the World English Bible and the Berean Standard
> Bible, both public domain. Eight further editions exist as separate packages,
> one translation each, in Spanish, French, Portuguese, Russian, Arabic, Hindi,
> Kiswahili, and Chinese.

**Build instructions:**

> ```
> git clone https://github.com/Arcdub/sower.git
> cd sower
> ./gradlew assembleEnRelease
> ```
>
> Output: `app/build/outputs/apk/en/release/app-en-release.apk`. A plain Gradle
> Android build with no non-free build steps and no extra tooling. Java 17,
> compileSdk 37, minSdk 21. The nine language editions are product flavours, so
> `assembleRelease` builds them all; `en` is the one being requested here.

**AI assistance level:** see the note below before answering.

**AI tool(s):** `Claude`

**What did the tools help with, and how?**

> Claude wrote most of the implementation against my direction and review:
> the reader and its red letter rendering, the highlight storage, search, the
> asset conversion script, and the UI layouts. I set the goals, decided the
> design, tested every build on device, and reviewed and edited the output
> before it shipped.

**AI accountability:** tick both boxes if they are true of how you worked. They
were true for this project: everything was reviewed, and every release was run
on a device before it went out.

**Further Notices:**

> A merge request for the main F-Droid repository is also open
> (fdroiddata !46150, opened 18 Aug). It is unresolved for a reason unrelated
> to the app: the merge request pipeline cannot run on a new GitLab account
> without identity verification, which the contribution template says not to
> submit, so all four pipeline runs ended in `error` rather than failing a job.
> This request does not depend on that one.
>
> Releases are signed with a single key that does not change between versions.
> Its SHA-256 is recorded in `fdroid/arcsky.steph.sower.yml` in the repository.
>
> Each release carries one APK per edition. The one to track for this request is
> `Sower-<version>-en.apk`, for example `Sower-1.0-en.apk`. The same release also
> carries an unversioned `Sower-en.apk` so that QR codes already printed keep
> working; the versioned filename is the one to follow.

## One thing to decide before you submit

The form asks how extensively AI tools were used, on a five point scale from
None to Dominant. Answering honestly means **Dominant**, since most of the code
was written by Claude, even though you directed, reviewed, and tested all of it.
Understating it would be a false statement to a repository that is explicitly
asking, and it is the kind of thing that is worse when discovered later than
when declared. The accountability checkboxes directly below it exist to carry
exactly the nuance that applies here.
