# Releasing Sower

**A pushed tag is never moved.** F-Droid pins each build to a full commit hash
and watches this repo's tags for updates, so re-pointing a tag would make the
published source disagree with the published binary and break their
verification. Every release gets a new tag and a higher `versionCode`.

## Cutting a release

```powershell
pwsh tools/release.ps1 -Version 1.1 -Notes "What changed in this release." -DryRun
pwsh tools/release.ps1 -Version 1.1 -Notes "What changed in this release."
```

The script refuses to reuse an existing tag, bumps `versionCode` and
`versionName` in `app/build.gradle`, writes the fastlane changelog F-Droid
shows as "What's New", builds all nine languages, commits, tags once, pushes,
and uploads two names per language:

- `Sower-<version>-<lang>.apk` — the versioned record.
- `Sower-<lang>.apk` — evergreen, so QR codes already in the wild keep
  resolving through `releases/latest/download/`.

## Updating F-Droid afterwards

`fdroid/arcsky.steph.sower.yml` mirrors the recipe in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). After a release, add a new
entry to `Builds:` with the new `versionName`, `versionCode`, and the full
commit hash the script prints, then raise `CurrentVersion` /
`CurrentVersionCode` to match. `AllowedAPKSigningKeys` stays as it is — that is
the fingerprint of `sower-release.jks`, and it is what lets F-Droid ship builds
under our signature so existing installs can update in place.

`AutoUpdateMode: Version` means F-Droid can pick up later tags on its own once
the first build is accepted.

## Signing

`sower-release.jks` and `keystore.properties` are gitignored and must never be
committed. Losing them means no existing install can ever be updated again;
keep a backup somewhere safe.
