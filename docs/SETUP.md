# Developer Setup & Release Guide

## 1. Local Build Prerequisites
- **JDK:** 17 or 21
- **Android SDK:** Compile SDK 36, Build Tools 36.x, Platform Tools
- **Device:** Any Android 10+ (`API 29+`) ARM64 (`arm64-v8a`) smartphone or ARM64 emulator

## 2. Clone & Build
```bash
git clone https://github.com/Raj-Dev-Lab/Simple-Ai-Browser.git
cd Simple-Ai-Browser
./gradlew :app:assembleDebug
```

The debug APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## 3. Running Unit & Robolectric Tests
```bash
./gradlew :app:testDebugUnitTest
```

## 4. Release Signing (Production APK / AAB)
Never commit your release keystore (`my-upload-key.jks`) to Git. Supply signing credentials via environment variables in CI or locally:

```bash
export KEYSTORE_PATH="/path/to/my-upload-key.jks"
export STORE_PASSWORD="your-store-password"
export KEY_PASSWORD="your-key-password"
./gradlew :app:bundleRelease
```

## 5. GitHub Token & Security Best Practices
- Use fine-grained Personal Access Tokens scoped **only** to `Simple-Ai-Browser` (`Contents: Read and write`).
- Never paste tokens in public issues, READMEs, `.git/config`, or source files.
- Rotate exposed tokens immediately under **GitHub → Settings → Developer settings → Personal access tokens**.
- For semantic versioning releases:
  ```bash
  git tag -a v1.1.0 -m "Release v1.1.0: Core GeckoView 161 browser shell"
  git push origin main --tags
  ```
