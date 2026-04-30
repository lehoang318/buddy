package com.example.buddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.buddy.data.BuiltInProviders
import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmDefaults
import com.example.buddy.data.LlmProvider
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SettingsRepository
import com.example.buddy.data.WebSearchProvider
import com.example.buddy.ext.LinkUpWebSearch
import com.example.buddy.ext.ExaWebSearch
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

        BackgroundScheduler.scheduleConnectivityChecks(this)

        val builtInLlmProviders = BuiltInProviders.loadLlmProviders(this)
        val builtInWebSearchProviders = BuiltInProviders.loadWebSearchProviders(this)

        lifecycleScope.launch {
            combine(
                combine(
                    settingsRepository.settings,
                    settingsRepository.isConfigured,
                    settingsRepository.customLlmProviders,
                    settingsRepository.customWebSearchProviders
                ) { settings, isConfigured, customLlm, customWs ->
                    Quadruple(settings, isConfigured, customLlm, customWs)
                },
                settingsRepository.llmApiKeysByProvider,
                settingsRepository.webSearchApiKeysByProvider
            ) { base, llmKeys, wsKeys ->
                val settings = base.first
                val isConfigured = base.second
                val customLlmProviders = base.third
                val customWebSearchProviders = base.fourth
                currentSettingsFlow.value = settings

                val allLlmProviders = builtInLlmProviders + customLlmProviders
                val llmProvider = allLlmProviders.find { it.id == settings.provider }

                if (isConfigured && llmProvider != null) {
                    val resolvedApiKey = llmKeys[llmProvider.id]?.takeIf { it.isNotBlank() }
                        ?: llmProvider.apiKey.takeIf { it.isNotBlank() }
                        ?: settings.apiKey
                    val result = LlmClientFactory.createWithProvider(llmProvider, resolvedApiKey, settings.model)
                    result.onSuccess { client ->
                        llmClientFlow.value = client
                    }.onFailure {
                        llmClientFlow.value = null
                    }
                } else {
                    llmClientFlow.value = null
                }

                val wsProviderId = settings.webSearchProvider
                val allWsProviders = builtInWebSearchProviders + customWebSearchProviders
                val wsProvider = allWsProviders.find { it.id == wsProviderId }
                val wsApiKey = wsKeys[wsProviderId]?.takeIf { it.isNotBlank() }
                    ?: wsProvider?.apiKey?.takeIf { it.isNotBlank() }
                    ?: settings.webSearchApiKey

                if (wsProvider != null && wsApiKey.isNotBlank()) {
                    webSearchFlow.value = createWebSearch(wsProviderId, wsApiKey) ?: TavilyWebSearch(wsApiKey)
                } else {
                    webSearchFlow.value = null
                }
            }.collect { }
        }

        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                MainContent(llmClientFlow, webSearchFlow, urlFetcherFlow, currentSettingsFlow, settingsRepository)
            }
        }
    }

    private fun createWebSearch(providerId: String, apiKey: String): WebSearch? {
        return when (providerId) {
            "linkup" -> LinkUpWebSearch(apiKey)
            "exa" -> ExaWebSearch(apiKey)
            "tavily" -> TavilyWebSearch(apiKey)
            else -> null
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
                        webSearchApiKey = currentSettings.webSearchApiKey
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
            settingsRepository = settingsRepository,
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
                        webSearchApiKey = settings.webSearchApiKey
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
