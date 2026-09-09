# Repository Notes

## Build & Verify
- Kotlin-only changes: `./gradlew :cli:test :app:compileDebugKotlin :cli:installDist`
- Any `src/android/main/res/values/*.xml` change: also run `./gradlew assembleDebug` (or `mergeDebugResources`) — `compileDebugKotlin` skips AAPT2's resource-flattening pass entirely, so it will not catch a broken string resource
- Desktop picks up `res/values` changes automatically via `copyAndroidValues` → `processResources`; `:cli:test` and `:cli:installDist` rerun the copy when the res files change
- String resource escaping: literal `'` and `"` must be escaped as `\'`/`\"` even inside `<![CDATA[...]]>` blocks — CDATA does not exempt Android's own escape-processing pass. AAPT2 error messages can misattribute the failure to the wrong resource name; bisect by blanking suspect strings if the reported one looks unrelated
- No CI, no lint/format config — the desktop-JVM unit tests (`:cli:test`) are the only automated tests
- Gradle version catalog at `gradle/libs.versions.toml`
- Desktop JVM unit tests run with `./gradlew :cli:test`

## Architecture

### Single Activity, State-Based Navigation
- `MainActivity` is the only Activity — no Jetpack Navigation, no Fragments
- Navigation is conditional rendering in `MainContent` (`MainActivity.kt:157`):
  `if (showParameters) ... else if (showEvents) ... else if (showAbout) ... else if (showProviders) ... else ChatScreen`
- Each screen sets its corresponding boolean state to `false` on back

### Dependency Injection
- No Hilt/Koin — uses `CompositionLocalProvider` with three globals:
  - `LocalLlmClient` — `LlmClient?` (`MainActivity.kt:46`)
  - `LocalWebSearch` — `WebSearch?` (`MainActivity.kt:47`)
  - `LocalUrlFetcher` — `UrlFetcher?` (`MainActivity.kt:48`)

### LLM Client Architecture
- Single client implementation: `OpenAIClient` (`src/common/.../llm/OpenAIClient.kt`) handles all providers
- No per-provider client code — everything goes through OpenAI-compatible `/chat/completions` and `/models` endpoints
- `LlmClientFactory` (`src/common/.../llm/LlmClientFactory.kt:15`) creates clients; `getModels()` creates a temp client to fetch models

### Common Sources (`src/common`)
- Shared networking, provider, and conversation logic lives in `src/common/main/kotlin/com/example/buddy/{llm,search,fetch,chat}` and is compiled into both the `:app` (Android) and `:cli` (desktop) modules
- `ConversationEngine` owns URL detection/fetch orchestration, web search, message assembly, streaming, summaries, and compression; Android `ChatViewModel` and the `:cli` application consume its events
- `AppConfigProvider` supplies fine-grained configuration interfaces; Android installs `AndroidAppConfig` (reads `res/` via R) at startup; the `:cli` app and desktop tests read the actual `src/android/main/res/values/*.xml` files
- Desktop config resolution: `copyAndroidValues` (in `desktop/build.gradle.kts`) copies `src/android/main/res/values/*.xml` into the CLI jar under `values/` at build time; `ResourceValuesLoader.loadFromClasspath()` parses them (`ResourceValues.kt`) and `ResourceAppConfig` (`ResourceAppConfig.kt`) exposes them via the `AppConfig` interfaces. `Main.kt` installs it at startup; `AppConfigProvider` also falls back to the same lazy classpath load when nothing is installed (Android sets it first, so the fallback never fires there). `ResourceConfigLoadTest` guards key coverage
- `Log` delegates common logging to the Phase-1 `Logger`; Android installs `EventLog` at startup
- `KeyProvider` abstracts API-key access; Android `SessionKeyCache` remains the encrypted implementation
- `EnvKeyProvider` maps desktop provider IDs to environment variables for the standalone `:cli` desktop application
- Session persistence lives in `src/common/.../data` (`SavedSession`/`SessionMessage`, `SessionRepository`, `SessionStorage`) and `src/common/.../chat/ChatSessionManager`. `SessionStorage` abstraces backing (Android uses `DataStoreSessionStorage` in `src/android`; a desktop file-backed store can be added later). `ChatViewModel` delegates session bookkeeping to `ChatSessionManager`
- Custom-provider serialization (`serializeProviderData`/`deserializeProviderData`) is in `src/common/.../data/Providers.kt`; only Android's `BuiltInProviders` loads providers from `res/` resources

