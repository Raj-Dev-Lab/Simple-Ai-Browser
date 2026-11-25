## Summary
Briefly describe what this PR adds or fixes.

## Checklist
- [ ] Preserves engine boundary (`core/browser/` and `ui/` have zero GeckoView/WebView imports)
- [ ] Tested on Android 10+ (`minSdk 29`)
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` passes cleanly
- [ ] Updated `CHANGELOG.md` if user-facing behavior changed
- [ ] No secrets, keystores, or personal tokens committed
