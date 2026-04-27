# Buddy AI Assistant - Sequence Diagrams

## Other Scenarios

### 1. Settings - Initial Configuration (Happy Path)

```mermaid
sequenceDiagram
    participant User
    participant MainActivity
    participant SettingsScreen
    participant SettingsRepository
    participant LlmClientFactory
    participant LLMClient

    User->>MainActivity: Open app
    MainActivity->>MainActivity: Load settings from repository
    MainActivity->>MainActivity: Create LLM client if configured
    MainActivity->>MainActivity: Display Chat Screen

    User->>MainActivity: Tap Buddy logo
    MainActivity->>MainActivity: Show menu dropdown
    MainActivity->>MainActivity: User selects Settings

    User->>SettingsScreen: Settings screen opens
    SettingsScreen->>SettingsRepository: getSettings()
    SettingsRepository-->>SettingsScreen: Return saved settings

    User->>SettingsScreen: Select provider from dropdown
    SettingsScreen->>SettingsScreen: Update selected provider

    User->>SettingsScreen: Enter API key
    SettingsScreen->>SettingsScreen: Update API key field

    User->>SettingsScreen: Tap sync button
    SettingsScreen->>SettingsScreen: handleConnect()
    SettingsScreen->>LlmClientFactory: getModels(provider, apiKey)
    LlmClientFactory->>LlmClientFactory: Call provider API
    LlmClientFactory-->>SettingsScreen: Return list of models

    Note over SettingsScreen: Auto-select first model: selectedModel = models.first().id

    SettingsScreen->>LlmClientFactory: createWithProviderId(provider, apiKey, selectedModel)
    LlmClientFactory->>LlmClientFactory: Create LLM client instance
    LLMClient->>LLMClient: testConnection()
    LLMClient-->>SettingsScreen: Connection successful

    SettingsScreen->>SettingsRepository: updateAll(settings)
    SettingsRepository->>SettingsRepository: Save settings locally
    SettingsRepository-->>SettingsScreen: Settings saved

    User->>SettingsScreen: Tap back button
    SettingsScreen->>SettingsScreen: onSaveModelSettings(LlmSettings(model=selectedModel, ...))
    SettingsScreen->>MainActivity: onBack()
    MainActivity-->>User: Return to chat screen
```

### 2. Settings - Change Model

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

### 3. Settings - Change Web Search Provider

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant SettingsRepository
    participant TavilyWebSearch

    User->>SettingsScreen: Open settings
    SettingsScreen->>SettingsScreen: Display current settings

    User->>SettingsScreen: Select web search provider from dropdown
    SettingsScreen->>SettingsScreen: Update selected provider

    User->>SettingsScreen: Enter Tavily API key
    SettingsScreen->>SettingsScreen: Update API key field

    User->>SettingsScreen: Tap back button
    SettingsScreen->>SettingsRepository: updateAll(settings)
    SettingsRepository->>SettingsRepository: Save settings locally
    SettingsRepository-->>SettingsScreen: Settings saved

    SettingsScreen->>MainActivity: onSettingsSaved()
    MainActivity->>MainActivity: Create TavilyWebSearch instance
    MainActivity->>MainActivity: Update web search capability
    MainActivity-->>User: Return to chat screen
```

### 4. Settings - Connection Error Handling

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant LlmClientFactory
    participant LLMClient

    User->>SettingsScreen: Enter API key and tap sync
    SettingsScreen->>LlmClientFactory: getModels(provider, apiKey)
    LlmClientFactory->>LlmClientFactory: Call provider API
    Note over LlmClientFactory: Invalid API key
    LlmClientFactory-->>SettingsScreen: Return error

    SettingsScreen->>SettingsScreen: Show error dialog
    SettingsScreen-->>User: Display "Invalid API key" message

    User->>SettingsScreen: Dismiss error dialog
    SettingsScreen->>SettingsScreen: Clear error state

    User->>SettingsScreen: Enter correct API key
    SettingsScreen->>LlmClientFactory: getModels(provider, apiKey)
    LlmClientFactory->>LlmClientFactory: Call provider API
    LlmClientFactory-->>SettingsScreen: Return list of models

    User->>SettingsScreen: Select model and tap sync
    SettingsScreen->>LlmClientFactory: createWithProviderId(provider, apiKey, model)
    LlmClientFactory->>LlmClientFactory: Create LLM client instance
    LLMClient->>LLMClient: testConnection()
    LLMClient-->>SettingsScreen: Connection successful

    SettingsScreen->>SettingsScreen: Save settings
    SettingsScreen-->>User: Return to chat screen
```

### 5. Events Screen - View Event Log

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

### 6. Events Screen - Event Types Displayed

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

### 7. About Screen - Display App Information

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

### 8. About Screen - Contact Developer

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

### 9. Settings - Menu Navigation Flow

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

### 10. Settings - Model Refresh

```mermaid
sequenceDiagram
    participant User
    participant SettingsScreen
    participant SettingsRepository
    participant LlmClientFactory

    User->>SettingsScreen: Open settings
    SettingsScreen->>SettingsScreen: Display current settings

    User->>SettingsScreen: Tap sync button to refresh models
    SettingsScreen->>SettingsScreen: handleConnect()
    SettingsScreen->>LlmClientFactory: getModels(provider, apiKey)
    LlmClientFactory->>LlmClientFactory: Call provider API
    LlmClientFactory-->>SettingsScreen: Return updated model list

    Note over SettingsScreen: Auto-select first model

    SettingsScreen->>SettingsScreen: Show new available models in dialog

    User->>SettingsScreen: Select newly available model
    SettingsScreen->>SettingsScreen: Update selected model via ModelSelectionDialog

    User->>SettingsScreen: Tap back to save
    SettingsScreen->>SettingsScreen: onSaveModelSettings(LlmSettings(model=selectedModel, ...))
    SettingsScreen->>SettingsRepository: updateAll(settings)
    SettingsRepository-->>SettingsScreen: Settings saved

    SettingsScreen-->>User: Return to chat screen
```

---

**Note**: These diagrams represent high-level happy path scenarios for settings, events, and about functionality. Detailed error handling and edge cases are not shown for clarity.
