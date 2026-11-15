# Gemini integration

Official documentation was checked during implementation on **15 November 2026 (Asia/Jakarta)**. Model names are discovered at runtime, not frozen into a selection list.

| Purpose | Request |
|---|---|
| Catalog/authentication test | `GET /v1beta/models?pageSize=1000&pageToken=…` |
| Text reply / summary | `POST /v1beta/models/{id}:generateContent` |
| Streaming text | `POST /v1beta/models/{id}:streamGenerateContent?alt=sse` |
| Legacy compatible TTS | `POST /v1beta/models/{id}:generateContent` with audio response modality and prebuilt voice |
| New TTS protocol | `POST /v1beta/interactions` with audio response format, speech metadata and speech configuration |
| Extended/custom catalog | `GET /v1beta/voices?page_size=100&page_token=…` |
| Text-described voice design | `POST /v1beta/voices` with `type=prompted`, model, description and `store=true` |

All calls use HTTPS and the `x-goog-api-key` header. The API key never appears in a URL. Redirects are disabled so credentials cannot be forwarded to another host. The production host is fixed to Google's Generative Language API. Base URL injection exists for offline protocol tests only; there is no end-user arbitrary-server setting.

## Capability and cost handling

The Models API supplies names, descriptions, generation methods and token limits, but does not guarantee a user's pricing tier, quota or output-modality entitlement. The UI displays these limits honestly. Text filtering uses generation methods and metadata hints; TTS filtering uses name/description hints. “Tampilkan semua” keeps users in control if metadata is incomplete. An actual generation request validates the selected endpoint. A rejected model does not delete history, change model or activate billing.

The two explicitly documented voice-design/two-speaker model IDs in `core/ai/SpeechPlanner.kt` are a **capability registry**, not the model-selection source. Only returned models can be selected normally. New voice-design families need a documentation review before enabling that creation path. A successful Voices catalog check is a prerequisite, not a promise of design entitlement.

## Retry and error behavior

- Invalid/revoked credentials, HTTP 401, access restriction/403, unavailable model/404, content restrictions, offline/DNS, timeouts and temporary service failures have distinct Indonesian messages.
- Structured quota details distinguish identifiable daily limits and zero-quota access from other 429 responses. If the provider does not identify the quota type, the application does not invent one.
- `Retry-After` (seconds/date) and `google.rpc.RetryInfo.retryDelay` are respected. Rejected 429/503 requests have at most two retries after the first attempt. Long server delays are shown to the user instead of keeping an invisible job waiting indefinitely.
- A stream is never automatically retried after visible text has arrived. Mid-stream failure leaves the partial message and an explicit retry action.
- Transport I/O failures are not blindly retried: the server may already have processed the request.
- Voice creation is never automatically retried because it creates a persistent Google-project resource.
- Provider error text is classified and replaced before presentation; raw error bodies/keys are not logged or exported.

Replacing/deleting the key cancels active generation, changes only credential storage and clears stale model/voice discovery state. Conversations, character snapshots, voice presets and memories remain intact. Project-level quotas are not reset by rotating keys within the same Google project.

## Protocol notes

Normal chat uses the still-supported GenerateContent API with `systemInstruction` and alternating user/model contents. Adjacent equal roles are merged; an initial model turn following summarization receives a neutral user continuation marker. Thought parts are not rendered as final text. Safety blocks become explicit failures; no `BLOCK_NONE` or filter-bypass settings are sent.

The SSE parser handles multiline data events, empty heartbeat/comment lines, bounded output and cancellation. JSON bodies/audio have response-size caps. Interactions TTS uses `store=false` to avoid opting into persistent interaction history. Designed voice resources use `store=true` intentionally and require a clear in-app confirmation.

## Official references

- [Models resource](https://ai.google.dev/api/models)
- [Models pagination](https://aistudio.google.com/api-reference/models/list-models)
- [GenerateContent API](https://ai.google.dev/api/generate-content)
- [Text generation](https://ai.google.dev/gemini-api/docs/text-generation)
- [Interactions behavior and storage](https://ai.google.dev/gemini-api/docs/interactions)
- [API keys](https://ai.google.dev/gemini-api/docs/api-key)
- [Rate limits](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Speech generation](https://ai.google.dev/gemini-api/docs/speech-generation)
- [Voices resource](https://aistudio.google.com/api-reference/voices)
- [Voice design](https://ai.google.dev/gemini-api/docs/voice-design)
