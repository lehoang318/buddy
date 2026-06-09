package com.example.buddy.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.buddy.LocalLlmClient
import com.example.buddy.LocalUrlFetcher
import com.example.buddy.LocalWebSearch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToParameters: () -> Unit = {},
    onNavigateToEvents: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
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
    var showClearChatConfirm by remember { mutableStateOf(false) }
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val context = LocalContext.current
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType?.startsWith("image/") == true) {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                val base64 = bitmap?.let { vm.bitmapToBase64(it) }
                bitmap?.recycle()
                vm.onImagePicked(base64)
            } else {
                vm.onFilePicked(uri)
            }
        }
    }

    val cameraTempUri = remember {
        val cacheDir = context.cacheDir
        val imageFile = File.createTempFile("buddy_camera_", ".jpg", cacheDir)
        FileProvider.getUriForFile(
            context.applicationContext,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraTempUri != null) {
            val inputStream = context.contentResolver.openInputStream(cameraTempUri)
            val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
            val base64 = bitmap?.let { vm.bitmapToBase64(it) }
            bitmap?.recycle()
            vm.onImagePicked(base64)
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
                File(cameraTempUri.path!!).delete()
            } catch (e: Exception) {
            }
        }
    }

    Scaffold(
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
                onSettings = onNavigateToSettings,
                onParameters = onNavigateToParameters,
                onEvents = onNavigateToEvents,
                onAbout = onNavigateToAbout,
                onClearChat = { showClearChatConfirm = true }
            )
        },
        bottomBar = {
            InputBar(
                text = state.inputText,
                pendingImage = state.pendingImageBase64,
                pendingFile = state.pendingFileUri,
                pendingFileName = state.pendingFileName,
                fileTooLargeError = state.fileTooLargeError,
                isOffline = state.isOffline,
                isProcessing = state.isLoading || state.isStreaming || state.urlFetchInProgress,
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
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
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
        }
    }

    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text("Clear Chat") },
            text = { Text("Clear conversation history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearChat()
                        showClearChatConfirm = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
