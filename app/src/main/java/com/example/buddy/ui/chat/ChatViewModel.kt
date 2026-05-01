package com.example.buddy.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.buddy.data.ChatMessage as UiChatMessage
import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmDefaults
import com.example.buddy.data.Role
import com.example.buddy.ext.LlmClient
import com.example.buddy.ext.LlmGenerationConfig
import com.example.buddy.ext.LlmMessage
import com.example.buddy.ext.LlmModel
import com.example.buddy.ext.LlmRole
import com.example.buddy.ext.UrlFetcher
import com.example.buddy.ext.WebSearch
import com.example.buddy.service.BuddyForegroundService
import com.example.buddy.service.ServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

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
    val isStreaming: Boolean = false
)

class ChatViewModel(
    private val application: Application,
) : ViewModel() {

    private var llmClient: LlmClient? = null
    private var webSearch: WebSearch? = null
    private var urlFetcher: UrlFetcher? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

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

    fun updateConfig(config: LlmGenerationConfig, model: String) {
        _uiState.update {
            it.copy(
                generationConfig = config,
                selectedModel = model.takeIf { m -> m.isNotBlank() } ?: it.selectedModel
            )
        }
    }

    fun toggleReasoningEffort() {
        val current = _uiState.value.generationConfig.reasoningEffort
        val next = llmClient?.toggleReasoning(current) ?: LlmDefaults.ReasoningEffort.HIGH
        _uiState.update {
            it.copy(generationConfig = it.generationConfig.copy(reasoningEffort = next))
        }
    }

    fun setOfflineMode(offline: Boolean) {
        _uiState.update { it.copy(isOffline = offline) }
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
                urlFetchInProgress = false
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
        if (text.isBlank() && state.pendingImageBase64 == null && state.pendingFileUri == null) return

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
            inputText = "",
            pendingImageBase64 = null,
            pendingFileUri = null,
            pendingFileName = null,
            webSearchError = null,
            fileTooLargeError = null
        )}

        viewModelScope.launch {
            var fetchedUrlText: String? = null
            val fetcher = urlFetcher
            if (urls.isNotEmpty() && fetcher != null) {
                _uiState.update { it.copy(urlFetchInProgress = true, urlFetchWarnings = emptyList()) }
                
                ServiceHelper.onOperationStart(application)
                BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.URL_FETCHING, "Fetching URL content...")
                
                val result = fetcher.fetchAll(urls, correlationId)
                fetchedUrlText = result.fetchedText
                _uiState.update { it.copy(urlFetchInProgress = false, urlFetchWarnings = result.warnings) }
                
                ServiceHelper.onOperationEnd(application)
            }

            var fileText: String? = null
            if (savedFileUri != null) {
                val result = readTextFile(savedFileUri)
                if (result.isFailure) {
                    _uiState.update { it.copy(fileTooLargeError = result.exceptionOrNull()?.message ?: "Could not read file") }
                    return@launch
                }
                fileText = result.getOrNull()
            }

            val userMsg = UiChatMessage(
                role = Role.USER,
                content = text,
                fetchedUrlText = fetchedUrlText,
                imageBase64 = savedImageBase64,
                attachedFileUri = savedFileUri,
                attachedFileName = savedFileName,
                attachedFileText = fileText
            )

            _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }

            streamRealResponse(userMsg, correlationId)
        }
    }

    private suspend fun streamRealResponse(userMsg: UiChatMessage, correlationId: String) {
        val client = llmClient ?: return
        val search = webSearch
        val assistantId = java.util.UUID.randomUUID().toString()
        val state = _uiState.value
        val shouldSearch = state.webSearchEnabled && search != null && userMsg.content.isNotBlank()

        var searchResultsText: String? = null

        if (shouldSearch) {
            ServiceHelper.onOperationStart(application)
            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.WEB_SEARCHING, "Searching the web...")
            
            val helper = com.example.buddy.ext.WebSearchHelper(client, search)
            val result = helper.search(userMsg.content, correlationId)
            
            result.resultsText?.let { searchResultsText = it }
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
                    webSearchUsed = searchResultsText != null
                ),
                isLoading = false,
                isStreaming = true
            )
        }

        val messages = buildLlmMessages(searchResultsText)
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

    private fun buildLlmMessages(searchResults: String? = null): List<LlmMessage> {
        val messages = _uiState.value.messages
            .filter { it.role != Role.SYSTEM }
            .filterNot { it.role == Role.ASSISTANT && it.content.isEmpty() && it.isStreaming }
            .map { message ->
                LlmMessage(
                    role = when (message.role) {
                        Role.USER -> LlmRole.USER
                        Role.ASSISTANT -> LlmRole.ASSISTANT
                        else -> LlmRole.USER
                    },
                    content = buildMessageContent(message),
                    imageBase64 = message.imageBase64
                )
            }

        val messagesWithSearch = if (searchResults != null) {
            messages.toMutableList().also { list ->
                val lastUserIndex = list.indexOfLast { it.role == LlmRole.USER }
                if (lastUserIndex >= 0) {
                    val msg = list[lastUserIndex]
                    list[lastUserIndex] = msg.copy(content = "${msg.content}\n\nUse the information below as references:\n$searchResults")
                }
            }
        } else {
            messages
        }

        return listOf(
            LlmMessage(
                role = LlmRole.SYSTEM,
                content = LlmDefaults.defaultSystemMessage
            )
        ) + messagesWithSearch
    }

    private fun buildMessageContent(message: UiChatMessage): String {
        val parts = mutableListOf<String>()
        if (message.fetchedUrlText != null) {
            parts.add("[Fetched from URL]\n${message.fetchedUrlText}")
        }
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
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
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
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
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
