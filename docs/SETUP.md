# Developer Setup & Release Guide

## 1. Prerequisites
- **JDK 21** (required by Android Gradle Plugin 9.1.1; JDK 17 is the minimum)
- **Android SDK:** platform `android-36.1`, build-tools `36.0.0`, platform-tools
- **Gradle:** nothing to install — the repository ships the Gradle 9.3.1 wrapper (`./gradlew`) with a verified SHA-256 checksum
- **Device:** Android 10+ (API 29+). Phones are `arm64-v8a`; emulators and most Chromebooks are `x86_64`. Both are built.

## 2. Clone & Build
```bash
git clone https://github.com/Raj-Dev-Lab/Simple-Ai-Browser.git
cd Simple-Ai-Browser
./gradlew :app:assembleDebug
```

The build produces three debug APKs of the same code in `app/build/outputs/sab/debug/` — pick the one for your CPU:

| File | For | Size (debug build) |
|---|---|---|
| `sab-debug-arm64-v8a.apk` | phones | ≈ 260 MiB |
| `sab-debug-x86_64.apk` | emulators, Chromebooks | ≈ 278 MiB |
| `sab-debug-arm64-x86_64.apk` | universal: contains both | ≈ 452 MiB |

The size comes from GeckoView's native engine (about 150–170 MB of native code per CPU architecture). Debug builds are not optimised; release APKs are smaller. All debug APKs are signed with the shared key that is committed in the repository, so a new debug build always updates an installed debug build in place. (Debug APKs from releases older than 2026-11-25 were signed with a random CI key — uninstall that old build once before installing a new one.)

## 3. Tests
```bash
./gradlew :app:testDebugUnitTest          # JUnit + Robolectric, no device needed
./gradlew :app:connectedDebugAndroidTest  # needs a phone or emulator connected via adb
```
GitHub Actions runs both automatically on every push (`.github/workflows/android-ci.yml`).

## 4. Release Signing (Production APK / AAB)
The release key is private and is **never** committed. The complete guide — where the key lives, how to give it to GitHub once, how to build a signed release on your own computer, and what to do in an emergency — is [docs/KEYSTORE.md](KEYSTORE.md).

Short version for a local build (the signing block in `app/build.gradle.kts` reads these three variables and must not be changed):

```bash
export KEYSTORE_PATH="/path/to/sab-release.jks"
read -rs STORE_PASSWORD && export STORE_PASSWORD
read -rs KEY_PASSWORD    && export KEY_PASSWORD
./gradlew :app:assembleRelease
```

Signed APKs appear in `app/build/outputs/sab/release/` (`sab-release-arm64-v8a.apk`, `sab-release-x86_64.apk`, `sab-release-arm64-x86_64.apk`). GitHub Actions builds them automatically as soon as the three repository secrets from `docs/KEYSTORE.md` exist.

## 5. GitHub Token & Security Best Practices
- Use fine-grained Personal Access Tokens scoped **only** to `Simple-Ai-Browser` with the fewest permissions that work.
- Never paste tokens in public issues, READMEs, `.git/config`, or source files.
- Rotate exposed tokens immediately under **GitHub → Settings → Developer settings → Personal access tokens**.

## 6. Releasing a Version
1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add a `## [x.y.z] - YYYY-MM-DD` section to `CHANGELOG.md`.
3. Commit, tag and push. CI checks that the tag matches `versionName`, that the changelog has the section and that `versionCode` grew; then it publishes the APKs:

```bash
git tag -a v1.2.1 -m "Release v1.2.1"
git push origin v1.2.1
```
