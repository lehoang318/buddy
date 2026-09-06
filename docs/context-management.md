# Context Management

Two complementary mechanisms manage conversation context — **structured summarization** replaces full history with condensed points, and **Web Data system messages** keep web content separate from user text.

## Problem

| Old Behavior | New Behavior |
|-------------|-------------|
| Full history sent every request (unbounded growth) | Only last 2 Q&A pairs as raw messages |
| Web search results baked into user message content | Separate `## Web Data` system message |
| Fetched URL content persisted in message history | URL content goes to Web Data block; summarized away |
| File attachments re-sent with every historical message | Attachments only for current message; older ones summarized |
| Search query generated from isolated user message | Search query gets summaries context for pronoun resolution |
| No guard against exceeding model context limits | Constant base of ~1.5K tokens + current turn |

## Summaries — Per-Turn Condensation

After each Q&A exchange completes, a non-streaming LLM call generates a structured JSON summary:

```
SummaryPoint(text: String, key: Boolean)
Summary(question: String, points: List<SummaryPoint>, tags: List<String>)
```

Rules:
- **2–3 points** per exchange, extracted by the LLM
- **Images**: if the user's message carried an image, the image is included in the summarizer call (as an `image_url` part when the model is multimodal) and key visual facts are captured as points — so visual context survives once the exchange leaves the recent-pairs window (`max_qa_pairs`).
- `key: true` — used sparingly for hard user decisions, absolute constraints, or strong preferences
- `key: false` — ordinary facts from the exchange
- Points are **sanitized** against a restrictive-patterns blacklist to prevent prompt over-generalization
- **Tags** — 1–3 categories chosen from a fixed 10-item list (`Politics`, `Business`, `World`, `Technology`, `Science`, `Health`, `Environment`, `Justice`, `Entertainment`, `Sports`, defined in the `session_tags` string-array in `res/values/conversation.xml`), parsed leniently (missing/wrong shape/unknown → ignored) and matched case-insensitively; aggregated into the owning session's tags (see [sessions.md](./sessions.md)). Small models that omit `"tags"` just produce an empty list.

Example JSON response from the LLM:

```json
{
  "points": [
    {"text": "User wants to deploy on Jetson Orin Nano 8GB", "key": true},
    {"text": "They are using OpenVINO IR format", "key": false}
  ],
  "tags": ["Technology"]
}
```

The summary is appended to `ConversationEngine.summaries`. If generation fails (network error, parse failure), the summary is silently skipped — the conversation continues with older context.

During compression, the merged "Earlier conversation" summary also mechanically preserves the compressed group's tags (like key points), so old tags survive merging.

## Compression — Preventing Unbounded Growth

When `summaries.size > maxSummaries` (20), compression triggers:
1. Oldest `maxSummaries / 2` (10) summaries are selected
2. **Key points** (`key: true`) from those summaries are mechanically extracted and preserved
3. Non-key points are formatted and sent to the LLM with a merge prompt
4. The LLM returns a merged summary (2–3 points, all `key: false`)
5. Mechanically-preserved key points are appended to the LLM's output
6. The 10 oldest summaries are replaced by 1 compressed summary
7. Result: `summaries.size` drops from ~21 to ~12

Key decisions are never lost during compression — they are preserved programmatically, not subject to LLM summarization quality.

## Context Assembly (`MessageBuilder`)

```
[0] SYSTEM  — system prompt ("You are a helpful assistant.")
[1] SYSTEM  — summaries context (if any exist):
    ## Use the context below when relevant:
    - {question}
      + [KEY] point text
      + point text
[2] SYSTEM  — Web Data (if fetched URLs or search results exist):
    ## Web Data
    ### Fetched URL
    #### https://example.com
    page content...
    ### Web Search
    #### Result Title
    result content...
[3..N]      — last maxQaPairs user+assistant pairs (bare content only)
[N+1] USER  — current message (with file attachment text if any)
```

**Pair selection:** Messages are scanned for consecutive USER→ASSISTANT pairs. Unpaired messages (e.g. the greeting) are naturally excluded.

## Token & Request Size Limits

