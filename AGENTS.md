# Repository Notes

## Build & Verify
- Build: `./gradlew app:compileDebugKotlin`
- No CI, no test suite, no lint/format config — manual verification only
- Gradle version catalog at `gradle/libs.versions.toml`

## Architecture

### Single Activity, State-Based Navigation
- `MainActivity` is the only Activity — no Jetpack Navigation, no Fragments
- Navigation is conditional rendering in `MainContent` (`MainActivity.kt:157`):
  `if (showParameters) ... else if (showEvents) ... else if (showAbout) ... else if (showSettings) ... else ChatScreen`
- Each screen sets its corresponding boolean state to `false` on back

### Dependency Injection
- No Hilt/Koin — uses `CompositionLocalProvider` with three globals:
  - `LocalLlmClient` — `LlmClient?` (`MainActivity.kt:39`)
  - `LocalWebSearch` — `WebSearch?` (`MainActivity.kt:40`)
  - `LocalUrlFetcher` — `UrlFetcher?` (`MainActivity.kt:41`)

### LLM Client Architecture
- Single client implementation: `OpenAiCompatibleLlmClient` handles all providers
- No per-provider client code — everything goes through OpenAI-compatible `/chat/completions` and `/models` endpoints
- `LlmClientFactory` (`ext/LlmClient.kt:134`) creates clients; `getModels()` creates a temp client to fetch models

### Providers
- Built-in providers loaded from `res/values/providers.xml` string arrays:
  - LLM: Fireworks AI, Together AI, Ollama Cloud, OpenRouter, SiliconFlow
  - Web Search: Exa, LinkUp, Tavily
- Provider IDs must match exactly when wiring web search: `"exa"`, `"linkup"`, `"tavily"`
- Custom providers added via Settings screen, persisted as Gson JSON in DataStore under `SettingsKeys.CUSTOM_LLM_PROVIDERS`
- API keys stored per-provider as JSON `Map<String, String>` in DataStore (`SettingsKeys.LLM_API_KEYS`) — auto-loaded on provider switch

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

## Conventions
- No comments added to code
- Follow existing code patterns and style
- Dead dependencies were removed — do not re-add `com.aallam.openai:openai-client` or `io.ktor:ktor-client-okhttp`

## Documentation
- `README.md` — project overview and quick start
- `docs/providers.md` — provider reference (URLs, privacy, status)
- `docs/dependencies.md` — external library catalog
- `docs/use-cases.md` — step-by-step user guides
- `docs/limitations.md` — known technical constraints
- `docs/seq_chat.md` — chat sequence diagrams
- `docs/seq_others.md` — settings/events/about sequence diagrams
- `FAQ.md` — user-facing FAQ
