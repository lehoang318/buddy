package com.example.buddy.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.buddy.data.ChatMessage as UiChatMessage
import com.example.buddy.data.EventLog
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

val SUPPORTED_TEXT_EXTENSIONS = listOf(
    ".txt", ".md", ".log", ".rst", ".adoc", ".asciidoc", ".rtf", ".json", ".xml", ".html"
)

data class ChatUiState(
    val messages: List<UiChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val pendingImageUri: Uri? = null,
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
    private val llmClient: LlmClient?,
    private val webSearch: WebSearch?,
    private val urlFetcher: UrlFetcher?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(isOffline = llmClient == null))
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        if (llmClient != null) {
            _uiState.update {
                it.copy(
                    messages = listOf(
                        UiChatMessage(
                            role = Role.ASSISTANT,
                            content = "Hey! I'm Buddy. I can answer questions, write code, analyze images, and search the web. What can I help you with?",
                            isComplete = true
                        )
                    ),
                    selectedModel = llmClient.currentModel
                )
            }
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

    fun setOfflineMode(offline: Boolean) {
        _uiState.update { it.copy(isOffline = offline) }
    }

    private fun loadAvailableModels() {
        if (llmClient == null) return
        viewModelScope.launch {
            val models = llmClient.getModels()
            _uiState.update {
                it.copy(
                    availableModels = models,
                    selectedModel = it.selectedModel.takeIf { s -> models.any { m -> m.id == s } } ?: models.firstOrNull()?.id ?: ""
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onImagePicked(uri: Uri?) {
        _uiState.update {
            it.copy(
                pendingImageUri = uri,
                pendingFileUri = null,
                pendingFileName = null,
                fileTooLargeError = null
            )
        }
    }

    fun onClearImage() {
        _uiState.update { it.copy(pendingImageUri = null) }
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
        _uiState.update { it.copy(pendingFileUri = uri, pendingFileName = fileName, pendingImageUri = null, fileTooLargeError = null) }
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
        if (text.isBlank() && state.pendingImageUri == null && state.pendingFileUri == null) return

        EventLog.add("I", "user input: ${text.length} characters")

        val urls = extractUrls(text)

        _uiState.update { it.copy(
            inputText = "",
            pendingImageUri = null,
            pendingFileUri = null,
            pendingFileName = null,
            webSearchError = null,
            fileTooLargeError = null
        )}

        viewModelScope.launch {
            var fetchedUrlText: String? = null
            val warnings = mutableListOf<String>()
            if (urls.isNotEmpty() && urlFetcher != null) {
                _uiState.update { it.copy(urlFetchInProgress = true, urlFetchWarnings = emptyList()) }
                
                // Notify service that URL fetch is starting
                ServiceHelper.onOperationStart(application)
                BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.URL_FETCHING, "Fetching URL content...")
                
                val results = urls.mapNotNull { url ->
                    try {
                        EventLog.add("I", "web fetch: sent")
                        val content = urlFetcher.fetchTextContent(url)
                        if (content != null) {
                            EventLog.add("I", "web fetch: success")
                            "Source: $url\n$content" to null
                        } else {
                            EventLog.add("E", "web fetch: failed")
                            warnings.add("Failed to fetch: $url")
                            null
                        }
                    } catch (e: Exception) {
                        EventLog.add("E", "web fetch: failed")
                        warnings.add("Failed to fetch: $url")
                        null
                    }
                }
                fetchedUrlText = if (results.isNotEmpty()) results.map { it.first }.joinToString("\n\n---\n\n") else null
                _uiState.update { it.copy(urlFetchInProgress = false, urlFetchWarnings = warnings) }
                
                // Notify service that URL fetch is complete
                ServiceHelper.onOperationEnd(application)
            }

            var fileText: String? = null
            val fileUri = state.pendingFileUri
            val fileName = state.pendingFileName
            if (fileUri != null) {
                val result = readTextFile(fileUri)
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
                imageUri = state.pendingImageUri,
                attachedFileUri = fileUri,
                attachedFileName = fileName,
                attachedFileText = fileText
            )

            _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }

            streamRealResponse(userMsg)
        }
    }

    private suspend fun streamRealResponse(userMsg: UiChatMessage) {
        if (llmClient == null) return
        val assistantId = java.util.UUID.randomUUID().toString()
        val state = _uiState.value
        val shouldSearch = state.webSearchEnabled && webSearch != null && userMsg.content.isNotBlank()

        var searchResultsText: String? = null

        if (shouldSearch) {
            // Notify service that web search is starting
            ServiceHelper.onOperationStart(application)
            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.WEB_SEARCHING, "Searching the web...")
            
            try {
                val searchQuery = llmClient.generateSearchQuery(userMsg.content)
                EventLog.add("I", "web query generation: ${searchQuery.length} characters")
                EventLog.add("I", "web query: ${searchQuery.length} characters")
                EventLog.add("I", "web fetch: sent")
                val results = webSearch.search(searchQuery)
                EventLog.add("I", "web fetch: success")
                EventLog.add("I", "web search: ${results.joinToString("\n\n").length} characters")
                if (results.isEmpty()) {
                    _uiState.update { it.copy(webSearchError = "Web search returned no results") }
                } else {
                    searchResultsText = results.joinToString("\n\n") { result ->
                        "Source: ${result.title}\nURL: ${result.url}\n${result.content}"
                    }
                }
            } catch (e: Exception) {
                EventLog.add("E", "web fetch: failed")
                val errorMsg = when {
                    e.message?.contains("401") == true || e.message?.contains("403") == true -> "Invalid Tavily API key"
                    e.message?.contains("429") == true -> "Tavily usage limit exceeded"
                    else -> "Web search failed: ${e.message}"
                }
                _uiState.update { it.copy(webSearchError = errorMsg) }
            } finally {
                // Notify service that web search is complete
                ServiceHelper.onOperationEnd(application)
            }
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
        val messagesText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        EventLog.add("I", "llm request: ${messagesText.length} characters")
        val model = _uiState.value.selectedModel.ifBlank { llmClient.currentModel }
        val config = _uiState.value.generationConfig

        try {
            // Notify service that LLM streaming is starting
            ServiceHelper.onOperationStart(application)
            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.LLM_STREAMING, "Generating response...")
            
            llmClient.streamCompletion(messages, model, config).collect { token ->
                _uiState.update { s ->
                    s.copy(
                        messages = s.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(content = msg.content + token) else msg
                        }
                    )
                }
            }
            EventLog.add("I", "llm response: success")
            // Mark streaming done and isComplete=true so markdown rendering kicks in
            _uiState.update { s ->
                s.copy(
                    messages = s.messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(isStreaming = false, isComplete = true) else msg
                    },
                    isStreaming = false
                )
            }
        } catch (e: Exception) {
            EventLog.add("E", "llm response: failed: ${e.message}")
            _uiState.update { s ->
                s.copy(
                    messages = s.messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(content = "Error: ${e.message}", isStreaming = false, isComplete = true) else msg
                    },
                    isStreaming = false
                )
            }
        } finally {
            // Notify service that LLM streaming is complete
            ServiceHelper.onOperationEnd(application)
            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.IDLE, "")
        }
    }

    private fun buildLlmMessages(searchResults: String? = null): List<LlmMessage> {
        val baseMessages = _uiState.value.messages
            .filter { it.role != Role.SYSTEM }
            .filterNot { it.role == Role.ASSISTANT && it.content.isEmpty() && it.isStreaming }
            .map { message ->
                val imageBase64 = message.imageUri?.let { resizeImageToJpeg(it) }
                val content = buildMessageContent(message)
                LlmMessage(
                    role = when (message.role) {
                        Role.USER -> LlmRole.USER
                        Role.ASSISTANT -> LlmRole.ASSISTANT
                        else -> LlmRole.USER
                    },
                    content = content,
                    imageBase64 = imageBase64
                )
            }

        return if (searchResults != null) {
            listOf(
                LlmMessage(
                    role = LlmRole.SYSTEM,
                    content = "Use the following web search results to provide accurate, up-to-date information. Cite sources when relevant.\n\n$searchResults"
                )
            ) + baseMessages
        } else {
            baseMessages
        }
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

    private fun resizeImageToJpeg(uri: Uri): String? {
        return try {
            val inputStream = application.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream.use { BitmapFactory.decodeStream(it, null, options) }
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null
            val maxDim = maxOf(originalWidth, originalHeight)
            val inSampleSize = if (maxDim > MAX_IMAGE_DIMENSION) calculateInSampleSize(options, MAX_IMAGE_DIMENSION) else 1
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val inputStream2 = application.contentResolver.openInputStream(uri) ?: return null
            var bitmap = inputStream2.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null
            val scaledWidth = bitmap.width
            val scaledHeight = bitmap.height
            val scaledMaxDim = maxOf(scaledWidth, scaledHeight)
            if (scaledMaxDim > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / scaledMaxDim
                val newWidth = (scaledWidth * scale).toInt()
                val newHeight = (scaledHeight * scale).toInt()
                val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                bitmap.recycle()
                bitmap = resized
            }
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            bitmap.recycle()
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqSize: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqSize || width > reqSize) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqSize && (halfWidth / inSampleSize) >= reqSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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
    private val llmClient: LlmClient?,
    private val webSearch: WebSearch?,
    private val urlFetcher: UrlFetcher?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(application, llmClient, webSearch, urlFetcher) as T
    }
}
