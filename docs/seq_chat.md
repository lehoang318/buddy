# Buddy AI Assistant - Sequence Diagrams

## Chat Scenarios

### 1. Basic Chat (No Web Search, No URL)

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant LLMClient
    participant SettingsRepository

    User->>ChatScreen: Type message and send
    ChatScreen->>ViewModel: onInputChange(message)
    ViewModel->>ViewModel: Validate message
    ViewModel->>SettingsRepository: getSettings()
    SettingsRepository-->>ViewModel: Return settings (model, apiKey, etc.)
    ViewModel->>ViewModel: Create request with settings
    ViewModel->>LLMClient: streamCompletion(messages, model, config)
    LLMClient->>LLMClient: Prepare API request
    LLMClient->>LLMClient: Send to provider
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Process and accumulate response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display response
```

### 2. Chat with Web Search Enabled

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant LLMClient
    participant WebSearchHelper
    participant WebSearch

    User->>ChatScreen: Type message about current events
    ChatScreen->>ViewModel: onInputChange(message)
    ViewModel->>ViewModel: Check web search enabled flag
    ViewModel->>WebSearchHelper: search(message)
    WebSearchHelper->>LLMClient: generateSearchQuery(message)
    LLMClient-->>WebSearchHelper: Return focused search query
    WebSearchHelper->>WebSearch: search(query)
    WebSearch->>WebSearch: Query web search provider
    WebSearch-->>WebSearchHelper: Return search results
    WebSearchHelper-->>ViewModel: Return formatted results
    ViewModel->>ViewModel: Combine message + search results
    ViewModel->>LLMClient: streamCompletion(combinedContext)
    LLMClient->>LLMClient: Prepare API request with context
    LLMClient->>LLMClient: Send to provider
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Process response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display response with citations
```

### 3. Chat with URL Context

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant UrlFetcher
    participant LLMClient

    User->>ChatScreen: Type message with URL
    ChatScreen->>ViewModel: onInputChange(message + url)
    ViewModel->>ViewModel: Detect URL in message
    ViewModel->>UrlFetcher: fetchUrl(url)
    UrlFetcher->>UrlFetcher: Make HTTP request to URL
    UrlFetcher-->>ViewModel: Return webpage content
    ViewModel->>ViewModel: Extract relevant content from URL
    ViewModel->>ViewModel: Combine message + URL content
    ViewModel->>LLMClient: streamCompletion(combinedContext, model, config)
    LLMClient->>LLMClient: Prepare API request with context
    LLMClient->>LLMClient: Send to provider
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Process and accumulate response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display response referencing URL
```

### 4. Chat with Image Attachment

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant ImageProcessor
    participant LLMClient

    User->>ChatScreen: Take photo or select image
    ChatScreen->>ViewModel: onImagePicked(imageUri)
    ViewModel->>ViewModel: Process image URI
    ViewModel->>ImageProcessor: Convert to base64
    ImageProcessor-->>ViewModel: Return base64 image data
    User->>ChatScreen: Type message about image
    ChatScreen->>ViewModel: onInputChange(message)
    ViewModel->>ViewModel: Check for pending image
    ViewModel->>ViewModel: Create multimodal request
    ViewModel->>LLMClient: streamCompletion(messages, model, config)
    LLMClient->>LLMClient: Prepare multimodal API request
    LLMClient->>LLMClient: Send to provider
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Process and accumulate response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display response about image
```

### 5. Chat with File Attachment

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant FileProcessor
    participant LLMClient

    User->>ChatScreen: Select text/code file
    ChatScreen->>ViewModel: onFilePicked(fileUri)
    ViewModel->>ViewModel: Process file URI
    ViewModel->>FileProcessor: Read file content
    FileProcessor-->>ViewModel: Return file text content
    User->>ChatScreen: Type message about file
    ChatScreen->>ViewModel: onInputChange(message)
    ViewModel->>ViewModel: Check for pending file
    ViewModel->>ViewModel: Create request with file context
    ViewModel->>LLMClient: streamCompletion(messages, model, config)
    LLMClient->>LLMClient: Prepare API request with context
    LLMClient->>LLMClient: Send to provider
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Process and accumulate response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display response referencing file
```

### 6. Chat with Web Search + URL Context

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant UrlFetcher
    participant WebSearchHelper
    participant WebSearch
    participant LLMClient

    User->>ChatScreen: Type message with URL, enable web search
    ChatScreen->>ViewModel: onInputChange(message + url)
    ViewModel->>ViewModel: Detect URL and web search enabled
    ViewModel->>UrlFetcher: fetchAll(urls)
    UrlFetcher->>UrlFetcher: Make HTTP requests to URLs
    UrlFetcher-->>ViewModel: Return webpage content
    ViewModel->>ViewModel: Extract content from URLs
    ViewModel->>WebSearchHelper: search(message)
    WebSearchHelper->>LLMClient: generateSearchQuery(message)
    LLMClient-->>WebSearchHelper: Return search query
    WebSearchHelper->>WebSearch: search(query)
    WebSearch->>WebSearch: Query web search provider
    WebSearch-->>WebSearchHelper: Return search results
    WebSearchHelper-->>ViewModel: Return formatted results
    ViewModel->>ViewModel: Combine message + URL + search results
    ViewModel->>LLMClient: streamCompletion(fullContext)
    LLMClient->>LLMClient: Prepare API request with all context
    LLMClient->>LLMClient: Send to provider
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Process response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display comprehensive response
```

