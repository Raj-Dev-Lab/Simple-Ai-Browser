# AGENTS.md — rules for AI coding agents and contributors

Read this file first in every session. The owner of Simple AI Browser (SAB), Tamal Mitra, is not a
programmer: explain results in plain language and never report something as done that you did not
build and test.

## Project memory (continuity)
- The complete project memory — feature inventory, specs, decisions, verified GeckoView API facts,
  open bugs and the roadmap — is **private**. It lives in the GitHub **draft release** named
  "SAB Project Memory (PRIVATE - never publish)" of this repository (asset `PROJECT-MEMORY.md`).
  Draft releases are visible only to people with write access. **Never publish that draft and never
  copy its contents into the public repository.**
- Read it at the start of a task and update it at the end of every task (append/change status,
  never delete older facts). If it is missing, ask the owner — do not invent its contents.

## Non-negotiable rules
1. **Never delete, simplify or weaken an existing feature.** Read the callers of anything you change.
2. **Real code only.** No mocks, stubs, TODOs, fake data or unverified APIs. Verify every GeckoView /
   Android API against the real jar (`javap`) before using it. If something cannot be built or
   tested, say so instead of faking it.
3. **Signing keys:** do not change the `signingConfigs` block in `app/build.gradle.kts`.
   `debug.keystore` in the repo root is committed **on purpose** (one shared debug key). The release
   key is private and is only ever provided through `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`
   or the GitHub Actions secrets — see `docs/KEYSTORE.md`.
4. **Never commit** tokens, passwords, `*.jks`, `.env`, or the private memory.
5. **Verify before you push:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`. With a device
   or emulator also run `./gradlew :app:connectedDebugAndroidTest`.
6. **Versions:** raise `versionCode` and `versionName` in `app/build.gradle.kts`, add a
   `## [x.y.z]` section to `CHANGELOG.md`, then push tag `vx.y.z`. CI refuses a tag that does not
   match. CI must stay green: steps that depend on unavailable infrastructure are non-blocking.
7. **Push** after each finished task, with a 20–30 second pause between pushes.
8. APKs are published as `sab-<debug|release>-arm64-v8a.apk`, `sab-<…>-x86_64.apk` and the universal
   `sab-<…>-arm64-x86_64.apk` (produced in `app/build/outputs/sab/<variant>/`).

## End-of-task report (mandatory)
`Honest Completion: N%` · `Actually Built & Working` · `Not Done / Shortcuts Taken` ·
`How You Can Test It`.
