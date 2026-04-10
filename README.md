# Buddy — Android AI Assistant

A Jetpack Compose chat UI prototype for a multimodal LLM assistant app.

## Features (prototype)
- Dark-themed chat UI with streaming simulation
- User & assistant message bubbles
- Code block rendering (monospace, highlighted)
- Image attachment (gallery picker)
- Web search toggle with pill indicator
- Animated typing indicator
- Blinking streaming cursor

---

## Setup in Android Studio

### 1. Create a new project
- Template: **Empty Activity**
- Language: **Kotlin**
- Minimum SDK: **API 26**
- Build config: **Kotlin DSL**

### 2. Copy these files into your project

Replace / create the following files:

```
app/src/main/java/com/example/buddy/
├── MainActivity.kt
├── data/
│   ├── ChatMessage.kt
│   └── Models.kt
└── ui/
    ├── chat/
    │   ├── ChatScreen.kt
    │   └── ChatViewModel.kt
    └── theme/
        └── Theme.kt

app/src/main/AndroidManifest.xml
app/src/main/res/values/themes.xml
app/build.gradle.kts          ← replace the generated one
```

### 3. Sync Gradle
Click **"Sync Now"** in the yellow bar or go to **File → Sync Project with Gradle Files**.

### 4. Run on emulator
- Select a Pixel device (API 30+) in the AVD Manager
- Press ▶ Run

---

## Wiring a real LLM provider

In `ChatViewModel.kt`, replace `streamMockResponse()` with a real OkHttp SSE call:

```kotlin
// 1. Build request
val request = ChatRequest(
    model = state.provider.model,
    messages = state.messages.map { ApiMessage(it.role.name.lowercase(), it.content) },
    stream = true
)

// 2. POST to provider base URL
val httpRequest = Request.Builder()
    .url("${state.provider.baseUrl}/chat/completions")
    .addHeader("Authorization", "Bearer ${state.provider.apiKey}")
    .addHeader("Content-Type", "application/json")
    .post(Gson().toJson(request).toRequestBody("application/json".toMediaType()))
    .build()

// 3. Stream SSE lines
OkHttpClient().newCall(httpRequest).execute().body?.source()?.let { src ->
    while (!src.exhausted()) {
        val line = src.readUtf8Line() ?: break
        if (line.startsWith("data: ") && line != "data: [DONE]") {
            val delta = parseStreamChunk(line.removePrefix("data: "))
            // emit delta to UI state
        }
    }
}
```

---

## Web Search Integration

In `ChatViewModel.kt`, before building the LLM request:

```kotlin
if (webSearchEnabled) {
    val results = searchApi.query(userMessage)  // Brave / SerpAPI / Searxng
    systemPrompt = "Use these search results:\n$results"
}
```
