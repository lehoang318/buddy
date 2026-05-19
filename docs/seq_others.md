# Buddy AI Assistant - Sequence Diagrams

## Other Scenarios

### 1. Settings - Initial Configuration (Happy Path)

```mermaid
sequenceDiagram
    participant User
    participant MainActivity
    participant SettingsScreen
    participant SessionKeyCache
    participant SettingsRepository
    participant LlmClientFactory
    participant LLMClient

    User->>MainActivity: Open app
    MainActivity->>SettingsRepository: Load settings from DataStore
    MainActivity->>SessionKeyCache: Load API keys from EncryptedSharedPreferences
    MainActivity->>MainActivity: Create LLM client if key exists
    MainActivity->>MainActivity: Display Chat Screen

    User->>MainActivity: Tap Buddy logo
    MainActivity->>MainActivity: Show menu dropdown
    MainActivity->>MainActivity: User selects Settings

    User->>SettingsScreen: Settings screen opens
    SettingsScreen->>SettingsRepository: getSettings()
    SettingsRepository-->>SettingsScreen: Return saved settings

    User->>SettingsScreen: Select provider from dropdown
    SettingsScreen->>SettingsScreen: Update selected provider

    User->>SettingsScreen: Tap connect button
    SettingsScreen->>SettingsScreen: Show ApiKeyConnectDialog
    User->>SettingsScreen: Enter API key and tap Connect
    SettingsScreen->>SessionKeyCache: saveKey(providerId, key)
    SettingsScreen->>LlmClientFactory: getModels(provider)
    LlmClientFactory->>LlmClientFactory: ApiKeyInterceptor reads key from SessionKeyCache
    LlmClientFactory->>LlmClientFactory: Call provider API
    LlmClientFactory-->>SettingsScreen: Return list of models

    Note over SettingsScreen: Auto-select first model: selectedModel = models.first().id

    SettingsScreen->>LlmClientFactory: createWithProvider(provider, selectedModel)
    LlmClientFactory->>LlmClientFactory: Create LLM client instance
    LLMClient->>LLMClient: testConnection()
    LLMClient-->>SettingsScreen: Connection successful

    SettingsScreen->>SettingsRepository: updateAll(provider, model, ...)
    SettingsRepository->>SettingsRepository: Save non-sensitive settings
    SettingsRepository-->>SettingsScreen: Settings saved

    User->>SettingsScreen: Tap back button
    SettingsScreen->>SettingsScreen: onSaveModelSettings(LlmSettings(model=selectedModel, ...))
    SettingsScreen->>MainActivity: onBack()
    MainActivity-->>User: Return to chat screen
```

### 2. Settings - Add Custom Provider

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant SessionKeyCache
    participant SettingsRepository
    participant LlmClientFactory

    User->>SettingsScreen: Open settings
    SettingsScreen->>SettingsScreen: Display current settings

    User->>SettingsScreen: Tap LLM provider dropdown
    SettingsScreen->>SettingsScreen: Show provider list
    User->>SettingsScreen: Tap "Add Provider..."
    SettingsScreen->>SettingsScreen: Show AddProviderDialog

    User->>SettingsScreen: Enter name, base URL, API key
    SettingsScreen->>SettingsScreen: Tap "Add"
    SettingsScreen->>SessionKeyCache: saveKey(providerId, apiKey)
    SettingsScreen->>SettingsRepository: addCustomLlmProvider(provider config without key)
    SettingsRepository->>SettingsRepository: Save to DataStore (key stripped)
    SettingsRepository-->>SettingsScreen: Provider saved

    SettingsScreen->>SettingsScreen: Select new provider from dropdown
    SettingsScreen->>SettingsScreen: handleConnect()
    SettingsScreen->>LlmClientFactory: getModels(provider)
    LlmClientFactory->>LlmClientFactory: ApiKeyInterceptor reads key from SessionKeyCache
    LlmClientFactory->>LlmClientFactory: Call provider API
    LlmClientFactory-->>SettingsScreen: Return list of models

    Note over SettingsScreen: Auto-select first model

    SettingsScreen->>SettingsRepository: updateAll(provider, model, ...)
    SettingsRepository->>SettingsRepository: Save non-sensitive settings (no apiKey)
    SettingsRepository-->>SettingsScreen: Settings saved

    SettingsScreen-->>User: Return to chat screen
