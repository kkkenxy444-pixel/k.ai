# Architecture and invariants

## Modules and dependency direction

`:app` depends on the Android-free `:core` JVM library. Core has no Android dependency and can be tested with JUnit and MockWebServer. Feature packages keep Compose screens separate from Gemini protocol code, Room, audio encoding and cryptography. `AppGraph` is a small explicit dependency-injection composition root; no DI code generator is required.

`KaiViewModel` exposes lifecycle-collected `StateFlow`s and handles user commands. `ChatEngine` owns active network jobs in an application-scoped `SupervisorJob`, so navigation/configuration changes do not duplicate generation. Explicit stop/delete cancels the corresponding calls. If Android kills the process, startup marks unfinished rows `INTERRUPTED` and unfinished audio as retryable. There is no unattended foreground/background service and no automatic replay of interrupted paid/quota-bearing requests.

## Identity and ownership

- Every session and character uses an independent UUID. Names are display data, never identifiers.
- `characters` stores per-session character snapshots. A session refers to exactly its own character row.
- Template creation and starting from a template deep-copy IDs, personality settings, and voice profile snapshots. Templates do not remain live-linked to sessions.
- Roleplay actor snapshots live in the session's versioned serializable scenario. Copying a scenario remaps actor IDs and its selected-speaker reference.
- Voice presets are reusable library entries. Applying one copies it into the character/actor. Preset edits and deletion do not retroactively modify sessions.
- Duplicating a session copies text and memory with new session, message, character and actor IDs. It omits audio to avoid sharing cache ownership; VN can be regenerated.

## Persistence

Room v1 contains `sessions`, `characters`, `messages`, `templates`, `voice_profiles`, `personality_presets`, and `models`. Session rows include scenario JSON, compact summary with a coverage ordinal, pinned story-event JSON, user draft and settings. Message rows store status, text, errors and audio-clip metadata. Foreign keys cascade only messages when their session is deleted. Character deletion is explicit after the session is removed. Message order uses a monotonically allocated ordinal per session.

The initial Room schema is exported by KSP to `app/schemas`. There is deliberately no `fallbackToDestructiveMigration`. Future releases must increment the database version, add an explicit migration, and test upgrades from every supported version. JSON fields use additive defaults and `ignoreUnknownKeys`; removals/renames require a semantic migration too.

Settings use Preferences DataStore. Only non-secret settings go there. The encrypted credential uses a separate atomic file under `noBackupFilesDir`, protected by a non-exportable AES key in Android Keystore. Audio also lives in app-private storage. Files are written to a temporary path then renamed. TTS metadata is committed only after all required audio segments succeed. On failure partial new files are removed and the prior VN remains intact.

Cleanup removes unreferenced files after a ten-minute grace period so in-flight requests are not deleted. User-requested message/session deletion removes its referenced audio immediately. A global cache-clear command cancels generation before clearing metadata and files. Preview A/B files are disposable, with stale-preview cleanup on startup.

## Conversation and branch editing

The repository saves the user message before generation. Streaming deltas checkpoint at most about five times per second; the final text/status always commits. Only one text operation can run per session, and at most one TTS job per message. An interrupted stream retains its partial response.

Editing a message or regenerating an older response is an explicit destructive branch operation with a confirmation: later messages are removed. The existing summary is invalidated, because it may contain facts from the old branch. Deleting a single message also invalidates the summary. Pinned notes are user-authored and remain until explicitly removed.

The Compose list loads the most recent 100 messages and supports incremental loading of older messages. Older saved history is not re-read for every streaming delta. Stable row keys retain scroll identity. Expensive network work is initiated by commands, never by recomposition.

## Context and story continuity

The prompt compiler interprets personality controls as behavioral instructions, with a neutral band and a bounded set of dominant traits. It does not send meaningless slider numbers. Application safety/security instructions precede user-defined identity, story and memory data. No tools, secrets or filesystem operations are exposed to the model.

Un-summarized completed messages remain available until a summary successfully covers them. Automatic compaction normally starts after 40 messages or 48,000 characters and retains at least 24 recent messages under the message-count threshold. For long text, it retains at least four recent messages and summarizes only complete older turns. If summary generation fails, the application reports failure and does not silently drop history. Summaries retain decisions, relationships, motivations, outstanding events and current scene; they are still probabilistic and user-correctable via pinned notes and source history. Disabling automatic summary causes an explicit prompt to summarize manually when needed.

Roleplay uses line markers `[Narasi]`, `[Aksi: Nama]` and `[Nama]`. They permit different rendering and deterministic audio selection. Unknown/unmarked text is treated as narration; it is never silently assigned a known actor's dialogue voice. Narration, dialogue-only and one-character speech scopes are separate settings.

## UI and lifecycle

Material 3, restrained sage accents, readable typography, shared spacing, keyboard/inset handling, and dark/light/system themes form the design system. Navigation/draft editor state is retained via SavedStateHandle; API-key entry is deliberately excluded from saved state. The entire activity uses `FLAG_SECURE`. A single Media3 player manages focus and avoids overlapping audio. Playback pauses on activity stop and on noisy-output changes; TTS jobs may complete in-process but never restart themselves after process death.
