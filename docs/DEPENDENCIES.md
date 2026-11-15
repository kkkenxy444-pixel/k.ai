# Pinned toolchain and dependencies

Versions are centralized in `gradle/libs.versions.toml`. There are no dynamic `+`, `latest.release`, alpha or RC application dependencies. The Compose BOM aligns Compose runtime libraries; Kotlin's Compose compiler plugin uses the same version as the Kotlin plugin. AGP 9 uses built-in Kotlin, so `org.jetbrains.kotlin.android` is deliberately not applied.

| Component | Pinned version | Purpose / source |
|---|---|---|
| Gradle | 9.6.0 | Official wrapper distribution, SHA-256 pinned |
| Android Gradle Plugin | 9.5.0 | [AGP compatibility notes](https://developer.android.com/build/releases/gradle-plugin) |
| Kotlin / Compose compiler / serialization plugin | 2.4.10 | [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) |
| KSP | 2.3.14 | [Official KSP releases](https://github.com/google/ksp/releases) |
| Android compile / target SDK | 37.0 / 37 | Platform SDK installed for build; minimum SDK 26 |
| JDK used for verification | 21 | Full JDK required; JVM bytecode target 17 |
| Compose BOM | 2026.09.00 | [Official BOM setup](https://developer.android.com/develop/ui/compose/bom) |
| Activity Compose | 1.11.0 | Native activity/Compose integration |
| Lifecycle Compose / ViewModel | 2.11.0 | Lifecycle-aware UI and saved state |
| Room | 2.8.5 | [Official Room release](https://developer.android.com/jetpack/androidx/releases/room) |
| DataStore | 1.3.0 | [Official DataStore release](https://developer.android.com/jetpack/androidx/releases/datastore) |
| Media3 ExoPlayer | 1.11.1 | [Official Media3 release](https://developer.android.com/jetpack/androidx/releases/media3) |
| Coroutines | 1.10.2 | Flows, structured concurrency and cancellation |
| kotlinx.serialization JSON | 1.9.0 | Typed local serialization, Gemini wire JSON |
| OkHttp | 4.12.0 | HTTP and SSE streaming without SDK/backend dependency |
| JUnit | 4.13.2 | JVM unit tests |
| AndroidX test runner / ext-junit | 1.7.0 / 1.3.0 | Instrumentation tests |

Documentation checked on 15 November 2026. Resolved dependency/build evidence belongs in `TEST_RESULTS.md`; documentation presence alone is not a successful build. `./gradlew :app:dependencies` can inspect the exact transitive tree. Before a release, the next engineer should review vulnerability/security updates and freeze dependency verification metadata in the intended CI environment.

The implementation uses Google/Maven Central artifacts and official Gradle distribution bytes. No third-party Maven mirror, unpublished binary dependency, Firebase SDK, web runtime, microphone SDK, DI framework or cloud backend is required.