All limit values live in `res/values/llm_defaults.xml` and are exposed through `AppConfig` (`AndroidAppConfig` reads them via `R` on device; `ResourceAppConfig` reads the same XML from the desktop jar's classpath).

**Response limits (chat only):**
- The system prompt states a soft limit: `## Output Limit — "Your response may not exceed N tokens"`, where `N` is the effective value of `llm.maxTokens` (`default_max_tokens`, 4096; overridden by the persisted Android setting).
- The API request actually sends `max_tokens = N * responseLimitMultiplier` (2), so the provider enforces a hard cap of 2N. This gives the model headroom (formatting, reasoning overhead) while the prompt keeps the visible answer tight. The prompt and the API parameter always derive from the same `N`, so they stay consistent.
- If the selected model's `context_length` is known (parsed from `/models`; OpenRouter exposes it, others may not), the prompt is estimated at `Σ content chars / 4` tokens. When `prompt + hard limit` exceeds the window, `max_tokens` is clamped to the remaining budget, never below `min_response_tokens` (256). A warning is logged to the Events screen. The guard is best-effort — it only applies when a context length is known.
- Summary/compression calls keep `max_tokens == summary_max_tokens` (512) matching their prompt statement. Search-query generation uses its own `search_query_max_tokens`.

**Request size cap (chat only):**
- The assembled request (system prompt + memory + Web Data + pairs + current message) is capped at `llm.maxRequestChars` (32768 characters). Image base64 is excluded from the count (it would blow the budget on every image request).
- When the cap is exceeded, content is trimmed in priority order (lowest value first), with a warning logged:
  1. Search result contents (dropped from the tail)
  2. Fetched URL contents (dropped from the tail)
  3. Search engine summary
  4. Summaries (oldest first)
  5. Raw Q&A pairs (oldest first)
  6. Current user message (truncated at a word boundary, last resort)
- If the whole Web Data section empties, `## Web Data` is omitted entirely.

## Web Data

Fetched URLs and web search results share a single `## Web Data` system message:

- `### Fetched URL` section appears only when URLs were detected and successfully fetched
- `### Web Search` section appears only when web search is enabled and found results
- Both sections are omitted entirely when neither has data
- Each section is independently optional — you can have search results without fetched URLs and vice versa

Search query generation receives summaries context so the LLM can resolve pronouns and references in follow-up questions. As of the multi-query update, query generation returns a plan of 1-3 queries plus a recency hint (`day`/`week`/`month`/`any`), fanned out in parallel and merged before injection — see [web-search.md](./web-search.md) for the full workflow, including how small (~9B) models' malformed responses degrade gracefully instead of failing.

When a provider returns a native synthesized answer (Tavily, LinkUp), it's injected as a `### Search Engine Summary` section above the raw `### Web Search` results, guided by the `web_data_instructions` resource (prefer fresh results for time-sensitive claims, cite URLs, flag conflicting sources, treat the summary as a starting point rather than ground truth). Each raw result also carries its source URL and publish date (when the provider supplies one) alongside its content. The current date is injected into both the system prompt and the query-generation prompt so "latest"/"current" questions aren't reasoned about purely from training-data assumptions.

## Mutex Queue

A `Mutex` in `ConversationEngine` serializes message processing. If the user sends a follow-up message before summary generation completes for the previous turn, the new request waits:

```
ConversationEngine.send()
  └── coroutine {
        fetch URLs              ← outside lock, parallelizable
        mutex.withLock {
          web search (with summaries context)
          stream response
          generate summary     ← blocks next message, not the UI
          compress if needed
        }
      }
```

The user message is accepted after URL fetching, while actual LLM processing waits behind the lock. Android renders the resulting engine events; the CLI prints them.

## Resource Configuration

| File | Purpose |
|------|---------|
| `res/values/llm_prompts.xml` | Prompts: `search_query_prompt`, `summarizer_system_prompt`, `summarizer_user_template`, `compress_summaries_prompt` |
| `res/values/conversation.xml` | Parameters: `max_summaries` (20), `max_qa_pairs` (2), `max_session_tags` (3), formatting strings (`key_prefix`, `point_indent`, `context_header`, `web_data_header`), `restrictive_patterns` |
| `res/values/llm_defaults.xml` | LLM defaults: temperature, top_p, top_k, max_tokens, `response_hard_limit_multiplier`, `request_max_chars`, `min_response_tokens`, `search_query_max_tokens`, system_message, search tuning (see [web-search.md](./web-search.md)) |
| `src/common/.../data/Summary.kt` | `SummaryPoint(text, key)`, `Summary(question, points, tags)` |
| `src/common/.../chat/ConversationEngine.kt` | Turn queue, URL/search orchestration, streaming, summary generation, compression |
| `src/common/.../chat/ConversationEngine.kt` | `MessageBuilder` assembles system prompts, summaries, Web Data, history, and attachments |
| `src/common/.../config/AppConfig.kt` | Accessors: `summaries.maxSummaries`, `summaries.maxQaPairs`, `summaries.formatSummariesContext()`, `summaries.sanitizeSummaryPoints()`, `search.*`, etc. |
