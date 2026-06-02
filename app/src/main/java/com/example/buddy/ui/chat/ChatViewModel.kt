package com.example.buddy.ui.chat

import android.app.Application
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import com.example.buddy.ext.FetchedUrl
import com.example.buddy.ext.LlmClient
import com.example.buddy.ext.LlmGenerationConfig
import com.example.buddy.ext.LlmMessage
import com.example.buddy.ext.LlmModel
import com.example.buddy.ext.LlmRole
import com.example.buddy.ext.UrlFetcher
import com.example.buddy.ext.WebSearch
import com.example.buddy.ext.WebSearchHelper
import com.example.buddy.service.BuddyForegroundService
import com.example.buddy.service.ServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.Locale
import com.example.buddy.data.ChatMessage as UiChatMessage

private const val MAX_IMAGE_DIMENSION = 1440
private const val JPEG_QUALITY = 85
private const val MAX_FILE_SIZE_BYTES = 100 * 1024
private const val TAG = "Chat"

val SUPPORTED_TEXT_EXTENSIONS = listOf(
    ".txt", ".md", ".log", ".rst", ".adoc", ".asciidoc", ".rtf", ".json", ".xml", ".html", ".py", ".js"
)

data class ChatUiState(
    val messages: List<UiChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val pendingImageBase64: String? = null,
    val pendingFileUri: Uri? = null,
    val pendingFileName: String? = null,
    val availableModels: List<LlmModel> = emptyList(),
    val selectedModel: String = "",
    val isOffline: Boolean = false,
    val generationConfig: LlmGenerationConfig = LlmGenerationConfig(),
    val webSearchError: String? = null,
    val fileTooLargeError: String? = null,
    val urlFetchInProgress: Boolean = false,
    val urlFetchWarnings: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val summaries: List<Summary> = emptyList()
)

