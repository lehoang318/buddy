package com.example.buddy.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.buddy.data.EventLog
import com.example.buddy.data.SessionImageStore
import com.example.buddy.agent.ASK_USER_SKIP_ANSWER
import com.example.buddy.chat.ConversationEngine
import com.example.buddy.chat.ConversationEvent
import com.example.buddy.chat.ChatSessionManager
import com.example.buddy.chat.TextAttachment
import com.example.buddy.chat.TextAttachmentRules
import com.example.buddy.chat.ConversationMessage
import com.example.buddy.data.Role
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SavedSession
import com.example.buddy.data.SessionMessage
import com.example.buddy.data.SessionRepository
import com.example.buddy.data.SettingsRepository
import com.example.buddy.data.Summary
import com.example.buddy.fetch.UrlFetcher
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmModel
import com.example.buddy.llm.ReasoningEffort
import com.example.buddy.search.WebSearch
import com.example.buddy.service.BuddyForegroundService
import com.example.buddy.service.ServiceHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.example.buddy.data.ChatMessage as UiChatMessage

private const val MAX_IMAGE_DIMENSION = 1440
private const val JPEG_QUALITY = 85
private const val TAG = "Chat"

data class PendingQuestion(
    val question: String,
    val options: List<String>
)

data class ChatUiState(
    val messages: List<UiChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val pendingImageBase64: String? = null,
    val pendingFileUri: Uri? = null,
    val pendingFileName: String? = null,
    val agenticMode: Boolean = false,
    val availableModels: List<LlmModel> = emptyList(),
    val selectedModel: String = "",
    val isOffline: Boolean = false,
    val generationConfig: LlmGenerationConfig = LlmGenerationConfig(),
    val webSearchError: String? = null,
    val webSearchCancelled: Boolean = false,
    val attachmentError: String? = null,
    val urlFetchInProgress: Boolean = false,
    val urlFetchWarnings: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val isCancelling: Boolean = false,
    val pendingQuestion: PendingQuestion? = null,
    val summaries: List<Summary> = emptyList()
)