### 7. Chat with Connection Error

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant LLMClient
    participant SettingsRepository

    User->>ChatScreen: Type message and send
    ChatScreen->>ViewModel: onInputChange(message)
    ViewModel->>SettingsRepository: getSettings()
    SettingsRepository-->>ViewModel: Return settings
    ViewModel->>LLMClient: streamCompletion(messages, model, config)
    LLMClient->>LLMClient: Prepare API request
    LLMClient->>LLMClient: Send to provider
    Note over LLMClient: Connection timeout or error
    LLMClient-->>ViewModel: Throw exception
    ViewModel->>ViewModel: Catch exception
    ViewModel->>ChatScreen: Update UI with error message
    ChatScreen-->>User: Display "Error: [exception message]" in chat
```

### 8. Chat with Web Search Error

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant WebSearchHelper
    participant WebSearch
    participant LLMClient

    User->>ChatScreen: Type message about current events
    ChatScreen->>ViewModel: onInputChange(message)
    ViewModel->>ViewModel: Check web search enabled
    ViewModel->>WebSearchHelper: search(message)
    WebSearchHelper->>LLMClient: generateSearchQuery(message)
    LLMClient-->>WebSearchHelper: Return search query
    WebSearchHelper->>WebSearch: search(query)
    Note over WebSearch: Invalid API key or network error
    WebSearch-->>WebSearchHelper: Return error
    WebSearchHelper-->>ViewModel: Return error message
    ViewModel->>ViewModel: Set webSearchError in uiState
    ViewModel->>ViewModel: Continue without web search results
    ViewModel->>LLMClient: streamCompletion(messages, model, config)
    LLMClient->>LLMClient: Prepare API request
    LLMClient->>LLMClient: Send to provider
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Process and accumulate response
    ViewModel->>ChatScreen: Update UI with response + error pill
    ChatScreen-->>User: Display response with error notification
```

### 9. Chat with Invalid API Key

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant LLMClient
    participant SettingsRepository

    User->>ChatScreen: Type message and send
    ChatScreen->>ViewModel: onInputChange(message)
    ViewModel->>SettingsRepository: getSettings()
    SettingsRepository-->>ViewModel: Return settings
    ViewModel->>LLMClient: streamCompletion(messages, model, config)
    LLMClient->>LLMClient: Prepare API request
    LLMClient->>LLMClient: Send to provider
    Note over LLMClient: Provider rejects invalid key (HTTP 401)
    LLMClient-->>ViewModel: Throw exception with error details
    ViewModel->>ViewModel: Catch exception
    ViewModel->>ChatScreen: Update UI with error message
    ChatScreen-->>User: Display "Error: API error 401: ..." in chat
```

### 10. Chat with Model Switch During Session

```mermaid
sequenceDiagram
    participant User
    participant ChatScreen
    participant ViewModel
    participant LLMClient
    participant SettingsRepository

    User->>ChatScreen: Send message
    ChatScreen->>ViewModel: sendMessage()
    ViewModel->>SettingsRepository: getSettings()
    SettingsRepository-->>ViewModel: Return current settings
    ViewModel->>LLMClient: streamCompletion(messages, model, config)
    LLMClient->>LLMClient: Process with current model
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Accumulate response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display response

    User->>ChatScreen: Tap model name in top bar
    ChatScreen->>ChatScreen: Show ModelSelectionDialog
    User->>ChatScreen: Select new model from dialog
    ChatScreen->>ViewModel: selectModel(newModel)
    ViewModel->>ViewModel: Update selected model in uiState
    ViewModel->>ChatScreen: Update UI to show new model
    ChatScreen-->>User: Display new model in UI

    User->>ChatScreen: Send new message
    ChatScreen->>ViewModel: sendMessage()
    ViewModel->>LLMClient: streamCompletion(messages, newModel, config)
    LLMClient->>LLMClient: Process with new model
    LLMClient-->>ViewModel: Stream response tokens
    ViewModel->>ViewModel: Accumulate response
    ViewModel->>ChatScreen: Update UI with response
    ChatScreen-->>User: Display response from new model
```

---

**Note**: These diagrams represent high-level happy path scenarios with alternative branches for common cases. Detailed error handling, retry logic, and edge cases are not shown for clarity.