```

### 3. Settings - Change Model

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant SettingsRepository
    participant MainActivity

    User->>SettingsScreen: Open settings
    SettingsScreen->>SettingsScreen: Display current settings

    User->>SettingsScreen: Tap sync button to refresh models
    SettingsScreen->>SettingsScreen: Fetch available models
    SettingsScreen-->>User: Show model list

    User->>SettingsScreen: Tap "Default Model" field
    SettingsScreen->>SettingsScreen: Show ModelSelectionDialog
    User->>SettingsScreen: Select new model from dialog
    SettingsScreen->>SettingsScreen: Update selected model

    User->>SettingsScreen: Tap back button
    SettingsScreen->>SettingsScreen: onSaveModelSettings(LlmSettings(model=selectedModel, ...))
    SettingsScreen->>MainActivity: onBack()
    MainActivity->>MainActivity: Update LLM client with new model
    MainActivity-->>User: Return to chat screen
```

### 4. Settings - Change Web Search Provider

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant SessionKeyCache
    participant SettingsRepository
    participant MainActivity
    participant WebSearch

    User->>SettingsScreen: Open settings
    SettingsScreen->>SettingsScreen: Display current settings

    User->>SettingsScreen: Select web search provider from dropdown
    SettingsScreen->>SettingsScreen: Update selected provider

    User->>SettingsScreen: Tap connect button
    SettingsScreen->>SettingsScreen: Show ApiKeyConnectDialog
    User->>SettingsScreen: Enter API key and tap Connect
    SettingsScreen->>SessionKeyCache: saveKey("ws_${providerId}", key)
    SettingsScreen->>SettingsRepository: updateAll(provider, model, webSearchProvider)
    SettingsRepository->>SettingsRepository: Save settings (no key)
    SettingsRepository-->>SettingsScreen: Settings saved

    SettingsScreen->>MainActivity: onBack()
    MainActivity->>MainActivity: keyCache.keyIds flow triggers combine
    MainActivity->>MainActivity: Create WebSearch instance with ApiKeyInterceptor
    MainActivity-->>User: Return to chat screen
```

### 5. Settings - Connection Error Handling

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant SessionKeyCache
    participant LlmClientFactory
    participant LLMClient

    User->>SettingsScreen: Enter API key in dialog and tap Connect
    SettingsScreen->>SessionKeyCache: saveKey(providerId, key)
    SettingsScreen->>LlmClientFactory: getModels(provider)
    LlmClientFactory->>LlmClientFactory: ApiKeyInterceptor reads key from SessionKeyCache
    LlmClientFactory->>LlmClientFactory: Call provider API
    Note over LlmClientFactory: Invalid API key
    LlmClientFactory-->>SettingsScreen: Return error

    SettingsScreen->>SettingsScreen: Show error message
    SettingsScreen-->>User: Display "Invalid API key" message

    User->>SettingsScreen: Dismiss error dialog
    SettingsScreen->>SettingsScreen: Clear error state

    User->>SettingsScreen: Enter correct API key and tap Connect
    SettingsScreen->>SessionKeyCache: saveKey(providerId, key)
    SettingsScreen->>LlmClientFactory: getModels(provider)
    LlmClientFactory->>LlmClientFactory: ApiKeyInterceptor reads key from SessionKeyCache
    LlmClientFactory->>LlmClientFactory: Call provider API
    LlmClientFactory-->>SettingsScreen: Return list of models

    User->>SettingsScreen: Select model
    SettingsScreen->>LlmClientFactory: createWithProvider(provider, model)
    LlmClientFactory->>LlmClientFactory: Create LLM client instance
    LLMClient->>LLMClient: testConnection()
    LLMClient-->>SettingsScreen: Connection successful

    SettingsScreen->>SettingsScreen: Save settings
    SettingsScreen-->>User: Return to chat screen
```

