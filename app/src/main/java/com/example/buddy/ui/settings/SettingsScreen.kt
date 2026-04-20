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
import com.example.buddy.data.LlmProviders
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SettingsRepository
import com.example.buddy.data.WebSearchProviders
import com.example.buddy.ext.LlmModel
import com.example.buddy.ext.LlmClientFactory
import com.example.buddy.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSettingsSaved: () -> Unit,
    initialSettings: LlmSettings? = null,
    onSaveModelSettings: (LlmSettings) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    val savedSettings by settingsRepository.settings.collectAsState(initial = initialSettings ?: LlmSettings())
    val effectiveInitial = initialSettings ?: savedSettings

    var selectedProvider by remember(effectiveInitial.provider) { mutableStateOf(effectiveInitial.provider) }
    var apiKey by remember(effectiveInitial.apiKey) { mutableStateOf(effectiveInitial.apiKey) }
    var selectedModel by remember(effectiveInitial.model) { mutableStateOf(effectiveInitial.model) }

    var selectedWebSearchProvider by remember(effectiveInitial.webSearchProvider) { mutableStateOf(effectiveInitial.webSearchProvider) }
    var tavilyApiKey by remember(effectiveInitial.tavilyApiKey) { mutableStateOf(effectiveInitial.tavilyApiKey) }

    var showApiKey by remember { mutableStateOf(false) }
    var showTavilyKey by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<LlmModel>>(emptyList()) }
    var showModelSelection by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var webSearchProviderDropdownExpanded by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }

    val baseUrl = LlmProviders.getBaseUrl(selectedProvider)
    val canConnect = selectedProvider.isNotBlank() && apiKey.isNotBlank()
    val refreshEnabled = canConnect && !isConnecting

    fun handleConnect() {
        if (baseUrl.isBlank()) {
            connectError = "Unknown provider selected"
            showErrorDialog = true
            return
        }
        connectError = null
        isConnecting = true
        coroutineScope.launch {
            try {
                val models = LlmClientFactory.getModels(selectedProvider, apiKey)
                if (models.isEmpty()) {
                    connectError = "No models available. Please check your API Key."
                } else {
                    availableModels = models
                    selectedModel = models.first().id
                    
                    val testResult = LlmClientFactory.createWithProviderId(selectedProvider, apiKey, selectedModel)
                    testResult.fold(
                        onSuccess = { testClient ->
                            val connected = testClient.testConnection()
                            if (connected) {
                                settingsRepository.updateAll(
                                    provider = selectedProvider,
                                    apiKey = apiKey,
                                    model = selectedModel,
                                    webSearchProvider = selectedWebSearchProvider,
                                    tavilyApiKey = tavilyApiKey
                                )
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
                if (connectError != null) {
                    showErrorDialog = true
                }
            }
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Connection Error") },
            text = { Text(connectError ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
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
                                tavilyApiKey = tavilyApiKey
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

            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded,
                onExpandedChange = { providerDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = LlmProviders.ALL.find { it.id == selectedProvider }?.name
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
                    LlmProviders.ALL.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                selectedProvider = provider.id
                                providerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
                IconButton(
                    onClick = { handleConnect() },
                    enabled = refreshEnabled
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SendButton,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Sync, null, tint = if (refreshEnabled) MaterialTheme.colorScheme.onSurface else OnSurfaceVariant)
                    }
                }
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

            ExposedDropdownMenuBox(
                expanded = webSearchProviderDropdownExpanded,
                onExpandedChange = { webSearchProviderDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = WebSearchProviders.ALL.find { it.id == selectedWebSearchProvider }?.name
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
                    WebSearchProviders.ALL.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                selectedWebSearchProvider = provider.id
                                webSearchProviderDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = tavilyApiKey,
                onValueChange = { tavilyApiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("tvly-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showTavilyKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = { showTavilyKey = !showTavilyKey }) {
                        Icon(
                            imageVector = if (showTavilyKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
        }
    }
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