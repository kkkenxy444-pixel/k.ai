# k.ai — native Android companion

A Kotlin / Jetpack Compose application for independent AI characters, text chat, roleplay, and one-way AI voice notes. Indonesian is the primary interface language. Gemini is called directly with a key supplied by the person using the device; no account, Firebase, hosted backend, shared developer key, microphone, or WebView is used.

## Open and build

1. Extract this project and open **the `kai` folder** in Android Studio compatible with AGP 9.5 (Rabbit 2 or later). Do not create a replacement project.
2. Use a **full JDK 21** (JDK 17 is the AGP minimum; this project's verification uses 21). A JRE without `javac` cannot build Android code.
3. Install Android SDK Platform **37.0**, Build Tools **36.0.0**, and Platform Tools in SDK Manager. Accept the Android SDK licenses locally.
4. Android Studio creates `local.properties` with your SDK path. Alternatively set `ANDROID_HOME`. No API key belongs in any Gradle property, environment file, source file, or manifest.
5. On macOS/Linux run:

```sh
chmod +x gradlew
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

On Windows use `gradlew.bat`. The official Gradle 9.6.0 wrapper configuration is included with a pinned distribution SHA-256. Note: the binary `gradle/wrapper/gradle-wrapper.jar` is not stored in this repository; CI installs Gradle 9.6.0 directly via `gradle/actions/setup-gradle`, and locally you can run `gradle wrapper` once with Gradle 9.6.0 to regenerate the JAR (or invoke `gradle` directly). First build requires access to Google's Maven repository, Maven Central, and the Gradle distribution server. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

For command-line SDK setup, the current [official Android CLI](https://developer.android.com/tools/agents/android-cli) replaces the deprecated `sdkmanager` launcher. After installing it, run these commands (one package per invocation):

```sh
android --no-metrics --sdk="$ANDROID_HOME" sdk install 'platforms;android-37.0'
android --no-metrics --sdk="$ANDROID_HOME" sdk install 'build-tools;36.0.0'
android --no-metrics --sdk="$ANDROID_HOME" sdk install 'platform-tools'
```

For the device tests, use a fresh emulator/test device with API 26 or later:

```sh
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

The onboarding test expects a fresh installation. Do not run destructive test setup on a device containing valued personal data. See [TEST_RESULTS.md](docs/TEST_RESULTS.md) for evidence and the distinction between executed tests and tests that still need a device or a personal key.

## First use

1. Complete onboarding. Open **Pengaturan → Gemini API** and paste your personal key from [Google AI Studio](https://aistudio.google.com/apikey).
2. Save the key, test the connection, and refresh the catalog. A successful catalog request validates authentication, not model generation entitlement or Free Tier availability.
3. Choose independent default models for Normal Chat, Roleplay, and TTS. No model is silently selected or replaced. Existing sessions retain their own selections.
4. Create a chat character, or a roleplay world with named actors. Save the session. Each actor/character has independent personality and voice settings.
5. In Voice Studio choose a real voice, a compatible TTS model, delivery controls, and a custom test sentence. Generate and compare previews A/B. Save the profile and explicitly copy it into a session or actor.
6. Type a message. Use **Ubah ke VN** on an AI reply, or enable automatic VN in session settings. Audio generation and autoplay are separate switches.

No live response or preview is simulated. Requests require internet, a valid key, an accessible compatible model, and sufficient provider quota. Free access is never inferred from the fact that a model appears in the catalog.

## Implemented product flows

- Onboarding without a key; encrypted key save/test/replace/delete; link to AI Studio.
- Paginated Models API discovery, persistent catalog, search, capability hints, independently selected chat/roleplay/TTS models, and honest unknown pricing/entitlement labels.
- Unlimited local sessions within available storage; create/edit/rename/duplicate/delete; stable UUIDs; reusable character templates; **copy-on-create** session independence.
- Personality Studio with 12 interpreted controls, built-in presets, custom descriptions/rules and reusable locally saved presets.
- Genuine Gemini text generation with SSE streaming or a user-selected non-streaming mode. Automatic local persistence, interruption recovery, cancellation, copy, edit, delete, regenerate, retry and accessible message menus.
- Distinct roleplay reading style, world/scenario/rules/user role/relationships, multiple AI actors with individual backstories/personality/voices, narration/action/dialogue parsing, current-story notes and compact summaries.
- Voice Studio with 30 documented built-in voices, optional authenticated extended catalog, real saved voice IDs, 11 vocal-delivery controls, custom style instructions, local preset CRUD, real preview generation and A/B playback.
- Text-described voice creation through the documented Voices API for explicitly supported model families, after catalog access succeeds. This creates a resource in the user's Google project. Provider entitlement is still checked by the server; unavailable access is surfaced clearly.
- Manual and automatic VN; regeneration independent of text; preserved text and previous audio on TTS failure; native compatible two-speaker TTS with per-character sequential fallback; local WAV cache and metadata.
- Single Media3 player with audio focus, play/pause, duration/progress/seek, speed, replay, stop, next/previous segment and background pause. Audio never overlaps.
- Room persistence, DataStore settings, theme selection (dark/light/system), per-session memory management, JSON session export/import without credential fields or audio paths, explicit cache cleanup.

## Project map

| Location | Responsibility |
|---|---|
| `core/model` | Serializable domain models, stable IDs, traits and local presets |
| `core/ai` | Personality/delivery compilers, roleplay segmentation and context planning |
| `core/network` | Gemini HTTP/SSE protocols, pagination, response parsing and classified failures |
| `core/audio` | PCM-to-WAV encoding and sentence-aware speech splitting |
| `app/data` | Room schema/DAO/repository and DataStore preferences |
| `app/security` | AES-GCM credential encryption using Android Keystore |
| `app/feature/chat` | Request orchestration and normal/roleplay conversation UI |
| `app/feature/session` | Character/session/scenario forms and Personality Studio |
| `app/feature/voice` | Voice Studio, preset library and native player controls |
| `app/feature/settings` | Onboarding, BYOK and dynamic model selection |
| `app/feature/memory` | Summary and pinned-story-note management |
| `app/ui` | Design system, shared components, navigation and ViewModel |
| `core/src/test` | Offline API fixtures and business/protocol unit tests |
| `app/src/androidTest` | Room, Keystore, lifecycle-recovery and onboarding tests |
| `docs` | Architecture, API, voice, security, verification and handoff notes |

## Documents

- [Architecture and data ownership](docs/ARCHITECTURE.md)
- [Gemini API integration](docs/API_INTEGRATION.md)
- [Voice integration and limitations](docs/VOICE.md)
- [Security and privacy](docs/SECURITY.md)
- [Pinned tooling and dependency evidence](docs/DEPENDENCIES.md)
- [Acceptance tests and executed results](docs/TEST_RESULTS.md)
- [Next-engineer handoff / APK distribution](docs/HANDOFF.md)

Application ID, version name and version code are centralized in `gradle.properties`. The source namespace is `com.kai.app`. Changing the release application ID does not require changing Kotlin packages; migrating an already-installed app's ID is a separate data-migration decision.

## Practical limits

The implementation does not promise perfect long-term memory, exact pitch control, guaranteed free models, or consistent voice output across model revisions. It does not bypass content policies. Preview quality, account entitlement, device audio, installation and accessibility require the remaining manual/device verification recorded in the test report. A backend proxy is advisable before broader distribution; Keystore protects storage but cannot make a credential secret from a compromised client at runtime.