# Build & Release

## Requirements

- JDK 17
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
- The Gradle wrapper is included, so no separate Gradle installation is needed

## Build the debug APK

```bash
cd android
./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

To sign a local build with the project keystore (same certificate as the published APKs):

```bash
cd android
EINKMEMO_DEBUG_KEYSTORE=/path/to/keystore.jks ./gradlew assembleDebug
```

Without `EINKMEMO_DEBUG_KEYSTORE`, Gradle uses the default `~/.android/debug.keystore`, which has a different certificate.

## Versioning

EinkMemo follows semantic versioning:

- `versionName` = `X.Y.Z` (no `v` prefix)
- git tag and GitHub Release = `vX.Y.Z`
- `versionCode` = `X*10000 + Y*100 + Z` (monotonically increasing)

Update `versionCode` / `versionName` in `android/app/build.gradle.kts` and add release notes at `docs/releases/vX.Y.Z.md`.

## Release

Push a `vX.Y.Z` tag; the workflow in `.github/workflows/release.yml` builds the APK and creates the GitHub Release:

```bash
git tag -a v0.11.0 -m "EinkMemo v0.11.0"
git push origin v0.11.0
```

The workflow uploads `EinkMemo-<version>-debug.apk`. Release notes are read from `docs/releases/vX.Y.Z.md` when present, otherwise generated from commits.

Manual release (after building locally):

```bash
mkdir -p dist
cp android/app/build/outputs/apk/debug/app-debug.apk dist/EinkMemo-0.11.0-debug.apk
gh release create v0.11.0 dist/EinkMemo-0.11.0-debug.apk \
  --title "EinkMemo v0.11.0" --notes-file docs/releases/v0.11.0.md
```

`dist/` is git-ignored; APKs are distributed only through GitHub Releases.

## Signing

- Published APKs are signed with the project debug certificate (SHA-256 `b337bcc8…bd12c2`), stored as the repository secret `ANDROID_DEBUG_KEYSTORE_BASE64`.
- Keep this keystore stable: changing it breaks upgrades for existing installs (users would have to uninstall and reinstall).
- `applicationId` is `com.einkmemo.app`. Earlier builds used `com.elinkmemo.app`, so they install as a separate app: users migrating from an older build should sync to WebDAV in the old app first, install EinkMemo, then configure WebDAV and sync to restore notes.

## Stack

- Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00), AGP 8.5.2
- OkHttp 4.12.0
- compileSdk 34 / targetSdk 30 / minSdk 26