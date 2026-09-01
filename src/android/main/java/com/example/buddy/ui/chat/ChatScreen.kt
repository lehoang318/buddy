package com.example.buddy.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.example.buddy.LocalLlmClient
import com.example.buddy.LocalUrlFetcher
import com.example.buddy.LocalWebSearch
import com.example.buddy.data.Role
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToProviders: () -> Unit,
    onNavigateToParameters: () -> Unit = {},
    onNavigateToEvents: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val llmClient = LocalLlmClient.current
    val webSearch = LocalWebSearch.current
    val urlFetcher = LocalUrlFetcher.current
    val vm: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )

    LaunchedEffect(llmClient, webSearch, urlFetcher) {
        vm.updateClient(llmClient, webSearch, urlFetcher)
    }
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    val userMessageIndices by remember(state.messages) {
        derivedStateOf {
            state.messages.mapIndexedNotNull { index, msg ->
                if (msg.role == Role.USER) index + 1 else null
            }
        }
    }

    val isScrollable by remember {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }

    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType?.startsWith("image/") == true) {
                vm.onImageUri(uri)
            } else {
                vm.onFilePicked(uri)
            }
        }
    }

    val cameraTempFile = remember {
        File.createTempFile("buddy_camera_", ".jpg", context.cacheDir)
    }
    val cameraTempUri = remember {
        FileProvider.getUriForFile(
            context.applicationContext,
            "${context.packageName}.fileprovider",
            cameraTempFile
        )
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraTempUri != null) {
            vm.onImageUri(cameraTempUri)
        }
    }

    val onTakePhoto = remember(cameraTempUri) {
        {
            takePictureLauncher.launch(cameraTempUri)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraTempFile.delete()
            } catch (e: Exception) {
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BuddyChatTopBar(
                selectedModel = state.selectedModel,
                availableModels = state.availableModels,
                isOffline = state.isOffline,
                webSearchEnabled = state.webSearchEnabled,
                webSearchAvailable = webSearch != null,
                onModelSelect = vm::selectModel,
                onToggleWeb = vm::toggleWebSearch,
                onProviders = onNavigateToProviders,
                onParameters = onNavigateToParameters,
                onEvents = onNavigateToEvents,
                onAbout = onNavigateToAbout,
                onHistory = onNavigateToHistory,
                onClearChat = vm::startNewChat
            )
        },
        bottomBar = {
            InputBar(
                text = state.inputText,
                pendingImage = state.pendingImageBase64,
                pendingFile = state.pendingFileUri,
                pendingFileName = state.pendingFileName,
                attachmentError = state.attachmentError,
                isOffline = state.isOffline,
                isProcessing = state.isLoading || state.isStreaming || state.urlFetchInProgress,
                isCancelling = state.isCancelling,
                reasoningEffort = state.generationConfig.reasoningEffort,
                onToggleReasoning = vm::toggleReasoningEffort,
                onTextChange = vm::onInputChange,
                onClearImage = vm::onClearImage,
                onClearFile = vm::onClearFile,
                onPickAttachment = {
                    attachmentPickerLauncher.launch(
                        arrayOf("image/*", "text/*", "application/json", "application/xml", "text/plain", "text/markdown", "text/html")
                    )
                },
                onTakePhoto = onTakePhoto,
                onSend = {
                    keyboard?.hide()
                    vm.sendMessage()
                },
                onCancel = { vm.cancelRequest() },
                showPairNavigation = isScrollable && userMessageIndices.isNotEmpty(),
                canGoBack = userMessageIndices.firstOrNull()?.let { it < listState.firstVisibleItemIndex } ?: false,
                canGoForward = if (userMessageIndices.isNotEmpty()) {
                    (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) < userMessageIndices.last()
                } else false,
                onGoBack = {
                    val topIdx = listState.firstVisibleItemIndex
                    val pos = userMessageIndices.indexOfFirst { it >= topIdx }.let { if (it >= 0) it else userMessageIndices.lastIndex }
                    if (pos > 0) {
                        scope.launch { listState.animateScrollToItem(userMessageIndices[pos - 1]) }
                    } else if (userMessageIndices.isNotEmpty() && userMessageIndices[0] < topIdx) {
                        scope.launch { listState.animateScrollToItem(userMessageIndices[0]) }
                    }
                },
                onGoForward = {
                    val bottomIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val pos = userMessageIndices.indexOfLast { it <= bottomIdx }
                    if (pos >= 0 && pos < userMessageIndices.lastIndex) {
                        scope.launch { listState.animateScrollToItem(userMessageIndices[pos + 1]) }
                    } else if (pos < 0 && userMessageIndices.isNotEmpty()) {
                        scope.launch { listState.animateScrollToItem(userMessageIndices.first()) }
                    }
                },
                onGoFirst = {
                    userMessageIndices.firstOrNull()?.let { first ->
                        scope.launch { listState.animateScrollToItem(first) }
                    }
                },
                onGoLast = {
                    userMessageIndices.lastOrNull()?.let { last ->
                        scope.launch { listState.animateScrollToItem(last) }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    DayLabel("Today")
                }
                items(state.messages, key = { it.id }) { msg ->
                    MessageRow(message = msg)
                }
                if (state.isLoading) {
                    item { TypingIndicator() }
                }
                state.urlFetchWarnings.forEach { warning ->
                    item {
                        UrlFetchWarningPill(warning = warning)
                    }
                }
                state.webSearchError?.let { error ->
                    item {
                        WebSearchErrorPill(error = error)
                    }
                }
                if (state.webSearchCancelled) {
                    item {
                        WebSearchCancelledPill()
                    }
                }
            }
        }
    }
}
