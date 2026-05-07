package com.example.buddy.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.buddy.LocalLlmClient
import com.example.buddy.LocalUrlFetcher
import com.example.buddy.LocalWebSearch
import com.example.buddy.R
import com.example.buddy.ext.LlmModel
import com.example.buddy.ui.settings.ModelSelectionDialog
import com.example.buddy.ui.theme.Dimens
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
                urlFetchInProgress = state.urlFetchInProgress,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddyChatTopBar(
    selectedModel: String,
    availableModels: List<LlmModel>,
    isOffline: Boolean,
    webSearchEnabled: Boolean,
    webSearchAvailable: Boolean,
    onModelSelect: (String) -> Unit,
    onToggleWeb: () -> Unit,
    onSettings: () -> Unit,
    onParameters: () -> Unit = {},
    onEvents: () -> Unit = {},
    onAbout: () -> Unit = {},
    onClearChat: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showModelSelection by remember { mutableStateOf(false) }
    var titleWidthPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val titleWidthDp = with(density) { titleWidthPx.toDp() }

    val titleIconWidth = 36.dp
    val titleTextGap = 10.dp
    val clearanceAfterTitle = 12.dp
    val multimodalIconWidth = 14.dp
    val webSearchIconWidth = 48.dp
    val clearanceBetweenIcons = 8.dp
    val rightPadding = 16.dp

    val fixedSpace = titleIconWidth + titleTextGap + clearanceAfterTitle +
                     multimodalIconWidth + clearanceBetweenIcons + webSearchIconWidth + rightPadding
    val availableWidth = screenWidth - titleWidthDp - fixedSpace
    val minDropdownWidth = (availableWidth * 0.5f).coerceAtLeast(80.dp)
    val maxDropdownWidth = (availableWidth * 0.8f).coerceAtLeast(80.dp)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        title = {
            Box {
                Row(
                    modifier = Modifier.onGloballyPositioned { titleWidthPx = it.size.width },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.BuddyLogoSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { menuExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.avatar),
                            contentDescription = "Buddy Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text("Buddy", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurface) },
                        text = { Text("Settings", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            menuExpanded = false
                            onSettings()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.onSurface) },
                        text = { Text("Parameters", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            menuExpanded = false
                            onParameters()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        text = { Text("Clear Chat", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onClearChat()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.onSurface) },
                        text = { Text("Events", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            menuExpanded = false
                            onEvents()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface) },
                        text = { Text("About", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            menuExpanded = false
                            onAbout()
                        }
                    )
                }
            }
        },
        actions = {
            if (!isOffline && availableModels.isNotEmpty()) {
                Surface(
                    onClick = { showModelSelection = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .widthIn(min = minDropdownWidth, max = maxDropdownWidth)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = availableModels.find { it.id == selectedModel }?.name
                                ?: selectedModel.ifBlank { "Select" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                if (selectedModel.isNotBlank()) {
                    val selectedModelInfo = availableModels.find { it.id == selectedModel }
                    if (selectedModelInfo != null) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = if (selectedModelInfo.isMultimodal) "Multimodal supported" else "Multimodal not supported",
                            tint = if (selectedModelInfo.isMultimodal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else if (isOffline || availableModels.isEmpty()) {
                Surface(
                    onClick = { onSettings() },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "No model",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = if (webSearchAvailable) onToggleWeb else onSettings
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = "Web Search",
                    tint = when {
                        webSearchEnabled && webSearchAvailable && !isOffline -> MaterialTheme.colorScheme.primary
                        webSearchAvailable && !isOffline -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }
    )

    if (showModelSelection && availableModels.isNotEmpty()) {
        ModelSelectionDialog(
            availableModels = availableModels,
            currentModelId = selectedModel,
            onModelSelected = { modelId ->
                onModelSelect(modelId)
                showModelSelection = false
            },
            onDismiss = { showModelSelection = false }
        )
    }
}
