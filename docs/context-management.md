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
Summary(question: String, points: List<SummaryPoint>)
```

Rules:
- **2–3 points** per exchange, extracted by the LLM
- `key: true` — used sparingly for hard user decisions, absolute constraints, or strong preferences
- `key: false` — ordinary facts from the exchange
- Points are **sanitized** against a restrictive-patterns blacklist to prevent prompt over-generalization

Example JSON response from the LLM:

```json
{
  "points": [
    {"text": "User wants to deploy on Jetson Orin Nano 8GB", "key": true},
    {"text": "They are using OpenVINO IR format", "key": false}
  ]
}
```

The summary is appended to `ChatUiState.summaries`. If generation fails (network error, parse failure), the summary is silently skipped — the conversation continues with older context.

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

## Context Assembly (`buildLlmMessages`)

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

## Web Data

Fetched URLs and web search results share a single `## Web Data` system message:

- `### Fetched URL` section appears only when URLs were detected and successfully fetched
- `### Web Search` section appears only when web search is enabled and found results
- Both sections are omitted entirely when neither has data
- Each section is independently optional — you can have search results without fetched URLs and vice versa

Search query generation receives summaries context so the LLM can resolve pronouns and references in follow-up questions.

## Mutex Queue

A `Mutex` serializes message processing. If the user sends a follow-up message before summary generation completes for the previous turn, the new request waits:

```
sendMessage()
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

The user message is added to the UI immediately (with loading state), but actual LLM processing waits behind the lock.

## Resource Configuration

| File | Purpose |
|------|---------|
| `res/values/llm_prompts.xml` | Prompts: `search_query_prompt`, `summarizer_system_prompt`, `summarizer_user_template`, `compress_summaries_prompt` |
| `res/values/conversation.xml` | Parameters: `max_summaries` (20), `max_qa_pairs` (2), formatting strings (`key_prefix`, `point_indent`, `context_header`, `web_data_header`), `restrictive_patterns` |
| `res/values/llm_defaults.xml` | LLM defaults: temperature, top_p, top_k, max_tokens, system_message |
| `data/Summary.kt` | `SummaryPoint(text, key)`, `Summary(question, points)` |
| `data/LlmDefaults.kt` | Accessors: `maxSummaries`, `maxQaPairs`, `formatSummariesContext()`, `sanitizeSummaryPoints()`, etc. |
