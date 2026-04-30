package com.example.buddy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.buddy.data.BuiltInProviders
import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmProvider
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SettingsRepository
import com.example.buddy.data.WebSearchProvider
import com.example.buddy.ext.LlmModel
import com.example.buddy.ext.LlmClientFactory
import com.example.buddy.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "Settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSettingsSaved: () -> Unit,
    initialSettings: LlmSettings? = null,
    settingsRepository: SettingsRepository? = null,
    onSaveModelSettings: (LlmSettings) -> Unit = {}
) {
    val context = LocalContext.current
    val repo = settingsRepository ?: remember { SettingsRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    val savedSettings by repo.settings.collectAsState(initial = initialSettings ?: LlmSettings())
    val allLlmProviders by repo.allLlmProviders.collectAsState(initial = BuiltInProviders.loadLlmProviders(context))
    val allWebSearchProviders by repo.allWebSearchProviders.collectAsState(initial = BuiltInProviders.loadWebSearchProviders(context))
    val llmApiKeysMap by repo.llmApiKeysByProvider.collectAsState(initial = emptyMap())
    val wsApiKeysMap by repo.webSearchApiKeysByProvider.collectAsState(initial = emptyMap())
    val effectiveInitial = initialSettings ?: savedSettings

    val resolvedInitialLlmKey = remember {
        llmApiKeysMap[effectiveInitial.provider]?.takeIf { it.isNotBlank() } ?: effectiveInitial.apiKey
    }
    val resolvedInitialWsKey = remember {
        wsApiKeysMap[effectiveInitial.webSearchProvider]?.takeIf { it.isNotBlank() } ?: effectiveInitial.webSearchApiKey
    }

    var selectedProvider by remember(effectiveInitial.provider) { mutableStateOf(effectiveInitial.provider) }
    var apiKey by remember { mutableStateOf(resolvedInitialLlmKey) }
    var selectedModel by remember(effectiveInitial.model) { mutableStateOf(effectiveInitial.model) }

    var selectedWebSearchProvider by remember(effectiveInitial.webSearchProvider) { mutableStateOf(effectiveInitial.webSearchProvider) }
    var webSearchApiKey by remember { mutableStateOf(resolvedInitialWsKey) }

    var availableModels by remember { mutableStateOf<List<LlmModel>>(emptyList()) }
    var showModelSelection by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var webSearchProviderDropdownExpanded by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var showLlmApiKeyDialog by remember { mutableStateOf(false) }
    var showWsApiKeyDialog by remember { mutableStateOf(false) }
    var showRemoveProviderConfirm by remember { mutableStateOf<LlmProvider?>(null) }

    val builtInProviderIds = remember { BuiltInProviders.loadLlmProviders(context).map { it.id }.toSet() }

    val currentProvider = allLlmProviders.find { it.id == selectedProvider }
    val currentWsProvider = allWebSearchProviders.find { it.id == selectedWebSearchProvider }

    fun handleConnect(provider: LlmProvider, key: String, onComplete: (success: Boolean) -> Unit) {
        connectError = null
        isConnecting = true
        coroutineScope.launch {
            try {
                val models = LlmClientFactory.getModels(provider, key)
                if (models.isEmpty()) {
                    connectError = "No models available. Please check your API Key."
                } else {
                    availableModels = models
                    selectedModel = models.first().id

                    val testResult = LlmClientFactory.createWithProvider(provider, key, selectedModel)
                    testResult.fold(
                        onSuccess = { testClient ->
                            val connected = testClient.testConnection()
                            if (connected) {
                                apiKey = key
                                selectedProvider = provider.id
                                repo.updateAll(
                                    provider = provider.id,
                                    apiKey = key,
                                    model = selectedModel,
                                    webSearchProvider = selectedWebSearchProvider,
                                    webSearchApiKey = webSearchApiKey
                                )
                                repo.saveLlmApiKey(provider.id, key)
                                if (selectedWebSearchProvider.isNotBlank() && webSearchApiKey.isNotBlank()) {
                                    repo.saveWebSearchApiKey(selectedWebSearchProvider, webSearchApiKey)
                                }
                                EventLog.info(TAG, "Settings connected", "provider=${provider.id}, model=$selectedModel, webSearch=$selectedWebSearchProvider")
                            } else {
                                connectError = "Connection failed. Please check your API Key."
                            }
                        },
                        onFailure = { e ->
                            connectError = "Connection failed: ${e.message}"
                        }
                    )
                }
            } catch (e: Exception) {
                connectError = "Error: ${e.message}"
            } finally {
                isConnecting = false
                onComplete(connectError == null)
            }
        }
    }

    fun handleWsSave(key: String, onComplete: () -> Unit) {
        coroutineScope.launch {
            webSearchApiKey = key
            repo.updateAll(
                provider = selectedProvider,
                apiKey = apiKey,
                model = selectedModel,
                webSearchProvider = selectedWebSearchProvider,
                webSearchApiKey = key
            )
            repo.saveWebSearchApiKey(selectedWebSearchProvider, key)
            EventLog.info(TAG, "Web search settings saved", "provider=$selectedWebSearchProvider")
            onComplete()
        }
    }

    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            isConnecting = isConnecting,
            error = connectError,
            onConnect = { provider ->
                coroutineScope.launch {
                    repo.addCustomLlmProvider(provider)
                    if (provider.apiKey.isNotBlank()) {
                        repo.saveLlmApiKey(provider.id, provider.apiKey)
                    }
                    selectedProvider = provider.id
                    apiKey = provider.apiKey
                    handleConnect(provider, provider.apiKey) { success ->
                        if (success) {
                            showAddProviderDialog = false
                        }
                    }
                }
            }
        )
    }

    if (showLlmApiKeyDialog && currentProvider != null) {
        ApiKeyConnectDialog(
            title = "Connect to ${currentProvider.name}",
            initialApiKey = apiKey,
            isConnecting = isConnecting,
            error = connectError,
            onDismiss = {
                showLlmApiKeyDialog = false
                connectError = null
            },
            onConnect = { key ->
                handleConnect(currentProvider, key) { success ->
                    if (success) {
                        showLlmApiKeyDialog = false
                    }
                }
            }
        )
    }

    if (showWsApiKeyDialog && currentWsProvider != null) {
        ApiKeyConnectDialog(
            title = "Connect to ${currentWsProvider.name}",
            initialApiKey = webSearchApiKey,
            isConnecting = isConnecting,
            error = null,
            onDismiss = { showWsApiKeyDialog = false },
            onConnect = { key ->
                handleWsSave(key) {
                    showWsApiKeyDialog = false
                }
            }
        )
    }

    showRemoveProviderConfirm?.let { providerToRemove ->
        AlertDialog(
            onDismissRequest = { showRemoveProviderConfirm = null },
            title = { Text("Remove Provider", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Remove ${providerToRemove.name}?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repo.removeCustomLlmProvider(providerToRemove.id)
                            if (selectedProvider == providerToRemove.id) {
                                selectedProvider = ""
                                apiKey = ""
                                availableModels = emptyList()
                            }
                        }
                        showRemoveProviderConfirm = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveProviderConfirm = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                navigationIcon = {
                    IconButton(onClick = {
                        onSaveModelSettings(
                            LlmSettings(
                                provider = effectiveInitial.provider,
                                apiKey = effectiveInitial.apiKey,
                                model = selectedModel,
                                webSearchProvider = selectedWebSearchProvider,
                                webSearchApiKey = webSearchApiKey
                            )
                        )
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Text("Settings", color = MaterialTheme.colorScheme.onSurface)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("LLM Provider", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = providerDropdownExpanded,
                    onExpandedChange = { providerDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = allLlmProviders.find { it.id == selectedProvider }?.name
                            ?: if (selectedProvider.isNotBlank()) selectedProvider else "",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Provider") },
                        placeholder = { Text("Select a provider") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            focusedContainerColor = SurfaceVariant,
                            unfocusedContainerColor = SurfaceVariant,
                            focusedBorderColor = SendButton,
                            unfocusedBorderColor = Outline,
                            focusedLabelColor = SendButton,
                            unfocusedLabelColor = OnSurfaceVariant
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false }
                    ) {
                        allLlmProviders.forEach { provider ->
                            val isBuiltIn = provider.id in builtInProviderIds
                            val shieldTint = when (provider.id) {
                                "fireworks", "together" -> SendButton
                                "ollama" -> OnSurfaceVariant
                                else -> null
                            }
                            DropdownMenuItem(
                                text = { Text(provider.name, color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    if (shieldTint != null) {
                                        Icon(Icons.Default.Security, null, tint = shieldTint)
                                    }
                                },
                                trailingIcon = if (!isBuiltIn) {
                                    {
                                        IconButton(
                                            onClick = {
                                                showRemoveProviderConfirm = provider
                                                providerDropdownExpanded = false
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else null,
                                onClick = {
                                    selectedProvider = provider.id
                                    val savedKey = llmApiKeysMap[provider.id]?.takeIf { it.isNotBlank() }
                                        ?: provider.apiKey.takeIf { it.isNotBlank() } ?: ""
                                    apiKey = savedKey
                                    providerDropdownExpanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Add Provider...", color = MaterialTheme.colorScheme.primary) },
                            leadingIcon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                providerDropdownExpanded = false
                                connectError = null
                                showAddProviderDialog = true
                            }
                        )
                    }
                }
                IconButton(
                    onClick = {
                        connectError = null
                        showLlmApiKeyDialog = true
                    },
                    enabled = selectedProvider.isNotBlank() && !isConnecting
                ) {
                    if (isConnecting && showLlmApiKeyDialog) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SendButton,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Connect to provider",
                            tint = if (selectedProvider.isNotBlank()) MaterialTheme.colorScheme.onSurface else OnSurfaceVariant
                        )
                    }
                }
            }

            if (currentProvider != null) {
                Text(
                    "Base URL: ${currentProvider.baseUrl}",
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            val modelDisplayName = availableModels.find { it.id == selectedModel }?.name
                ?: selectedModel.ifBlank { "" }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = modelDisplayName,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Default Model") },
                    placeholder = { Text(if (availableModels.isEmpty()) "Connect to fetch models" else "Tap to select model") },
                    trailingIcon = {
                        if (availableModels.isNotEmpty()) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedBorderColor = SendButton,
                        unfocusedBorderColor = Outline,
                        focusedLabelColor = SendButton,
                        unfocusedLabelColor = OnSurfaceVariant
                    )
                )
                if (availableModels.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showModelSelection = true }
                    )
                }
            }

            if (showModelSelection && availableModels.isNotEmpty()) {
                ModelSelectionDialog(
                    availableModels = availableModels,
                    currentModelId = selectedModel,
                    onModelSelected = { modelId ->
                        selectedModel = modelId
                        showModelSelection = false
                    },
                    onDismiss = { showModelSelection = false }
                )
            }

            HorizontalDivider(color = Outline)

            Text("Web Search Provider", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = webSearchProviderDropdownExpanded,
                    onExpandedChange = { webSearchProviderDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = allWebSearchProviders.find { it.id == selectedWebSearchProvider }?.name
                            ?: if (selectedWebSearchProvider.isNotBlank()) selectedWebSearchProvider else "",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Provider") },
                        placeholder = { Text("Select a web search provider") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = webSearchProviderDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            focusedContainerColor = SurfaceVariant,
                            unfocusedContainerColor = SurfaceVariant,
                            focusedBorderColor = SendButton,
                            unfocusedBorderColor = Outline,
                            focusedLabelColor = SendButton,
                            unfocusedLabelColor = OnSurfaceVariant
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = webSearchProviderDropdownExpanded,
                        onDismissRequest = { webSearchProviderDropdownExpanded = false }
                    ) {
                        allWebSearchProviders.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedWebSearchProvider = provider.id
                                    val savedKey = wsApiKeysMap[provider.id]?.takeIf { it.isNotBlank() }
                                        ?: provider.apiKey.takeIf { it.isNotBlank() } ?: ""
                                    webSearchApiKey = savedKey
                                    webSearchProviderDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { showWsApiKeyDialog = true },
                    enabled = selectedWebSearchProvider.isNotBlank() && !isConnecting
                ) {
                    if (isConnecting && showWsApiKeyDialog) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SendButton,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Connect to web search provider",
                            tint = if (selectedWebSearchProvider.isNotBlank()) MaterialTheme.colorScheme.onSurface else OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyConnectDialog(
    title: String,
    initialApiKey: String,
    isConnecting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle API Key visibility",
                                tint = SendButton
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedBorderColor = SendButton,
                        unfocusedBorderColor = Outline,
                        focusedLabelColor = SendButton,
                        unfocusedLabelColor = OnSurfaceVariant
                    )
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(apiKey) },
                enabled = apiKey.isNotBlank() && !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SendButton,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isConnecting
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    isConnecting: Boolean,
    error: String?,
    onConnect: (LlmProvider) -> Unit
) {
    val providerId by remember { mutableStateOf("custom_${UUID.randomUUID().toString().take(8)}") }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("Add Custom Provider", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
                    placeholder = { Text("My Provider") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedBorderColor = SendButton,
                        unfocusedBorderColor = Outline,
                        focusedLabelColor = SendButton,
                        unfocusedLabelColor = OnSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedBorderColor = SendButton,
                        unfocusedBorderColor = Outline,
                        focusedLabelColor = SendButton,
                        unfocusedLabelColor = OnSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedBorderColor = SendButton,
                        unfocusedBorderColor = Outline,
                        focusedLabelColor = SendButton,
                        unfocusedLabelColor = OnSurfaceVariant
                    )
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConnect(
                        LlmProvider(
                            id = providerId,
                            name = name.ifBlank { "Custom ($providerId)" },
                            baseUrl = baseUrl.ifBlank { "https://api.openai.com/v1" },
                            apiKey = apiKey
                        )
                    )
                },
                enabled = (name.isNotBlank() || baseUrl.isNotBlank()) && apiKey.isNotBlank() && !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SendButton,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SliderWithLabel(
    label: String,
    tooltip: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueDisplay: String,
    isInt: Boolean = false
) {
    var showTooltip by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { showTooltip = !showTooltip },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Tooltip for $label",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(valueDisplay, color = SendButton, style = MaterialTheme.typography.labelMedium)
        }

        if (showTooltip) {
            Surface(
                color = SurfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    tooltip,
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = SendButton,
                activeTrackColor = SendButton,
                inactiveTrackColor = Outline
            )
        )
    }
}
