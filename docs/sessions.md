# Session Management & Chat History

Chat sessions are persisted locally so you can leave a conversation and resume it later. This document covers how sessions are saved, resumed, and automatically cleaned up.

## Overview

- **Single active chat**: the app shows one active conversation at a time, backed by a `SavedSession` persisted in a Jetpack DataStore (`"sessions"`).
- **Auto-save on every finished turn**: the current chat is saved automatically after each completed (or failed) response, so nothing is lost if the app is closed mid-conversation.
- **Auto-save on navigate**: the current chat is also saved to the History list when you start a new chat or resume a saved one (safety net).
- **No unsaved prompt**: a chat is only saved once it contains at least one user message; empty chats are discarded.
- **Title**: derived from the first user message, truncated to 50 characters (`Untitled` when blank).
- **Resume on launch**: the most recent session is reopened automatically when the app starts; tap **New Chat** to start fresh.

## Persistence Model

| Aspect | Detail |
|--------|--------|
| Storage | Jetpack DataStore (`DataStore<Preferences>`, file `sessions`) |
| Format | A JSON array of `SavedSession` (Gson) stored in the `sessions_list` preference key |
| SavedSession | `id`, `title`, `createdAt`, `updatedAt`, `raw` (list of `SessionMessage`), `summaries` |
| Cap | At most `SessionRepository.MAX_SESSIONS` (100) sessions are kept; the oldest are trimmed |
| Repository | `SessionRepository` in `src/android/main/java/com/example/buddy/data/` |
| UI | `HistoryScreen` in `src/android/main/java/com/example/buddy/ui/history/` |

`createdAt` is set once when a session is first saved and never changes. `updatedAt` is refreshed on **every** save (including auto-saves after each turn), so a session you revisit always moves back to the top of the list and is re-aged for filtering and auto-deletion. Loaded sessions missing `updatedAt` (from before this field existed) are normalized to fall back to `createdAt`.

## Save & Resume Flow

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant SessionRepository

    User->>ChatScreen: Send a message
    ChatScreen->>ViewModel: sendMessage()
    ViewModel->>ViewModel: stream response
    ViewModel->>ViewModel: saveCurrentSession() (auto-save on turn end)

    User->>ChatScreen: New Chat / resume a saved session
    ChatScreen->>ViewModel: startNewChat() / resumeSession(session)
    ViewModel->>ViewModel: cancelAndJoin() in-flight stream
    ViewModel->>ViewModel: saveCurrentSession()  (saves the outgoing chat)
    alt New Chat
        ViewModel->>ViewModel: clearChat()
    else Resume
        ViewModel->>ViewModel: conversationEngine.restore(history, summaries)
        ViewModel->>ViewModel: restore UI messages
    end
    ViewModel->>ChatScreen: Fresh / restored UI
```

- `sendMessage()` persists the session after each finished turn (both success and failure), so the active chat survives app closure.
- `startNewChat()` clears the text, engine history, summaries, and pending attachments; the outgoing chat is already saved.
- `resumeSession(session)` saves the current chat first, then restores the engine history and summaries for the chosen session and repopulates the UI.
- Both save the outgoing chat before changing state, so nothing is lost when navigating between conversations.

### Cancelling In-Flight Processing

When you start a new chat or resume a session while a response is still streaming, the ViewModel **cancels and joins** the running coroutine (`currentJob?.cancelAndJoin()`) **before** touching the state. This guarantees:

- The outgoing chat is built *after* the in-flight stream has fully stopped, so its captured state is complete and consistent.
- The old job's cleanup (which may set flags like `webSearchCancelled`) completes before the new chat starts, so a cancellation indicator from the old conversation never leaks into the new one.
- The old stream cannot append its assistant message or summaries into a just-restored session (the engine mutex is released on cancellation).

## History Screen

| Control | Behavior |
|---------|----------|
| Filter chips | All / Last 7 days / Last 30 days — filters the visible list by `updatedAt` |
| Session rows | Tap to resume the conversation |
| Checkboxes + Delete selected | Bulk-deletes checked sessions |
| Auto-delete toggle | Enables/disables automatic deletion of chats not interacted with for 30 days |

## Automatic Deletion (30 days)

The **Auto-delete** toggle removes conversations whose `updatedAt` is older than 30 days. Two triggers exist:

1. **App startup** — if auto-delete is enabled, sessions older than 30 days are purged on launch.
2. **Enabling the toggle** — if any session older than 30 days exists, a confirmation dialog appears asking whether to delete them.

```mermaid
sequenceDiagram
    participant User
    participant HistoryScreen
    participant MainActivity
    participant SessionRepository

    Note over User,SessionRepository: Startup
    MainActivity->>SessionRepository: autoDeleteEnabled()?
    SessionRepository-->>MainActivity: true
    MainActivity->>SessionRepository: purgeOlderThan(30 days)

    Note over User,SessionRepository: Toggle enable (with old chats)
    User->>HistoryScreen: Enable auto-delete switch
    HistoryScreen->>SessionRepository: setAutoDeleteOld(true)
    HistoryScreen->>HistoryScreen: any session older than 30 days?
    HistoryScreen->>User: Show "Delete Old Chats?" dialog
    User->>HistoryScreen: Confirm "Delete"
    HistoryScreen->>SessionRepository: purgeOlderThan(30 days)
```

Behavioral notes:

- **No stale-age triggers while running**: auto-delete does **not** run periodically or on every History screen visit — only on the two triggers above.
- **Dialog only when there is something to delete**: if no session is older than 30 days, the toggle enables silently.
- **Flag persists either way**: declining the confirmation dialog leaves auto-delete enabled (the confirm is only about deleting the *old* chats immediately).
- The threshold lives in `SessionRepository.AUTO_DELETE_AGE_MILLIS` (30 days).
- Because `updatedAt` is refreshed on every save, an actively used chat is re-aged and will not be auto-deleted.

## SessionMessage Metadata

`SessionMessage` persists the web-search flags per assistant turn (`webSearchUsed`, `webSearchSkipped`, `webSearchQueries`) so the web-search indicators and query chips are preserved after resume. Image attachments are stored as inline Base64 in `imageBase64`; file attachments keep their name and extracted text (the raw `Uri` is not persisted).

## Related Files

- `src/android/main/java/com/example/buddy/data/SessionRepository.kt` — session persistence, auto-delete flag, purge logic
- `src/android/main/java/com/example/buddy/ui/history/HistoryScreen.kt` — History screen, filters, toggle + confirmation dialog
- `src/android/main/java/com/example/buddy/ui/chat/ChatViewModel.kt` — save/resume orchestration and streaming cancellation
- `src/android/main/java/com/example/buddy/MainActivity.kt` — startup purge trigger