# Verification and acceptance report

Verification date: **15 November 2026**. This report separates executed checks from device/live-service tests that have not been executed. A compiled feature or a successful fixture test is not a live Gemini acceptance result.

## Executed checks

| Check | Result | Evidence |
|---|---|---|
| Core JVM business/protocol suite | **32 passed, 0 failed, 0 skipped** | 16 DomainTest + 16 NetworkTest cases; JUnit XML in `verification/` |
| Debug APK build | **Passed** | `:app:assembleDebug`, Gradle exit 0 |
| Instrumentation APK compilation | **Passed** | `:app:assembleDebugAndroidTest`; this does not run its nine tests |
| Android lint | **Passed: no issues** | `:app:lintDebug`, `verification/lint-debug.txt` |
| Release build including R8 and resource shrinking | **Passed** | `:app:assembleRelease`; unsigned APK generated |
| Source safety invariants | **Passed** | `python3 scripts/check_source.py` |
| Built debug/release APK inspection | **Passed** | `scripts/check_apk.py`; reports in `verification/apk-*.json` |
| Physical device/emulator tests | **Not executed** | `adb devices` returned no devices; no configured emulator or `/dev/kvm` |
| Live Gemini authentication, chat, TTS or voice design | **Not executed** | No personal Gemini key supplied or used |
| GitHub Actions execution / release signing / distribution | **Not executed** | Workflow supplied for the next engineer; no remote repository publication |