class ChatViewModel(
    private val application: Application,
) : ViewModel() {

    private var llmClient: LlmClient? = null

    private val sessionManager = ChatSessionManager(SessionRepository(application))
    private val sessionImageStore = SessionImageStore(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val conversationEngine = ConversationEngine()
    private var currentJob: Job? = null

    fun updateClient(client: LlmClient?, web: WebSearch?, fetcher: UrlFetcher?) {
        val clientChanged = llmClient !== client
        llmClient = client
        conversationEngine.updateDependencies(client, web, fetcher)
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
        val state = _uiState.value
        val current = state.generationConfig.reasoningEffort
        val next = llmClient?.toggleReasoning(current, state.webSearchEnabled, state.agenticMode)
            ?: ReasoningEffort.HIGH
        _uiState.update {
            it.copy(generationConfig = it.generationConfig.copy(reasoningEffort = next))
        }
    }

    fun updateSettings(settings: LlmSettings) {
        conversationEngine.agenticMode = settings.agenticMode
        _uiState.update {
            it.copy(
                agenticMode = settings.agenticMode,
                generationConfig = LlmGenerationConfig(
                    temperature = settings.temperature.takeIf { v -> v > 0f },
                    topP = settings.topP.takeIf { v -> v > 0f },
                    topK = settings.topK.takeIf { v -> v > 0 },
                    maxTokens = settings.maxTokens.takeIf { v -> v > 0 },
                    reasoningEffort = it.generationConfig.reasoningEffort
                )
            )
        }
    }

    fun toggleAgenticMode() {
        val next = !_uiState.value.agenticMode
        conversationEngine.agenticMode = next
        _uiState.update {
            val effort = if (!next && it.generationConfig.reasoningEffort == ReasoningEffort.DEEP) {
                ReasoningEffort.HIGH
            } else {
                it.generationConfig.reasoningEffort
            }
            it.copy(
                agenticMode = next,
                generationConfig = it.generationConfig.copy(reasoningEffort = effort)
            )
        }
        viewModelScope.launch { settingsRepository.updateAll(agenticMode = next) }
    }

    fun clearChat() {
        conversationEngine.clear()
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
                webSearchCancelled = false,
                attachmentError = null,
                urlFetchWarnings = emptyList(),
                isLoading = false,
                isStreaming = false,
                urlFetchInProgress = false,
                summaries = emptyList()
            )
        }
    }

    private fun toSessionMessages(state: ChatUiState): List<SessionMessage> =
        state.messages.map { m ->
            SessionMessage(
                role = m.role,
                content = m.content,
                imageBase64 = m.imageBase64,
                attachedFileName = m.attachedFileName,
                attachedFileText = m.attachedFileText,
                webSearchUsed = m.webSearchUsed,
                webSearchSkipped = m.webSearchSkipped,
                webSearchQueries = m.webSearchQueries,
                questionAsked = m.questionAsked,
                questionAnswer = m.questionAnswer,
                timestamp = m.timestamp
            )
        }

    private suspend fun saveCurrentSession() {
        val state = _uiState.value
        sessionManager.save(toSessionMessages(state), state.summaries) { session ->
            session.copy(raw = sessionImageStore.detach(session.id, session.raw))
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            currentJob?.cancelAndJoin()
            saveCurrentSession()
            sessionManager.reset()
            clearChat()
        }
    }

    fun resumeSession(session: SavedSession) {
        viewModelScope.launch {
            currentJob?.cancelAndJoin()
            saveCurrentSession()
            withRestoredSession(sessionImageStore.hydrate(session))
        }
    }

    private fun withRestoredSession(session: SavedSession) {
        val restored = migrateLegacyQuestionAnswers(session.raw)
        val raw = restored.map { m ->
            UiChatMessage(
                role = m.role,
                content = m.content,
                imageBase64 = m.imageBase64,
                attachedFileName = m.attachedFileName,
                attachedFileText = m.attachedFileText,
                webSearchUsed = m.webSearchUsed,
                webSearchSkipped = m.webSearchSkipped,
                webSearchQueries = m.webSearchQueries,
                questionAsked = m.questionAsked,
                questionAnswer = m.questionAnswer,
                timestamp = m.timestamp,
                isStreaming = false,
                isComplete = true
            )
        }
        val engineHistory = buildList {
            restored.forEach { m ->
                if (m.role == Role.ASSISTANT && !m.questionAsked.isNullOrBlank() && !m.questionAnswer.isNullOrBlank()) {
                    add(ConversationMessage(Role.USER, m.questionAnswer))
                }
                add(
                    ConversationMessage(
                        role = m.role,
                        content = m.content,
                        imageBase64 = m.imageBase64,
                        attachment = m.attachedFileText?.let { TextAttachment(m.attachedFileName ?: "attachment", it) },
                        questionAsked = m.questionAsked
                    )
                )
            }
        }
        conversationEngine.restore(engineHistory, session.summaries)
        _uiState.update { state ->
            state.copy(
                messages = raw,
                inputText = "",
                pendingImageBase64 = null,
                pendingFileUri = null,
                pendingFileName = null,
                webSearchError = null,
                webSearchCancelled = false,
                attachmentError = null,
                urlFetchWarnings = emptyList(),
                isLoading = false,
                isStreaming = false,
                urlFetchInProgress = false,
                pendingQuestion = null,
                summaries = session.summaries
            )
        }
        sessionManager.bind(session)
    }

    private fun migrateLegacyQuestionAnswers(messages: List<SessionMessage>): List<SessionMessage> {
        val result = mutableListOf<SessionMessage>()
        var index = 0
        while (index < messages.size) {
            val message = messages[index]
            val next = messages.getOrNull(index + 1)
            if (message.role == Role.ASSISTANT &&
                !message.questionAsked.isNullOrBlank() &&
                message.questionAnswer == null &&
                next?.role == Role.USER
            ) {
                val answer = if (next.content == "(skipped)") ASK_USER_SKIP_ANSWER else next.content
                result += message.copy(questionAnswer = answer)
                index += 2
            } else {
                result += message
                index++
            }
        }
        return result
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

    fun onImageUri(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(attachmentError = null) }
        viewModelScope.launch {
            val base64 = decodeImageToBase64(uri)
            _uiState.update { state ->
                if (base64 == null) {
                    state.copy(pendingImageBase64 = null, attachmentError = "Could not process image")
                } else {
                    state.copy(
                        pendingImageBase64 = base64,
                        pendingFileUri = null,
                        pendingFileName = null,
                        attachmentError = null
                    )
                }
            }
        }
    }

    private suspend fun decodeImageToBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            application.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var inSampleSize = 1
            while (maxDim / (inSampleSize * 2) >= MAX_IMAGE_DIMENSION) {
                inSampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val bitmap = application.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null
            val base64 = bitmapToBase64(bitmap)
            bitmap.recycle()
            base64
        } catch (e: Exception) {
            null
        }
    }

    fun onClearImage() {
        _uiState.update { it.copy(pendingImageBase64 = null, attachmentError = null) }
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(pendingFileUri = null, pendingFileName = null, attachmentError = null) }
            return
        }
        val fileName = getFileName(uri)
        val fileSize = getFileSize(uri)
        TextAttachmentRules.validate(fileName, fileSize)?.let { error ->
            _uiState.update { it.copy(pendingFileUri = null, pendingFileName = null, attachmentError = error) }
            return
        }
        _uiState.update { it.copy(pendingFileUri = uri, pendingFileName = fileName, pendingImageBase64 = null, attachmentError = null) }
    }

    fun onClearFile() {
        _uiState.update { it.copy(pendingFileUri = null, pendingFileName = null, attachmentError = null) }
    }

    fun toggleWebSearch() {
        _uiState.update {
            val enabled = !it.webSearchEnabled
            val effort = if (!enabled && it.generationConfig.reasoningEffort == ReasoningEffort.DEEP) {
                ReasoningEffort.HIGH
            } else {
                it.generationConfig.reasoningEffort
            }
            it.copy(
                webSearchEnabled = enabled,
                generationConfig = it.generationConfig.copy(reasoningEffort = effort)
            )
        }
    }

    fun cancelRequest() {
        val job = currentJob ?: return
        _uiState.update { it.copy(isCancelling = true) }
        job.cancel()
    }

    fun skipPendingQuestion() {
        if (_uiState.value.pendingQuestion == null) return
        submitAnswer("")
    }

    fun answerQuestion(answer: String) {
        if (_uiState.value.pendingQuestion == null) return
        submitAnswer(answer)
    }

    private fun submitAnswer(rawAnswer: String) {
        val answer = rawAnswer.trim()
        val accepted = conversationEngine.answerPendingQuestion(answer)
        if (!accepted) {
            _uiState.update { it.copy(pendingQuestion = null) }
            return
        }
        EventLog.info(TAG, "Clarification answered", "${answer.length} chars")
        _uiState.update { current ->
            val questionIndex = current.messages.indexOfLast { message ->
                message.role == Role.ASSISTANT &&
                    !message.questionAsked.isNullOrBlank() &&
                    message.questionAnswer == null
            }
            val answerText = answer.ifEmpty { ASK_USER_SKIP_ANSWER }
            current.copy(
                inputText = "",
                pendingQuestion = null,
                messages = if (questionIndex < 0) {
                    current.messages
                } else {
                    current.messages.mapIndexed { index, message ->
                        if (index == questionIndex) message.copy(questionAnswer = answerText) else message
                    }
                }
            )
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (state.pendingQuestion != null) {
            submitAnswer(state.inputText)
            return
        }
        val text = state.inputText.trim()
        if (text.isBlank()) return

        sessionManager.markDirty()

        val correlationId = java.util.UUID.randomUUID().toString()
        EventLog.info(TAG, "User input: ${text.length} chars", correlationId = correlationId)
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
            webSearchCancelled = false,
            attachmentError = null
        )}

        var fileText: String? = null
        if (savedFileUri != null) {
            val result = readTextFile(savedFileUri, savedFileName ?: "unknown")
            if (result.isFailure) {
                _uiState.update { it.copy(attachmentError = result.exceptionOrNull()?.message ?: "Could not read file") }
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

        currentJob = viewModelScope.launch {
            var assistantId: String? = null
            var plannedSearchQueries: List<String> = emptyList()
            try {
                conversationEngine.webSearchEnabled = state.webSearchEnabled
                val attachment = fileText?.let { TextAttachment(savedFileName ?: "unknown", it) }
                conversationEngine.send(
                    userText = text,
                    attachment = attachment,
                    imageBase64 = savedImageBase64,
                    generationConfig = state.generationConfig,
                    correlationId = correlationId
                ).collect { event ->
                    when (event) {
                        ConversationEvent.UrlFetchStarted -> {
                            _uiState.update { it.copy(urlFetchInProgress = true, urlFetchWarnings = emptyList()) }
                            ServiceHelper.onOperationStart(application)
                            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.URL_FETCHING, "Fetching URL content...")
                        }
                        is ConversationEvent.UrlFetchFinished -> {
                            _uiState.update { it.copy(urlFetchInProgress = false, urlFetchWarnings = event.result.warnings) }
                            ServiceHelper.onOperationEnd(application)
                        }
                        is ConversationEvent.UserMessageAccepted -> {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + UiChatMessage(
                                        role = Role.USER,
                                        content = event.message.content,
                                        imageBase64 = event.message.imageBase64,
                                        attachedFileUri = savedFileUri,
                                        attachedFileName = event.message.attachment?.name,
                                        attachedFileText = event.message.attachment?.text
                                    ),
                                    isLoading = true
                                )
                            }
                        }
                        ConversationEvent.SearchStarted -> {
                            ServiceHelper.onOperationStart(application)
                            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.WEB_SEARCHING, "Searching the web...")
                        }
                        is ConversationEvent.SearchQueriesPlanned -> {
                            plannedSearchQueries = event.queries
                            assistantId?.let { id ->
                                _uiState.update { current ->
                                    current.copy(messages = current.messages.map { message ->
                                        if (message.id == id) message.copy(webSearchUsed = true, webSearchQueries = event.queries) else message
                                    })
                                }
                            }
                        }
                        is ConversationEvent.SearchFinished -> {
                            event.outcome.errorMessage?.let { error ->
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
                        ConversationEvent.ClarificationAnswered -> {
                            _uiState.update { it.copy(pendingQuestion = null) }
                            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.LLM_STREAMING, "Generating response...")
                        }
                        is ConversationEvent.QuestionAsked -> {
                            _uiState.update { current ->
                                current.copy(
                                    messages = current.messages.map { message ->
                                        if (message.id == assistantId) message.copy(questionAsked = event.question) else message
                                    },
                                    pendingQuestion = PendingQuestion(event.question, event.options)
                                )
                            }
                            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.LLM_STREAMING, "Waiting for your answer...")
                        }
                        is ConversationEvent.AssistantStarted -> {
                            assistantId = event.id
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + UiChatMessage(
                                        id = event.id,
                                        role = Role.ASSISTANT,
                                        content = "",
                                        isStreaming = true,
                                        webSearchUsed = event.searchOutcome?.rawResults?.isNotEmpty() == true || plannedSearchQueries.isNotEmpty(),
                                        webSearchSkipped = event.searchOutcome?.skipped == true,
                                        webSearchQueries = plannedSearchQueries.ifEmpty { event.searchOutcome?.queries.orEmpty() }
                                    ),
                                    isLoading = false,
                                    isStreaming = true
                                )
                            }
                            ServiceHelper.onOperationStart(application)
                            BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.LLM_STREAMING, "Generating response...")
                        }
                        is ConversationEvent.Token -> _uiState.update { current ->
                            current.copy(messages = current.messages.map { message ->
                                if (message.id == assistantId) message.copy(content = message.content + event.text, thoughtsStreaming = false) else message
                            })
                        }
                        is ConversationEvent.ThoughtsDelta -> _uiState.update { current ->
                            current.copy(messages = current.messages.map { message ->
                                if (message.id == assistantId) message.copy(agentThoughts = message.agentThoughts + event.text, thoughtsStreaming = true) else message
                            })
                        }
                        is ConversationEvent.AnswerReset -> _uiState.update { current ->
                            current.copy(messages = current.messages.map { message ->
                                if (message.id == assistantId) {
                                    val thoughts = if (message.agentThoughts.isBlank()) event.retractedText
                                    else message.agentThoughts + "\n\n" + event.retractedText
                                    message.copy(content = "", agentThoughts = thoughts)
                                } else message
                            })
                        }
                        is ConversationEvent.Completed -> {
                            _uiState.update { current ->
                                current.copy(
                                    messages = current.messages.map { message ->
                                        if (message.id == assistantId) message.copy(isStreaming = false, isComplete = true) else message
                                    },
                                    isStreaming = false,
                                    pendingQuestion = null,
                                    summaries = event.summaries
                                )
                            }
                            ServiceHelper.onOperationEnd(application)
                        }
                        is ConversationEvent.Failed -> {
                            _uiState.update { current ->
                                current.copy(
                                    messages = if (assistantId == null) current.messages else current.messages.map { message ->
                                        if (message.id == assistantId) message.copy(content = "Error: ${event.error.message}", isStreaming = false, isComplete = true) else message
                                    },
                                    isLoading = false,
                                    isStreaming = false,
                                    pendingQuestion = null
                                )
                            }
                            ServiceHelper.onOperationEnd(application)
                        }
                    }
                }
                saveCurrentSession()
            } catch (e: CancellationException) {
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map { message ->
                            if (message.id == assistantId) message.copy(isStreaming = false, isComplete = true) else message
                        },
                        isStreaming = false,
                        isCancelling = false,
                        pendingQuestion = null
                    )
                }
                _uiState.update { it.copy(webSearchCancelled = true) }
                ServiceHelper.onOperationEnd(application)
                throw e
            } finally {
                currentJob = null
                _uiState.update {
                    it.copy(
                        isCancelling = false,
                        isLoading = false,
                        isStreaming = false,
                        urlFetchInProgress = false,
                        pendingQuestion = null
                    )
                }
                BuddyForegroundService.updateStatus(BuddyForegroundService.OperationStatus.IDLE, "")
            }
        }
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

    private fun readTextFile(uri: Uri, fileName: String): Result<String> {
        return try {
            val inputStream = application.contentResolver.openInputStream(uri) ?: return Result.failure(Exception("Could not open file"))
            val bytes = inputStream.readBytes()
            inputStream.close()
            TextAttachmentRules.fromBytes(fileName, bytes).map { it.text }
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

}

class ChatViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(application) as T
    }
}
