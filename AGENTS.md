# Repository Notes

## Build & Verify
- Kotlin-only changes: `./gradlew :common:test :app:compileDebugKotlin`
- Any `res/values/*.xml` change: also run `./gradlew assembleDebug` (or `mergeDebugResources`) — `compileDebugKotlin` skips AAPT2's resource-flattening pass entirely, so it will not catch a broken string resource
- String resource escaping: literal `'` and `"` must be escaped as `\'`/`\"` even inside `<![CDATA[...]]>` blocks — CDATA does not exempt Android's own escape-processing pass. AAPT2 error messages can misattribute the failure to the wrong resource name; bisect by blanking suspect strings if the reported one looks unrelated
- No CI, no test suite, no lint/format config — manual verification only
- Gradle version catalog at `gradle/libs.versions.toml`
- `:common` is a pure Kotlin/JVM module and is tested with `./gradlew :common:test`

## Architecture

### Single Activity, State-Based Navigation
- `MainActivity` is the only Activity — no Jetpack Navigation, no Fragments
- Navigation is conditional rendering in `MainContent` (`MainActivity.kt:157`):
  `if (showParameters) ... else if (showEvents) ... else if (showAbout) ... else if (showSettings) ... else ChatScreen`
- Each screen sets its corresponding boolean state to `false` on back

### Dependency Injection
- No Hilt/Koin — uses `CompositionLocalProvider` with three globals:
  - `LocalLlmClient` — `LlmClient?` (`MainActivity.kt:46`)
  - `LocalWebSearch` — `WebSearch?` (`MainActivity.kt:47`)
  - `LocalUrlFetcher` — `UrlFetcher?` (`MainActivity.kt:48`)

### LLM Client Architecture
- Single client implementation: `OpenAIClient` (`common/.../llm/OpenAIClient.kt`) handles all providers
- No per-provider client code — everything goes through OpenAI-compatible `/chat/completions` and `/models` endpoints
- `LlmClientFactory` (`common/.../llm/LlmClientFactory.kt:15`) creates clients; `getModels()` creates a temp client to fetch models

### Common Module Seams
- Common networking and provider logic lives under `com.example.buddy.llm`, `com.example.buddy.search`, and `com.example.buddy.fetch`
- `AppConfigProvider` supplies fine-grained configuration interfaces; Android installs `AndroidAppConfig` at startup and desktop tests use `DefaultAppConfig`
- `Log` delegates common logging to the Phase-1 `Logger`; Android installs `EventLog` at startup
- `KeyProvider` abstracts API-key access; Android `SessionKeyCache` remains the encrypted implementation

### Providers
- Built-in providers loaded from `res/values/providers.xml` string arrays:
  - LLM: Fireworks AI, Together AI, Ollama Cloud, OpenRouter, SiliconFlow
  - Web Search: Exa, LinkUp, Tavily
- Provider IDs must match exactly when wiring web search: `"exa"`, `"linkup"`, `"tavily"`
- Custom providers added via Settings screen, persisted as Gson JSON in DataStore under `SettingsKeys.CUSTOM_LLM_PROVIDERS`
- API keys stored per-provider as JSON `Map<String, String>` in DataStore (`SettingsKeys.LLM_API_KEYS`) — autoloaded on provider switch

### Settings Repository
- Jetpack DataStore (not SharedPreferences) — all settings as reactive `Flow`s
- `SettingsRepository.kt` is the single source of truth for all persisted state
- `LlmSettings` data class holds all LLM parameters (provider, model, temperature, topP, topK, maxTokens, reasoningEffort, systemMessage)
- LLM client recreated reactively in `MainActivity` via `combine()` on settings flows

### Key Screens

| Screen | File | Notes |
|--------|------|-------|
| ChatScreen | `ui/chat/ChatScreen.kt` | Top bar has model selector (clickable name opens ModelSelectionDialog), web search toggle, Buddy logo menu |
| SettingsScreen | `ui/settings/SettingsScreen.kt` | Default Model is readOnly OutlinedTextField; clicking opens ModelSelectionDialog (AlertDialog) |
| ModelSelectionScreen | `ui/settings/ModelSelectionScreen.kt` | Two variants: full-screen `ModelSelectionScreen` + `ModelSelectionDialog` (AlertDialog); both use LazyColumn + real-time search |
| ParametersScreen | `ui/parameters/ParametersScreen.kt` | Temperature/Top-p/Top-k sliders; system message field |
| EventsScreen | `ui/events/EventsScreen.kt` | Event log viewer; filter by level (Error/Warning/Info/Debug) and tag |
| AboutScreen | `ui/about/AboutScreen.kt` | Version, build date, author links |

### ChatViewModel
- `ChatViewModel.updateClient()` sets `client.activeModel = client.defaultModel` when client changes
- File attachments: max 100KB, supported extensions: `.txt`, `.md`, `.log`, `.rst`, `.adoc`, `.asciidoc`, `.rtf`, `.json`, `.xml`, `.html`, `.py`, `.js`
- Image processing: max dimension 1440px, converted to JPEG at 85% quality, Base64-encoded
- Single attachment only — new attachment replaces previous one
- Context managed via **structured summarization**: each Q&A exchange is summarized into 2–3 points by a separate LLM call after streaming completes. Points have a `key` boolean for critical decisions.
- Summaries replace full history — only last N Q&A pairs sent as raw messages (default: 2 pairs, configurable via `max_qa_pairs` in `conversation.xml`)
- **Mutex-based processing queue**: if user sends a follow-up message before summary generation completes, the new request waits behind the lock until the current turn's summary is done
- Summaries compressed when exceeding `maxSummaries` (20): oldest half are merged into 1 summary by LLM, key points preserved mechanically
- **Web Data system message**: fetched URLs and web search results injected as a separate `## Web Data` system message (markdown), not appended to user content
- `buildLlmMessages()` structure: system prompt → summaries context → Web Data → limited Q&A pairs → current user message
- Web search: query generation returns a plan of 1-3 queries + a recency hint, fanned out in parallel by `WebSearchHelper` and merged; parsing is deliberately lenient for small (~9B) models, never erroring on a malformed response. See `docs/web-search.md` for the full workflow
- See `docs/context-management.md` for full details

## Conventions
- No comments added to code
- Follow existing code patterns and style
- Dead dependencies were removed — do not re-add `com.aallam.openai:openai-client` or `io.ktor:ktor-client-okhttp`

## Documentation
- `README.md` — project overview and quick start
- `docs/providers.md` — provider reference (URLs, privacy, status)
- `docs/dependencies.md` — external library catalog
- `docs/context-management.md` — history summarization, compression, and Web Data architecture
- `docs/web-search.md` — web search workflow: query-plan generation, lenient parsing for small models, parallel fan-out, merge, recency mapping
- `docs/use-cases.md` — step-by-step user guides
- `docs/limitations.md` — known technical constraints
- `docs/seq_chat.md` — chat sequence diagrams
- `docs/seq_others.md` — settings/events/about sequence diagrams
- `FAQ.md` — user-facing FAQ
