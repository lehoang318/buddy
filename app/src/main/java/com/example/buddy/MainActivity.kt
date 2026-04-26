package com.example.buddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmDefaults
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SettingsRepository
import com.example.buddy.ext.JsoupUrlFetcher
import com.example.buddy.ext.LlmClient
import com.example.buddy.ext.LlmClientFactory
import com.example.buddy.ext.TavilyWebSearch
import com.example.buddy.ext.UrlFetcher
import com.example.buddy.ext.WebSearch
import com.example.buddy.ui.chat.ChatScreen
import com.example.buddy.ui.events.EventsScreen
import com.example.buddy.ui.parameters.ParametersScreen
import com.example.buddy.ui.settings.SettingsScreen
import com.example.buddy.ui.about.AboutScreen
import com.example.buddy.ui.theme.BuddyTheme
import com.example.buddy.service.BackgroundScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "Settings"

val LocalLlmClient = compositionLocalOf<LlmClient?> { null }
val LocalWebSearch = compositionLocalOf<WebSearch?> { null }
val LocalUrlFetcher = compositionLocalOf<UrlFetcher?> { null }

@Composable
fun ProvideLlmClient(llmClient: LlmClient, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLlmClient provides llmClient) { content() }
}

@Composable
fun ProvideWebSearch(webSearch: WebSearch?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWebSearch provides webSearch) { content() }
}

@Composable
fun ProvideUrlFetcher(urlFetcher: UrlFetcher?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUrlFetcher provides urlFetcher) { content() }
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private val llmClientFlow = MutableStateFlow<LlmClient?>(null)
    private val webSearchFlow = MutableStateFlow<WebSearch?>(null)
    private val urlFetcherFlow = MutableStateFlow<UrlFetcher?>(JsoupUrlFetcher())
    private val currentSettingsFlow = MutableStateFlow(LlmSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LlmDefaults.init(this)
        settingsRepository = SettingsRepository(this)
        
        // Schedule background connectivity checks
        BackgroundScheduler.scheduleConnectivityChecks(this)
        
        lifecycleScope.launch {
            settingsRepository.settings.combine(settingsRepository.isConfigured) { settings, isConfigured ->
                Pair(settings, isConfigured)
            }.collect { pair ->
                val settings = pair.first
                val isConfigured = pair.second
                currentSettingsFlow.value = settings
                
                if (isConfigured && settings.baseUrl.isNotBlank() && settings.apiKey.isNotBlank()) {
                    val result = LlmClientFactory.createWithProviderId(
                        providerId = settings.provider,
                        apiKey = settings.apiKey,
                        model = settings.model
                    )
                    result.onSuccess { client ->
                        llmClientFlow.value = client
                    }.onFailure {
                        llmClientFlow.value = null
                    }
                } else {
                    llmClientFlow.value = null
                }

                if (settings.webSearchProvider == "tavily" && settings.tavilyApiKey.isNotBlank()) {
                    webSearchFlow.value = TavilyWebSearch(settings.tavilyApiKey)
                } else {
                    webSearchFlow.value = null
                }
            }
        }
        
        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                MainContent(llmClientFlow, webSearchFlow, urlFetcherFlow, currentSettingsFlow, settingsRepository)
            }
        }
    }
}

@Composable
fun MainContent(
    llmClientFlow: StateFlow<LlmClient?>,
    webSearchFlow: StateFlow<WebSearch?>,
    urlFetcherFlow: StateFlow<UrlFetcher?>,
    currentSettingsFlow: StateFlow<LlmSettings>,
    settingsRepository: SettingsRepository
) {
    val llmClient by llmClientFlow.collectAsStateWithLifecycle()
    val webSearch by webSearchFlow.collectAsStateWithLifecycle()
    val urlFetcher by urlFetcherFlow.collectAsStateWithLifecycle()
    val currentSettings by currentSettingsFlow.collectAsStateWithLifecycle()
    
    var showSettings by remember { mutableStateOf(false) }
    var showParameters by remember { mutableStateOf(false) }
    var showEvents by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    if (showParameters) {
        ParametersScreen(
            onBack = { showParameters = false },
            initialSettings = currentSettings,
            onSaveParameters = { temp, topP, topK, sysMsg ->
                scope.launch {
                    settingsRepository.updateAll(
                        provider = currentSettings.provider,
                        apiKey = currentSettings.apiKey,
                        model = currentSettings.model,
                        temperature = temp,
                        topP = topP,
                        topK = topK,
                        systemMessage = sysMsg,
                        webSearchProvider = currentSettings.webSearchProvider,
                        tavilyApiKey = currentSettings.tavilyApiKey
                    )
                }
            }
        )
    } else if (showEvents) {
        EventsScreen(onBack = { showEvents = false })
    } else if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
    } else if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            onSettingsSaved = { showSettings = false },
            initialSettings = currentSettings,
            onSaveModelSettings = { settings ->
                EventLog.info(TAG, "Model settings saved", "provider=${settings.provider}, model=${settings.model}")
                scope.launch {
                    settingsRepository.updateAll(
                        provider = currentSettings.provider,
                        apiKey = currentSettings.apiKey,
                        model = settings.model,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        topK = settings.topK,
                        webSearchProvider = settings.webSearchProvider,
                        tavilyApiKey = settings.tavilyApiKey
                    )
                }
            }
        )
    } else {
        ProvideUrlFetcher(urlFetcher) {
            if (llmClient != null) {
                ProvideLlmClient(llmClient!!) {
                    ProvideWebSearch(webSearch) {
                        ChatScreen(
                            onNavigateToSettings = { showSettings = true },
                            onNavigateToParameters = { showParameters = true },
                            onNavigateToEvents = { showEvents = true },
                            onNavigateToAbout = { showAbout = true }
                        )
                    }
                }
            } else {
                ProvideWebSearch(webSearch) {
                        ChatScreen(
                            onNavigateToSettings = { showSettings = true },
                            onNavigateToParameters = { showParameters = true },
                            onNavigateToEvents = { showEvents = true },
                            onNavigateToAbout = { showAbout = true }
                        )
                }
            }
        }
    }
}