### 6. Events Screen - View Event Log

```mermaid
sequenceDiagram
    participant User
    participant MainActivity
    participant EventsScreen
    participant EventLog

    User->>MainActivity: Open app
    MainActivity->>MainActivity: Display Chat Screen

    User->>MainActivity: Tap Buddy logo
    MainActivity->>MainActivity: Show menu dropdown
    MainActivity->>MainActivity: User selects Events

    User->>EventsScreen: Events screen opens
    EventsScreen->>EventLog: get events (StateFlow)
    EventLog-->>EventsScreen: Return list of AppEvent

    EventsScreen->>EventsScreen: Collect events via collectAsState
    EventsScreen->>EventsScreen: Display events list with timestamps
    EventsScreen-->>User: Show chronological event history

    User->>EventsScreen: Scroll through events
    EventsScreen->>EventLog: Re-collect updated events
    EventLog-->>EventsScreen: Return updated list
    EventsScreen->>EventsScreen: Display additional events

    User->>EventsScreen: Tap back button
    EventsScreen->>MainActivity: onBack()
    MainActivity-->>User: Return to chat screen
```

### 7. Events Screen - Event Types Displayed

```mermaid
sequenceDiagram
    participant ChatViewModel
    participant EventLog
    participant EventsScreen
    participant User

    Note over ChatViewModel: User sends message
    ChatViewModel->>EventLog: add("I", "user input: X characters")
    EventLog->>EventLog: Create AppEvent
    EventLog-->>ChatViewModel: Event logged

    Note over ChatViewModel: Web search in progress
    ChatViewModel->>EventLog: add("I", "web fetch: sent")
    EventLog->>EventLog: Create AppEvent
    EventLog-->>ChatViewModel: Event logged

    Note over ChatViewModel: Web search succeeds
    ChatViewModel->>EventLog: add("I", "web fetch: success")
    EventLog->>EventLog: Create AppEvent
    EventLog-->>ChatViewModel: Event logged

    Note over ChatViewModel: Web search fails
    ChatViewModel->>EventLog: add("E", "web fetch: failed")
    EventLog->>EventLog: Create AppEvent
    EventLog-->>ChatViewModel: Event logged

    Note over ChatViewModel: LLM response
    ChatViewModel->>EventLog: add("I", "llm response: success")
    EventLog->>EventLog: Create AppEvent
    EventLog-->>ChatViewModel: Event logged

    Note over EventsScreen: User views events
    EventsScreen->>EventLog: collect events
    EventLog-->>EventsScreen: Return all AppEvents
    EventsScreen->>EventsScreen: Display events with levels
    EventsScreen-->>User: Show [E] errors, [W] warnings, [I] info
```

### 8. About Screen - Display App Information

```mermaid
sequenceDiagram
    participant User
    participant MainActivity
    participant AboutScreen

    User->>MainActivity: Open app
    MainActivity->>MainActivity: Display Chat Screen

    User->>MainActivity: Tap Buddy logo
    MainActivity->>MainActivity: Show menu dropdown
    MainActivity->>MainActivity: User selects About

    User->>AboutScreen: About screen opens

    AboutScreen->>AboutScreen: Display app version
    AboutScreen->>AboutScreen: Display build date
    AboutScreen->>AboutScreen: Display author info
    AboutScreen->>AboutScreen: Display contact links

    AboutScreen-->>User: Show app details and contact info
```

### 9. About Screen - Contact Developer