### Providers
- Built-in providers loaded from `res/values/providers.xml` string arrays:
  - LLM: Fireworks AI, Together AI, Ollama Cloud, OpenRouter, SiliconFlow
  - Web Search: Exa, LinkUp, Tavily
- Provider IDs must match exactly when wiring web search: `"exa"`, `"linkup"`, `"tavily"`
- Custom providers added via the Providers screen, persisted as Gson JSON in DataStore under `SettingsKeys.CUSTOM_LLM_PROVIDERS`
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
| ProvidersScreen | `ui/providers/ProvidersScreen.kt` | Default Model is readOnly OutlinedTextField; clicking opens ModelSelectionDialog (AlertDialog) |
| ModelSelectionScreen | `ui/providers/ModelSelectionScreen.kt` | Two variants: full-screen `ModelSelectionScreen` + `ModelSelectionDialog` (AlertDialog); both use LazyColumn + real-time search |
| HistoryScreen | `ui/history/HistoryScreen.kt` | Saved chat sessions; filter chips (All/7/30 days), tag filter chips (AND), auto-delete toggle + confirmation dialog, bulk delete, tag labels on rows |
| ParametersScreen | `ui/parameters/ParametersScreen.kt` | Temperature/Top-p/Top-k sliders; system message field |
| EventsScreen | `ui/events/EventsScreen.kt` | Event log viewer; filter by level (Error/Warning/Info/Debug) and tag |
| AboutScreen | `ui/about/AboutScreen.kt` | Version, build date, author links |