Build command:

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease
```

Environment: full Temurin JDK 21.0.13+9, official Gradle 9.6.0, AGP 9.5.0, Kotlin 2.4.10, Android Platform 37.0 revision 2, Build Tools 36.0.0. The initial complete build ran through the matching official Gradle distribution; the supplied wrapper was checked separately against the same SHA-256-verified cached distribution. This environment could not download the distribution through the wrapper's Java network path, so official distribution bytes downloaded through the available transport were seeded into the wrapper cache. No wrapper source, checksum, TLS policy, dependency repository or SDK code was replaced to bypass the problem. A fresh external network/bootstrap on the recipient's machine is not certified by this cached check.

The source archive excludes SDKs, JDKs, machine-local Gradle caches, `local.properties`, signing material and generated APKs. Rebuild the supplied source directly; project regeneration is not needed. Debug and unsigned release outputs were approximately **17.2 MB** and **2.45 MB** respectively; exact sizes and hashes are in the inspection reports. These hashes identify the local inspected outputs, not a promise of bit-identical debug signing on another machine.

## What the automated suite establishes

- Independent character and voice copies, stable/remapped actor IDs, and isolation of changes at the domain-model level.
- Personality compilation interprets values, keeps the application rules ahead of persona data, and avoids raw percentage prompts. Every vocal control changes guidance outside its neutral band.
- Roleplay narration/action/dialogue parsing and selected-actor voice routing, including avoiding incorrect mapping of unknown names.
- Context remains available until a summary covers it; long messages can trigger compaction before the normal message-count threshold while preserving recent turns.
- Text splitting and PCM-to-WAV byte layout; correct explicit native two-speaker mappings and sequential fallback for incompatible configurations.
- Models API pagination and header authentication; genuine SSE parsing; no thought-part display; classified invalid-key, restricted-model, daily-quota and service failures; bounded retry behavior and server delays.
- GenerateContent and Interactions TTS payloads, returned audio decoding, and one-request compatible multi-speaker synthesis using a local test server.
- Invalid credential characters fail before HTTP construction without echoing the input. Raw provider error text is not exposed.
- No credential fields in the serialized archive model; the exported schema round-trips scenario and memory data.

The test HTTP server exists only in `core/src/test`. Runtime code uses Gemini's real HTTPS API and contains no fixture response fallback. Tests do not demonstrate that a particular Google project has model access, free entitlement or sufficient quota.

## Required acceptance scenarios

| ID | Scenario | Status and actual evidence |
|---|---|---|
| 01 | Fresh install without API key opens onboarding | **Unverified on device.** `OnboardingTest.opensWithoutAKeyAndShowsOnboarding` is implemented and compiled; no installation was executed. |
| 02 | Valid key authenticates and displays compatible models | **Partially automated.** Catalog pagination/header/capability fixture tests passed. Real authentication and UI require a personal key/device. |
| 03 | Replace key without losing sessions | **Unverified on device.** Isolated Keystore + Room instrumentation test is implemented and compiled; not executed. Credential storage is separate from the database. |
| 04 | Create Anya with affectionate personality | **Partially automated.** Domain preset/copy assertions passed. Character creation UI and Room flow have not run on a device. |
| 05 | Create Reina with assertive personality | **Partially automated.** Domain assertive-preset assertions passed. Device creation flow remains unverified. |
| 06 | Editing Reina leaves Anya unchanged | **Partially automated.** Domain independence test passed; persistence/reopen instrumentation test compiled but not run. |
| 07 | Separate conversation history | **Unverified on device.** Room history-isolation test compiled; no database instrumentation execution. |
| 08 | Change model without deleting history | **Unverified on device.** `replacingModelKeepsMessagesAndMemory` compiled; not run. |
| 09 | Fantasy scenario and plot memory persist | **Partially automated.** Scenario/memory JSON round-trip and roleplay parsing passed. Room duplication/reopen and live story continuity remain unverified. |
| 10 | Close/reopen preserves sessions and story | **Unverified on device.** Reopen and interrupted-state recovery tests compiled; process-death/device tests not run. |
| 11 | Generate voice preview from custom text | **Partially automated.** TTS payload/audio-decoding and WAV tests passed against fixtures. Real preview generation and listening not executed. |
| 12 | Slider changes affect a new preview | **Partially automated.** All voice controls affect compiled instructions in unit tests. Perceptual effects on live audio require A/B listening. |
| 13 | Ubah ke VN and play generated audio | **Partially automated.** Network/audio format paths passed unit tests. Button-to-player flow and speaker output unverified on device. |
| 14 | Regenerate VN without regenerating text | **Implemented, not end-to-end verified.** Separate message-audio operation preserves text in code; live/app orchestration still requires device testing. |
| 15 | Automatic VN starts after text completion | **Implemented, not end-to-end verified.** Completion triggers the same TTS operation; autoplay is a separate flag. Device/live sequence not executed. |
| 16 | TTS unavailable or free quota exhausted | **Partially automated.** Restricted/model errors, daily quota, unknown rate limit, Retry-After and bounded 503 retry tests passed. Actual project entitlement/quota tests not executed. |
| 17 | No microphone recording or calling interface | **Static checks executed and passed.** Source scan and both merged APK manifests contain no microphone permission; no recording/STT/calling implementation. Visual device audit not performed. |
| 18 | No hardcoded Gemini key in source or APK | **Static checks executed and passed.** Source + uncompressed APK entries contain no Google API-key pattern; no real key was used. Test credentials are non-working fixture strings outside runtime source. |
| 19 | Build with documented Gradle toolchain | **Executed and passed.** Debug, instrumentation APK, lint, unit tests and unsigned R8 release built successfully. See build and wrapper logs. |

No row requiring a live service or physical audio has been marked fully passed on fixture evidence alone.

## Remaining release checks

Use clean API 26 and current Android emulators plus a physical device. Run the nine instrumentation tests, then the 19 scenarios above. Verify rotation and process death during streaming/TTS, background pause, audio focus/headset removal, seek/replay after completion, small screens, keyboard/gesture insets, large font and TalkBack. Check low storage and failed imports. Repeat with two independent sessions and different actor voices.

Use a personally owned free-tier project for the live checks. Do not put the credential in test source, Gradle properties or GitHub Actions. Observe actual project entitlement instead of inferring it from a model listing. Test both TTS transports where available, native two-speaker output, sequential mixed-model fallback, text fidelity and Indonesian pronunciation. Record account-independent findings without exporting the key or private conversation data.

The release APK is intentionally unsigned. A successful R8 build does not certify release startup; run the signed optimized build on a device before distribution. Subjective UI polish and acoustic quality still need human verification. See `HANDOFF.md`, `VOICE.md` and `SECURITY.md` for the implementation boundaries and distribution steps.
