# Contributing to Simple AI Browser (SAB)

Thank you for your interest in contributing to **Simple AI Browser (SAB)**!

## Engineering Principles

1. **Preserve the Engine Boundary**: Never import `org.mozilla.geckoview.*` or `android.webkit.*` inside `core/browser/` or `ui/`. All engine interactions must pass through `BrowserEngine` and `BrowserTabSession`.
2. **GeckoView First**: Keep `org.mozilla.geckoview:geckoview` (official omni artifact, 161+) as the primary rendering engine for Android 10+ `arm64-v8a` and `x86_64`.
3. **Privacy by Default**: Never add third-party telemetry, analytics SDKs, or unencrypted secret storage.
4. **Conventional Commits**: Format commit messages using Conventional Commits:
   - `feat(tabs): add drag-to-group tab island gesture`
   - `fix(gecko): handle custom scheme redirects safely`
   - `docs(arch): document sab-bridge native messaging protocol`
   - `chore(ci): update Gradle cache configuration`

## Workflow

1. Fork the repository and create a feature branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Build and run tests locally:
   ```bash
   ./gradlew :app:assembleDebug :app:testDebugUnitTest
   ```
3. Open a Pull Request using the PR template and link any relevant issue.

## Code Style
- Kotlin official code style with Jetpack Compose Material 3.
- Keep composables stateless and hoist state into `BrowserViewModel`.
- Include `contentDescription` and `testTag` modifiers on interactive controls.
