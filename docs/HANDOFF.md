# Handoff to the next coding agent

## Start here

Open the supplied project directly. All runtime source, Gradle wrapper, configuration, offline tests, instrumentation tests and documentation are included. Do not regenerate the project or substitute a web implementation. First read `TEST_RESULTS.md`, then build the exact pinned toolchain. Preserve local data ownership semantics and add explicit migrations when changing persisted fields.

## Before merging or distributing

1. Run `./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease` with a full JDK and Android SDK 37.0.
2. Run `connectedDebugAndroidTest` on clean API 26 and modern gesture-navigation emulators. Test process death, keyboard/insets, rotation, small screens, large font, TalkBack, headset unplug, audio focus, background/foreground and low storage. The project report identifies what was not executed here.
3. Use your own test account/key on a physical device for TEST 02, 11–16 and other live-API acceptance items. Do not commit or put that key into CI. Confirm TTS availability and Free Tier/quota behavior for that particular project; do not infer access from the model name.
4. Compare real A/B previews with identical transcripts. Validate Indonesian pronunciation, text fidelity and the selected model's response to delivery controls. If a provider schema changes, update the protocol adapter and fixtures from official documentation.
5. Review code, error UX, localization/accessibility and visual polish. The app currently uses a compact explicit graph and a shared feature-facing ViewModel; these can be separated further without changing core/network/storage contracts.
6. Confirm the final application ID and versions in `gradle.properties`. Review icon/branding. Keep existing application ID and release signing key for upgrades.
7. Add verified release signing using protected secrets. Release builds deliberately have no signing configuration and must not fall back to embedding keystore passwords in Gradle source.

## GitHub workflow

An offline-compatible validation workflow is included in `.github/workflows/android.yml`. It performs only local tests/build/lint and uploads build reports/debug APK as workflow artifacts. It never calls Gemini, creates billing, publishes a release, or handles user API keys. The next engineer may adapt event triggers and pin actions to reviewed commit SHAs before enabling it in a repository.

For a signed release:

- Store the release keystore and passwords in protected GitHub Environment secrets (not repository files).
- Decode the keystore to a runner-temporary path; pass signing configuration through CI-only properties or a dedicated signing step.
- Run unit, lint and emulator acceptance gates before signing/distribution.
- Build `:app:assembleRelease`, verify with Android Build Tools `apksigner verify --verbose --print-certs`, and record the package/version/signing-certificate digest.
- Publish the signed APK only after an authorized release decision. Never include secrets or private chat exports in artifacts.
- Archive checksums, test reports and a changelog. Keep signing material recoverable through the organization's secret-management process.

## Important behavior to preserve

- Rotating/deleting the API key must not touch Room data. Quotas are project-level.
- A template or voice preset is copied into a session; edits cannot mutate another session.
- Model selection remains dynamic/manual; pricing/entitlement stays unknown unless actually verified.
- Text survives failed/denied TTS. Regenerate VN is independent of regenerate text.
- In-flight streams are not retried after output begins; process-death recovery does not resend automatically.
- Multi-character voice fallback keeps each actor's voice and speaking order. Do not silently assign narrator voice to unknown dialogue.
- No microphone, user VN, STT, calling, safety-bypass settings, logging of secrets or shared developer credential.
- No destructive Room migration fallback. Add schema migration tests when version 2 is introduced.

## Areas intentionally bounded

Direct BYOK client access is intended for personal/small-circle use. There is no production backend, account sync, push delivery or unattended background audio service. Native two-speaker synthesis is restricted to verified protocol families and compatible short transcripts; sequential per-speaker synthesis preserves other configurations. Custom voice design requires provider support and access. Future models may need protocol-capability registry changes. Automated fixtures exercise API shapes but cannot certify the subjective audio quality or an account's entitlement.
