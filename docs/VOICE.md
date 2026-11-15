# Voice system

## Four separate layers

| Layer | What it controls |
|---|---|
| Character personality | Textual behavior, conversational tone, initiative and identity consistency |
| Vocal identity | A real prebuilt/catalog/custom voice identifier accepted by the selected endpoint |
| TTS delivery | Natural-language style instructions compiled from the 11 voice controls and optional custom guidance |
| Playback processing | Native player speed and seeking; this does not change the stored profile or regenerate audio |

Slider values are app-level abstractions, not undocumented numeric API parameters. Values within the neutral band use a natural delivery. Extremes produce concise stylistic instructions; pitch remains a relative register suggestion within the selected voice, and speed remains a pace instruction. Voice identity is reused across requests, but generation is probabilistic and consistency is not absolute. The application does not claim precise pitch, formant or emotion control.

## Supported paths

**GenerateContent TTS:** Uses the documented `AUDIO` response modality and `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`. This path accepts the 30 documented built-in voices. Style guidance precedes a transcript. Returned Gemini PCM is wrapped as signed 16-bit little-endian WAV using MIME sample-rate metadata (24 kHz default).

**Interactions TTS:** Uses verbatim text items with `speech_metadata` style annotations, an audio response format and the selected real voice ID. WAV audio is read from the model-output step. This path supports compatible catalog/custom IDs. It is selectable independently, and incompatibility is reported by the server.

**Voice design:** Only the officially documented families are enabled in the UI after an authenticated catalog check. The request stores a voice in the user's Google project and uses its actual returned ID/sample, never a fabricated ID or unrelated preview. If this account lacks access or quota, the error is shown without changing the key, model or billing. Local preset deletion does not delete the cloud resource; manage that resource in AI Studio.

Microphone recording, user voice messages, STT, voice calls, Live API sessions and voice replication/cloning are deliberately absent.

## Preview and presets

Built-in delivery presets initialize real control values. Custom profiles support save/edit/duplicate/delete. Two generated previews are retained for A/B comparison, showing their original sentence, voice, model and control snapshots. “Pakai konfigurasi” copies settings into the current profile while preserving its identity. Preview generation does not autoplay; Play and Stop are explicit controls. Saving/applying a profile does not change other sessions.

## Message audio

Manual mode is the default. The text reply persists before “Ubah ke VN” is available. Automatic mode initiates the same TTS pipeline after text completion, with autoplay controlled separately. A failed request preserves both the text and an existing successful VN. Regenerate VN never regenerates the AI's text.

Long text is split at sentence/word boundaries into approximately 4,500-character chunks. A message is limited to 32 speech segments per conversion; the UI reports the limit rather than silently omitting text. Compatible short two-speaker dialogue can share one request; otherwise each segment has its own request, so long or many-speaker messages consume more quota.

For roleplay, exact named-dialogue markers map to the saved actor voice. Unknown labels remain narration. Dialogue-only excludes narration/actions; a selected-character scope requires that actor ID. Native two-speaker synthesis is used when both actors explicitly select the same documented Gemini 3.8 Flash TTS or Flash-Lite TTS model through Interactions, have stable voice mappings, and the combined transcript is at most 4,500 characters. Each turn carries its own speaker and delivery style; both profile snapshots are saved in audio metadata. Other combinations use **sequential single-speaker clips**, including three or more speakers, different models, long transcripts, or unverified model capabilities. No failure triggers a hidden model switch or extra fallback generation request. Native and sequential paths are fixture-tested; live account/audio verification remains outstanding.

One Media3 player serves every preview/message. Starting another owner stops the old playlist. Audio focus and headphone-disconnection handling are native; playback pauses when the activity is no longer visible. Seeking/progress are per segment, with segment index, speaker and next/previous controls for playlists. Playback speed is native time stretching rather than a Gemini parameter.

## Verification boundaries

Protocol/encoding tests can run without a key using test-only fixtures. They do not prove account entitlement, subjective vocal quality, exact text fidelity, pronunciation, or slider responsiveness of a live model. Those require the manual tests in `TEST_RESULTS.md` using a user's own key. The application contains no fallback prerecorded preview and no mock runtime response.