```mermaid
sequenceDiagram
    participant User
    participant AboutScreen
    participant EmailClient
    participant Browser

    User->>AboutScreen: Open about screen
    AboutScreen->>AboutScreen: Display contact information

    User->>AboutScreen: Tap on Email row
    AboutScreen->>EmailClient: Open email intent
    EmailClient->>EmailClient: Launch email app
    EmailClient-->>User: Show new email composition

    User->>EmailClient: Compose and send email
    EmailClient-->>User: Email sent successfully

    User->>AboutScreen: Tap on GitHub row
    AboutScreen->>Browser: Open GitHub URL
    Browser->>Browser: Launch browser
    Browser-->>User: Display GitHub repository

    User->>AboutScreen: Tap on LinkedIn row
    AboutScreen->>Browser: Open LinkedIn URL
    Browser->>Browser: Launch browser
    Browser-->>User: Display LinkedIn profile
```

### 10. Settings - Menu Navigation Flow

```mermaid
sequenceDiagram
    participant User
    participant MainActivity
    participant ChatScreen
    participant SettingsScreen
    participant EventsScreen
    participant AboutScreen

    User->>MainActivity: Open app
    MainActivity->>ChatScreen: Display chat interface

    User->>MainActivity: Tap Buddy logo
    MainActivity->>MainActivity: Show menu dropdown

    User->>MainActivity: Select Settings
    MainActivity->>SettingsScreen: Navigate to settings
    SettingsScreen-->>User: Show settings screen

    User->>SettingsScreen: Tap back button
    SettingsScreen->>MainActivity: Return to chat
    MainActivity->>ChatScreen: Resume chat screen

    User->>MainActivity: Tap Buddy logo again
    MainActivity->>MainActivity: Show menu dropdown

    User->>MainActivity: Select Events
    MainActivity->>EventsScreen: Navigate to events
    EventsScreen->>EventsScreen: Collect events from EventLog
    EventsScreen-->>User: Show events screen

    User->>EventsScreen: Tap back button
    EventsScreen->>MainActivity: Return to chat
    MainActivity->>ChatScreen: Resume chat screen

    User->>MainActivity: Tap Buddy logo again
    MainActivity->>MainActivity: Show menu dropdown

    User->>MainActivity: Select About
    MainActivity->>AboutScreen: Navigate to about
    AboutScreen-->>User: Show about screen

    User->>AboutScreen: Tap back button
    AboutScreen->>MainActivity: Return to chat
    MainActivity->>ChatScreen: Resume chat screen
```

### 11. Settings - Model Refresh

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant SessionKeyCache
    participant SettingsRepository
    participant LlmClientFactory

    User->>SettingsScreen: Open settings
    SettingsScreen->>SettingsScreen: Display current settings

    User->>SettingsScreen: Tap connect button to refresh models
    SettingsScreen->>SettingsScreen: handleConnect()
    SettingsScreen->>SessionKeyCache: saveKey(providerId, key) (if new)
    SettingsScreen->>LlmClientFactory: getModels(provider)
    LlmClientFactory->>LlmClientFactory: ApiKeyInterceptor reads key from SessionKeyCache
    LlmClientFactory->>LlmClientFactory: Call provider API
    LlmClientFactory-->>SettingsScreen: Return updated model list

    Note over SettingsScreen: Auto-select first model

    SettingsScreen->>SettingsScreen: Show new available models in dialog

    User->>SettingsScreen: Select newly available model
    SettingsScreen->>SettingsScreen: Update selected model via ModelSelectionDialog

    User->>SettingsScreen: Tap back to save
    SettingsScreen->>SettingsScreen: onSaveModelSettings(LlmSettings(model=selectedModel, ...))
    SettingsScreen->>SettingsRepository: updateAll(provider, model, ...)
    SettingsRepository-->>SettingsScreen: Settings saved

    SettingsScreen-->>User: Return to chat screen
```

---

**Note**: These diagrams represent high-level happy path scenarios for settings, events, and about functionality. Detailed error handling and edge cases are not shown for clarity.