### ChatViewModel
- `ChatViewModel` retains Android UI, URI, bitmap, ContentResolver, and foreground-service concerns; reusable conversation processing is implemented by `src/common/main/kotlin/.../chat/ConversationEngine.kt`
- `ChatViewModel.updateClient()` sets `client.activeModel = client.defaultModel` when client changes
- File attachments: max 100KB, supported extensions: `.txt`, `.md`, `.log`, `.rst`, `.adoc`, `.asciidoc`, `.rtf`, `.json`, `.xml`, `.html`, `.py`, `.js`
- Image processing: `ChatViewModel.onImageUri(uri)` decodes on `Dispatchers.IO` with a two-pass bounds+`inSampleSize` decode (avoids full-resolution OOM), downscales to max dimension 1440px, JPEG quality 85, Base64 data URI. Failure sets `attachmentError` in the UI
- Single attachment only — new attachment replaces previous one
- Context managed via **structured summarization**: each Q&A exchange is summarized into 2–3 points by a separate LLM call after streaming completes. Points have a `key` boolean for critical decisions.
- Summaries replace full history — only last N Q&A pairs sent as raw messages (default: 2 pairs, configurable via `max_qa_pairs` in `conversation.xml`)
- **Mutex-based processing queue**: if user sends a follow-up message before summary generation completes, the new request waits behind the lock until the current turn's summary is done
- Summaries compressed when exceeding `maxSummaries` (20): oldest half are merged into 1 summary by LLM, key points and tags preserved mechanically
- **Session tags**: each summary also carries 1–3 tags chosen from a fixed 10-category list (`summaries.sessionTags`, sourced from the `session_tags` string-array in `res/values/conversation.xml` via `SessionTags.CATEGORIES`), parsed leniently and matched case-insensitively; out-of-set tags are dropped at parse/load. `ChatSessionManager` derives the session's tags mechanically at save: `summaries.flatMap { it.tags }.distinct().takeLast(maxSessionTags)` (default 3). History filters sessions by multi-select tag chips (AND). See `docs/sessions.md`
- **Web Data system message**: fetched URLs and web search results injected as a separate `## Web Data` system message (markdown), not appended to user content
- `buildLlmMessages()` structure: system prompt → summaries context → Web Data → limited Q&A pairs → current user message
- Web search: query generation returns a plan of 1-3 queries + a recency hint, fanned out in parallel by `WebSearchHelper` and merged; parsing is deliberately lenient for small (~9B) models, but an unusable response (`search: false`, non-JSON prose, answer-shaped query strings, truncated output) **skips the search** rather than ever using prose as a query. The query-gen call is non-streaming and surfaces `finish_reason` + HTTP error bodies. When an image is attached and the active model is multimodal, the image is included in query generation so queries are grounded in its content. See `docs/web-search.md` for the full workflow
- **Chat sessions** (`ChatSessionManager` + `SessionRepository`): the active chat is auto-saved after every finished turn and on navigation (New Chat / resume), capped at `MAX_SESSIONS` (100). The most recent session is resumed on launch. `SavedSession.createdAt` is immutable; `updatedAt` drives History filters, ordering, and the 30-day auto-delete. Image attachments are not stored inline: Android `SessionImageStore` writes JPEGs to `filesDir/session_images/<sessionId>/` and persists only `imageRef` filenames in the DataStore JSON (base64 lives in memory only); sessions are hydrated on resume and image directories deleted on session delete/purge. See `docs/sessions.md`
- **Segment bubbles**: assistant responses are split at render time into stacked segment bubbles by `splitIntoSegments` (`src/common/main/kotlin/.../chat/MessageSegments.kt`): prose renders via Markwon, fenced code (```) becomes its own bubble (`CodeSegmentBubble.kt`, wider 80%-screen width, per-block copy button). Both preview buttons only appear when not streaming. Code blocks also get a save button (only when not streaming) that writes the code verbatim to the system Downloads folder via `MediaStore.Downloads` (`saveCodeToDownloads` in `CodeSegmentBubble.kt`) — no permission needed on minSdk 29+, no picker, fixed destination; the filename is `buddy_code_<yyyyMMdd_HHmmss>.<ext>` built by `CodeFileNaming.kt` (`codeFileName`/`extensionForLang`, lang→extension map, `.txt` fallback for unknown/unlabeled blocks, timestamp avoids collisions), the MIME type comes from `MimeTypeMap`, success/failure are logged under a `Save` event tag, and the button briefly flips to a check icon. Markdown blocks (`markdown`/`md`) preview in-app via `MarkdownPreviewDialog.kt` (native Compose dialog + Markwon, scrollable). HTML blocks (`html`/`htm`) have no in-app preview: the button writes the code verbatim to `cacheDir/buddy_preview.html` and opens it in the system browser via `FileProvider` `content://` + `ACTION_VIEW` `text/html` (see the `openHtmlInBrowser` helper in `CodeSegmentBubble.kt`); the existing `<cache-path path="."/>` in `res/xml/file_paths.xml` already covers cacheDir, and no `<queries>` element is needed because we never call `resolveActivity()`. Failure (no handler) shows a Toast and logs a `Preview` error event; copy remains the fallback. The HTML is written verbatim and opened with the browser's full privileges (opaque origin: JS/network work, but no localStorage/site access); previously HTML rendered in a sandboxed dialog WebView, which raced the Dialog window's intermediate layout passes and froze CSS viewport units — decks locked to `height`/`max-height`/`max-width` in `100vh`/`100vmin` with `overflow:hidden` rendered blank or clipped, so the WebView path was removed and is not to be re-added. Copy/export keeps the original HTML untouched. The split is UI-only — persistence, `SavedSession.raw`, and engine context keep the full unsplit assistant text
- See `docs/context-management.md` for full details

### Desktop CLI Application
- `:cli` is a standalone JVM desktop application, separate from the offline unit-test suite
- Run with `./gradlew :cli:run` or build an executable distribution with `./gradlew :cli:installDist`
- API keys are read from provider-specific environment variables; normal `:cli:test` never requires them
- Startup requires an LLM provider and model; web-search provider setup is optional
- Config is read at startup from the `res/values/*.xml` files bundled into the jar (see `copyAndroidValues` above), so config edits happen in `src/android/main/res/values/` only — never mirror values in code

## Conventions
- No comments added to code
- Follow existing code patterns and style
- Dead dependencies were removed — do not re-add `com.aallam.openai:openai-client` or `io.ktor:ktor-client-okhttp`

## Documentation
- `README.md` — project overview and quick start
- `docs/providers.md` — provider reference (URLs, privacy, status)
- `docs/dependencies.md` — external library catalog
- `docs/context-management.md` — history summarization, compression, and Web Data architecture
- `docs/web-search.md` — web search workflow: query-plan generation (`search` boolean contract), skip-on-unusable parsing for small models, parallel fan-out, merge, recency mapping
- `docs/sessions.md` — chat session persistence, auto-save/resume, History screen, and 30-day auto-delete
- `docs/use-cases.md` — step-by-step user guides
- `docs/limitations.md` — known technical constraints
- `docs/seq_chat.md` — chat sequence diagrams
- `docs/seq_others.md` — settings/events/about sequence diagrams
- `FAQ.md` — user-facing FAQ