class ChatViewModel(
    private val application: Application,
) : ViewModel() {

    private var llmClient: LlmClient? = null
    private var webSearch: WebSearch? = null
    private var urlFetcher: UrlFetcher? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val processingLock = Mutex()

    fun updateClient(client: LlmClient?, web: WebSearch?, fetcher: UrlFetcher?) {
        val clientChanged = llmClient !== client
        llmClient = client
        webSearch = web
        urlFetcher = fetcher
        val isOffline = client == null
        if (clientChanged && client != null) {
            client.activeModel = client.defaultModel
        }
        _uiState.update { state ->
            val messages = if (state.messages.isEmpty() && client != null) {
                listOf(
                    UiChatMessage(
                        role = Role.ASSISTANT,
                        content = "Hey! I'm Buddy. I can answer questions, write code, analyze images, and search the web. What can I help you with?",
                        isComplete = true
                    )
                )
            } else {
                state.messages
            }
            val model = when {
                client != null -> client.activeModel
                else -> ""
            }
            state.copy(
                messages = messages,
                selectedModel = model,
                isOffline = isOffline
            )
        }
        if (client != null) {
            loadAvailableModels()
        }
    }

    fun toggleReasoningEffort() {
        val current = _uiState.value.generationConfig.reasoningEffort
        val next = llmClient?.toggleReasoning(current) ?: AppResources.ReasoningEffort.HIGH
        _uiState.update {
            it.copy(generationConfig = it.generationConfig.copy(reasoningEffort = next))
        }
    }

    fun clearChat() {
        _uiState.update { state ->
            val greeting = if (llmClient != null) {
                listOf(
                    UiChatMessage(
                        role = Role.ASSISTANT,
                        content = "Hey! I'm Buddy. I can answer questions, write code, analyze images, and search the web. What can I help you with?",
                        isComplete = true
                    )
                )
            } else {
                emptyList()
            }
            state.copy(
                messages = greeting,
                inputText = "",
                pendingImageBase64 = null,
                pendingFileUri = null,
                pendingFileName = null,
                webSearchError = null,
                fileTooLargeError = null,
                urlFetchWarnings = emptyList(),
                isLoading = false,
                isStreaming = false,
                urlFetchInProgress = false,
                summaries = emptyList()
            )
        }
    }

    private fun loadAvailableModels() {
        val client = llmClient ?: return
        viewModelScope.launch {
            val models = client.getModels()
            _uiState.update {
                it.copy(
                    availableModels = models.ifEmpty { it.availableModels },
                    selectedModel = if (models.isEmpty()) {
                        it.selectedModel
                    } else {
                        it.selectedModel.takeIf { s -> models.any { m -> m.id == s } }
                            ?: llmClient?.activeModel?.takeIf { m -> models.any { mod -> mod.id == m } }
                            ?: models.firstOrNull()?.id ?: it.selectedModel
                    }
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        llmClient?.activeModel = modelId
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onImagePicked(base64: String?) {
        _uiState.update {
            it.copy(
                pendingImageBase64 = base64,
                pendingFileUri = null,
                pendingFileName = null,
                fileTooLargeError = null
            )
        }
    }

    fun onClearImage() {
        _uiState.update { it.copy(pendingImageBase64 = null) }
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(pendingFileUri = null, pendingFileName = null, fileTooLargeError = null) }
            return
        }
        val fileName = getFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in SUPPORTED_TEXT_EXTENSIONS.map { it.removePrefix(".") }) {
            _uiState.update { it.copy(pendingFileUri = null, pendingFileName = null, fileTooLargeError = "Unsupported file type: .$ext") }
            return
        }
        val fileSize = getFileSize(uri)
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            _uiState.update { it.copy(pendingFileUri = null, pendingFileName = null, fileTooLargeError = "File too large (${formatFileSize(fileSize)}, max 100KB)") }
            return
        }
        _uiState.update { it.copy(pendingFileUri = uri, pendingFileName = fileName, pendingImageBase64 = null, fileTooLargeError = null) }
    }

    fun onClearFile() {
        _uiState.update { it.copy(pendingFileUri = null, pendingFileName = null, fileTooLargeError = null) }
    }

    fun toggleWebSearch() {
        _uiState.update { it.copy(webSearchEnabled = !it.webSearchEnabled) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank()) return

        val correlationId = java.util.UUID.randomUUID().toString()
        val urls = extractUrls(text)
        val urlData = if (urls.isNotEmpty()) urls.joinToString("\n") else null
        EventLog.info(TAG, "User input: ${text.length} chars" + if (urls.isNotEmpty()) ", ${urls.size} URL(s)" else "", data = urlData, correlationId = correlationId)
        val savedImageBase64 = state.pendingImageBase64
        val savedFileUri = state.pendingFileUri
        val savedFileName = state.pendingFileName

        if (savedImageBase64 != null) {
            EventLog.info(TAG, "Image attached", "Size: ${savedImageBase64.length} chars", correlationId = correlationId)
        }
        if (savedFileUri != null) {
            EventLog.info(TAG, "File attached", "Name: $savedFileName", correlationId = correlationId)
        }

        _uiState.update { it.copy(
            webSearchError = null,
            fileTooLargeError = null
        )}

        var fileText: String? = null
        if (savedFileUri != null) {
            val result = readTextFile(savedFileUri)
            if (result.isFailure) {
                _uiState.update { it.copy(fileTooLargeError = result.exceptionOrNull()?.message ?: "Could not read file") }
                return
            }
            fileText = result.getOrNull()
        }

        _uiState.update { it.copy(
            inputText = "",
            pendingImageBase64 = null,
            pendingFileUri = null,
            pendingFileName = null
        )}

        viewModelScope.launch {
            var fetchedUrls: List<FetchedUrl> = emptyList()
            val fetcher = urlFetcher
            if (urls.isNotEmpty() && fetcher != null) {
                _uiState.update { it.copy(urlFetchInProgress = true, urlFetchWarnings = emptyList()) }
                
                ServiceHelper.onOperationStart(application)
                BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.URL_FETCHING, "Fetching URL content...")
                
                val result = fetcher.fetchAll(urls, correlationId)
                fetchedUrls = result.urls
                _uiState.update { it.copy(urlFetchInProgress = false, urlFetchWarnings = result.warnings) }
                
                ServiceHelper.onOperationEnd(application)
            }

            val userMsg = UiChatMessage(
                role = Role.USER,
                content = text,
                imageBase64 = savedImageBase64,
                attachedFileUri = savedFileUri,
                attachedFileName = savedFileName,
                attachedFileText = fileText
            )

            _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }

            processingLock.withLock {
                streamRealResponse(userMsg, fetchedUrls, correlationId)
            }
        }
    }

    private suspend fun streamRealResponse(userMsg: UiChatMessage, fetchedUrls: List<FetchedUrl>, correlationId: String) {
        val client = llmClient ?: return
        val search = webSearch
        val assistantId = java.util.UUID.randomUUID().toString()
        val state = _uiState.value
        val shouldSearch = state.webSearchEnabled && search != null && userMsg.content.isNotBlank()

        var searchResults: List<com.example.buddy.ext.SearchResult> = emptyList()
        var searchSkipped = false

        if (shouldSearch) {
            ServiceHelper.onOperationStart(application)
            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.WEB_SEARCHING, "Searching the web...")
            
            val currentSummaries = _uiState.value.summaries
            val helper = WebSearchHelper(client, search)
            val result = helper.search(userMsg.content, currentSummaries, correlationId)
            
            searchSkipped = result.skipped
            searchResults = result.rawResults
            result.errorMessage?.let { error ->
                val errorMsg = when {
                    error.contains("401") || error.contains("403") -> "Invalid web search API key"
                    error.contains("429") -> "Web search usage limit exceeded"
                    error == "Web search returned no results" -> error
                    else -> "Web search failed: $error"
                }
                _uiState.update { it.copy(webSearchError = errorMsg) }
            }
            
            ServiceHelper.onOperationEnd(application)
        }

        _uiState.update {
            it.copy(
                messages = it.messages + UiChatMessage(
                    id = assistantId,
                    role = Role.ASSISTANT,
                    content = "",
                    isStreaming = true,
                    webSearchUsed = searchResults.isNotEmpty(),
                    webSearchSkipped = searchSkipped
                ),
                isLoading = false,
                isStreaming = true
            )
        }

        val messages = buildLlmMessages(searchResults, fetchedUrls)
        val model = client.activeModel
        val config = _uiState.value.generationConfig

        try {
            ServiceHelper.onOperationStart(application)
            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.LLM_STREAMING, "Generating response...")
            
            client.streamCompletionWithLogging(messages, model, config, correlationId).collect { token ->
                _uiState.update { s ->
                    s.copy(
                        messages = s.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(content = msg.content + token) else msg
                        }
                    )
                }
            }
            _uiState.update { s ->
                s.copy(
                    messages = s.messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(isStreaming = false, isComplete = true) else msg
                    },
                    isStreaming = false
                )
            }

            val fullAssistantContent = _uiState.value.messages.lastOrNull { it.id == assistantId }?.content ?: ""
            if (fullAssistantContent.isNotBlank()) {
                try {
                    val summary = client.generateSummary(userMsg.content, fullAssistantContent, model = model)
                    _uiState.update { state ->
                        val newSummaries = state.summaries + summary
                        if (newSummaries.size > AppResources.summaries.maxSummaries) {
                            val nCompress = AppResources.summaries.maxSummaries / 2
                            val batch = newSummaries.take(nCompress)
                            try {
                                val compressed = client.compressSummaries(batch, model = model)
                                val updatedSummaries = listOf(Summary("Earlier conversation", compressed.points)) + newSummaries.drop(nCompress)
                                state.copy(summaries = updatedSummaries)
                            } catch (e: Exception) {
                                EventLog.error(TAG, "Failed to compress summaries", e.message)
                                state.copy(summaries = newSummaries)
                            }
                        } else {
                            state.copy(summaries = newSummaries)
                        }
                    }
                } catch (e: Exception) {
                    EventLog.error(TAG, "Failed to generate summary", e.message)
                }
            }
        } catch (e: Exception) {
            _uiState.update { s ->
                s.copy(
                    messages = s.messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(content = "Error: ${e.message}", isStreaming = false, isComplete = true) else msg
                    },
                    isStreaming = false
                )
            }
        } finally {
            ServiceHelper.onOperationEnd(application)
            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.IDLE, "")
        }
    }

    private fun buildLlmMessages(
        searchResults: List<com.example.buddy.ext.SearchResult> = emptyList(),
        fetchedUrls: List<FetchedUrl> = emptyList()
    ): List<LlmMessage> {
        val systemParts = mutableListOf<String>()
        systemParts.add("## Instructions\n" + AppResources.llm.defaultSystemMessage)

        val summaries = _uiState.value.summaries
        if (summaries.isNotEmpty()) {
            systemParts.add(AppResources.summaries.formatSummariesContext(summaries))
        }

        if (fetchedUrls.isNotEmpty() || searchResults.isNotEmpty()) {
            val webParts = mutableListOf<String>()
            webParts.add(AppResources.summaries.webDataHeader)

            if (fetchedUrls.isNotEmpty()) {
                webParts.add("### Fetched URL")
                for (fu in fetchedUrls) {
                    webParts.add("#### ${fu.url}")
                    webParts.add(fu.content)
                }
            }

            if (searchResults.isNotEmpty()) {
                webParts.add("### Web Search")
                for (sr in searchResults) {
                    webParts.add("#### ${sr.title}")
                    webParts.add(sr.content)
                }
            }

            systemParts.add(webParts.joinToString("\n"))
        }

        val result = mutableListOf(
            LlmMessage(role = LlmRole.SYSTEM, content = systemParts.joinToString("\n\n"))
        )

        val allMessages = _uiState.value.messages
            .filter { it.role != Role.SYSTEM }
            .filterNot { it.role == Role.ASSISTANT && it.content.isEmpty() && it.isStreaming }

        val currentUserMsg = allMessages.lastOrNull()
        val history = if (currentUserMsg != null) allMessages.dropLast(1) else allMessages

        val pairs = mutableListOf<Pair<UiChatMessage, UiChatMessage>>()
        var i = 0
        while (i < history.size - 1) {
            if (history[i].role == Role.USER && history[i + 1].role == Role.ASSISTANT) {
                pairs.add(Pair(history[i], history[i + 1]))
                i += 2
            } else {
                i++
            }
        }

        val recentPairs = pairs.takeLast(AppResources.summaries.maxQaPairs)
        for (pair in recentPairs) {
            result.add(LlmMessage(role = LlmRole.USER, content = pair.first.content))
            result.add(LlmMessage(role = LlmRole.ASSISTANT, content = pair.second.content))
        }

        if (currentUserMsg != null) {
            result.add(
                LlmMessage(
                    role = LlmRole.USER,
                    content = buildMessageContent(currentUserMsg),
                    imageBase64 = currentUserMsg.imageBase64
                )
            )
        }

        return result
    }

    private fun buildMessageContent(message: UiChatMessage): String {
        val parts = mutableListOf<String>()
        if (message.attachedFileText != null && message.attachedFileName != null) {
            parts.add("[File: ${message.attachedFileName}]\n${message.attachedFileText}")
        }
        parts.add(message.content)
        return parts.joinToString("\n\n")
    }

    private fun extractUrls(text: String): List<String> {
        val pattern = Regex("""https://[^\s<>"{}|\\^`\[\]]+""")
        return pattern.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'') }
            .toList()
    }

    fun bitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val maxDim = maxOf(bitmap.width, bitmap.height)
            val scaledBitmap = if (maxDim > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDim
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                bitmap.scale(newWidth, newHeight)
            } else {
                bitmap
            }
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            if (scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            null
        }
    }

    private fun readTextFile(uri: Uri): Result<String> {
        return try {
            val inputStream = application.contentResolver.openInputStream(uri) ?: return Result.failure(Exception("Could not open file"))
            val bytes = inputStream.readBytes()
            inputStream.close()
            if (bytes.size > MAX_FILE_SIZE_BYTES) {
                return Result.failure(Exception("File too large (${formatFileSize(bytes.size.toLong())}, max 100KB)"))
            }
            val content = String(bytes, Charsets.UTF_8)
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = application.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return it.getString(nameIndex)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = application.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0) return it.getLong(sizeIndex)
            }
        }
        return 0
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

class ChatViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(application) as T
    }
}
