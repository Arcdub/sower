# fdroiddata !46150: what to do next

https://gitlab.com/fdroid/fdroiddata/-/merge_requests/46150

## Where it stands

On 15 Oct linsui rebased the branch onto master, which finally let CI run, and
then asked us to check the result. Two jobs failed, both now fixed here:

- **`fdroid rewritemeta`** failed on line endings alone. Every line of the
  recipe was replaced by an identical line differing only by CRLF. Fixed by
  `.gitattributes` pinning YAML to LF, since this repo uses
  `core.autocrlf=true` and a checkout was handing back CRLF.
- **`fdroid build`** failed with
  `Found usual suspect 'org.gradle.toolchains.foojay-resolver' at settings.gradle`.
  Android Studio adds that plugin to every new project; it downloads a JDK from
  an external service at build time, so the scanner rejects it. Nothing used
  it. Removed in `e14810d`.

The foojay plugin was present at the old pinned commit too, so 1.0 could never
have passed. Release **1.1** was cut for this, tagged at
`248aedd3f70773fd66d5658bafd11007226c5030`, with all eighteen assets published.

Checked before cutting it: the gradle files score zero against F-Droid's own
`suss.json` signature list, a clean `assembleRelease` of all nine editions
succeeds, the en APK runs on an emulator, the release URL resolves, and the
APK's signing certificate is
`8d784455ee265b5a00c1fc8b625eb02fe22754cfc157dc4c5accff725288c729`, matching
`AllowedAPKSigningKeys`.

## Step 1: update the recipe on the fork

Signed in as Arcdub, edit `metadata/arcsky.steph.sower.yml` on the
`new-app-sower` branch of `arcdub1/fdroiddata`. The GitLab web editor writes
LF, which avoids repeating the line ending problem. Replace the whole file
with the contents of `fdroid/arcsky.steph.sower.yml` in this repository, which
is:

```yaml
Categories:
  - Reading
License: MIT
AuthorName: Arcdub
SourceCode: https://github.com/Arcdub/sower
IssueTracker: https://github.com/Arcdub/sower/issues
Changelog: https://github.com/Arcdub/sower/releases

AutoName: Sower

RepoType: git
Repo: https://github.com/Arcdub/sower.git
Binaries: https://github.com/Arcdub/sower/releases/download/v%v/Sower-%v-en.apk

Builds:
  - versionName: '1.1'
    versionCode: 2
    commit: 248aedd3f70773fd66d5658bafd11007226c5030
    subdir: app
    gradle:
      - en

AllowedAPKSigningKeys: 8d784455ee265b5a00c1fc8b625eb02fe22754cfc157dc4c5accff725288c729

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: '1.1'
CurrentVersionCode: 2
```

The 1.0 build entry is gone on purpose. Keeping it would keep failing the
scanner forever, since the plugin is in that commit.

## Step 2: comment on the merge request

> Thanks for the rebase, that got CI running and showed both real problems.
> Both are fixed, and I have pushed an updated recipe.
>
> `fdroid build` was failing on
> `Found usual suspect 'org.gradle.toolchains.foojay-resolver' at settings.gradle`.
> That plugin comes from the Android Studio project template and nothing in the
> build used it, so I removed it. It was present at the old pinned commit as
> well, which means 1.0 could never have passed the scanner, so I cut 1.1 for
> this rather than trying to patch around it. The recipe now builds 1.1 at
> `248aedd3f70773fd66d5658bafd11007226c5030` and the 1.0 entry is dropped.
>
> `fdroid rewritemeta` was failing only on line endings: the recipe had been
> committed with CRLF, so every line came back as an identical replacement. The
> file is LF now, and the upstream repository pins YAML to LF so it cannot
> happen again.
>
> I checked the new commit against the `suss.json` signature list before
> releasing and it comes back clean, and the published APK's signing
> certificate still matches `AllowedAPKSigningKeys`.
>
> Could this go back in the review queue? The `waiting-on-response` label is
> now answered.
