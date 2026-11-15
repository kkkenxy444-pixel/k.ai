# Security and privacy

## Credentials

The user enters their own Gemini key. There is no key in source, BuildConfig, manifests, Gradle properties, fixtures, exports or CI. Test credentials are unmistakable non-working strings. Key input is password-masked, excluded from SavedStateHandle, and protected by `FLAG_SECURE`. Keyboard autocorrection is disabled. The app does not copy the credential to the clipboard or use an HTTP logging interceptor.

`KeyVault` uses AES-256-GCM and a randomly generated IV for every write. Key material is generated inside Android Keystore with randomized encryption required. An atomic private file contains version byte, IV and ciphertext. Key-store failure sets an explicit recovery flag: the user can replace the credential without deleting chat data. The decrypted key exists in process memory while the app is running so calls can authenticate; Keystore cannot protect that memory from root/hooking/instrumentation or a compromised device.

Backups are disabled in the manifest, with explicit cloud and device-transfer exclusions. Credentials and audio are stored under `noBackupFilesDir`. Non-secret JSON export is opt-in via Android's document picker. Export schema contains session/character/scenario/history/memory only; local audio metadata is stripped. Users may still write sensitive material into their chat text, so exported conversation files must be treated as private.

## Network and provider

Requests go only to the fixed HTTPS Gemini endpoint in production. TLS validation remains enabled. There are no custom trust managers, insecure hostname verifiers, cleartext production requests, redirects forwarding headers, or runtime arbitrary endpoint configuration. API keys use headers, not query parameters. Errors are classified before presentation; raw server responses and credentials are not logged. No crash-reporting or analytics SDK is included.

Conversation text, relevant history/summary, persona/scenario data and TTS transcript must be sent to Google for the requested function. Provider handling depends on the user's project, tier and terms. The app does not claim that Free Tier traffic is private from the provider or that choosing BYOK changes Google's terms. Interactions TTS opts out of persistent conversation state with `store=false`; this is not a promise of zero provider retention.

The app never enables billing or silently changes to another model. It also cannot enforce a zero-cost cap if the user's Google project already has billing. Model discovery is not a pricing or quota API. Users must monitor their project and choose eligible models themselves. Voice design explicitly creates a stored resource in the user's Google project; local preset deletion leaves that resource for the user to manage in AI Studio.

## Local data and permissions

The app source declares INTERNET. The merged APK also includes ACCESS_NETWORK_STATE and WAKE_LOCK from Media3, plus AndroidX's app-scoped signature permission for non-exported receivers. No wake-lock mode or background playback service is enabled. No microphone, camera, contacts, location, phone, storage-wide access, STT, voice calls or user audio recording is present. Android's document picker grants scoped URI access for imports/exports. Chat data is app-private but is not separately encrypted with an application database passphrase. Normal Android sandbox/device encryption protects it on an uncompromised locked device. Deleting or uninstalling the app removes local data; no automatic cloud restore is provided.

Audio metadata uses UUID filenames, validated before access. The app does not trust imported audio paths or share local cache paths. Import creates new IDs and removes audio references. File size/message count limits bound import memory use. Generated files are atomically committed and orphan-cleaned. Model-supplied content is rendered as Compose text, not HTML or script, and never executed.

Persona and story rules are treated as user configuration, subordinate to application/provider safety instructions. No safety bypass is implemented. This is still prompt-based guidance, not a formal guarantee against every model instruction-following mistake; no privileged tools or secrets are included in model context.

## Before wider distribution

Personal BYOK use can tolerate direct client access with a clearly disclosed threat model. For broader/public deployment, introduce an authenticated backend proxy with per-user authorization, scoped credentials, rate limits, abuse protection, cost controls and server-side monitoring. Do not place a shared developer key into a signed APK. A proxy does not remove the need for user consent, provider-policy compliance, secure release signing and lifecycle/retention policies.

Release signing keys are a separate secret from Gemini credentials. Keep the signing keystore in protected CI secrets or a signing service. Preserve the signing key and application ID across updates so users keep their local data. Never publish a keystore or debug signing material as a project deliverable.
