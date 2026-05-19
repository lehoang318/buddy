package com.example.buddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.buddy.crypto.ApiKeyInterceptor
import com.example.buddy.crypto.ExaApiKeyInterceptor
import com.example.buddy.crypto.SessionKeyCache
import com.example.buddy.data.BuiltInProviders
import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmDefaults
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SettingsRepository
import com.example.buddy.ext.ExaWebSearch
import com.example.buddy.ext.JsoupUrlFetcher
import com.example.buddy.ext.LinkUpWebSearch
import com.example.buddy.ext.LlmClient
import com.example.buddy.ext.LlmClientFactory
import com.example.buddy.ext.TavilyWebSearch
import com.example.buddy.ext.UrlFetcher
import com.example.buddy.ext.WebSearch
import com.example.buddy.service.BackgroundScheduler
import com.example.buddy.ui.about.AboutScreen
import com.example.buddy.ui.chat.ChatScreen
import com.example.buddy.ui.events.EventsScreen
import com.example.buddy.ui.parameters.ParametersScreen
import com.example.buddy.ui.settings.SettingsScreen
import com.example.buddy.ui.theme.BuddyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
    private lateinit var keyCache: SessionKeyCache
    private val llmClientFlow = MutableStateFlow<LlmClient?>(null)
    private val webSearchFlow = MutableStateFlow<WebSearch?>(null)
    private val urlFetcherFlow = MutableStateFlow<UrlFetcher?>(JsoupUrlFetcher())
    private val currentSettingsFlow = MutableStateFlow(LlmSettings())
    private var lastLlmClientKey = Pair("", "")
    private var lastWebSearchProvider = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LlmDefaults.init(this)
        EventLog.init(this)
        settingsRepository = SettingsRepository(this)
        keyCache = SessionKeyCache(this)

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                keyCache.clearCache()
            }
        })

        BackgroundScheduler.scheduleConnectivityChecks(this)

        val builtInLlmProviders = BuiltInProviders.loadLlmProviders(this)
        val builtInWebSearchProviders = BuiltInProviders.loadWebSearchProviders(this)

        lifecycleScope.launch {
            settingsRepository.migrateKeysToSessionCache(keyCache)
        }

        lifecycleScope.launch {
            combine(
                settingsRepository.settings,
                settingsRepository.customLlmProviders,
                settingsRepository.customWebSearchProviders,
                keyCache.keyIds
            ) { settings, customLlm, customWs, _ ->
                currentSettingsFlow.value = settings

                val allLlmProviders = builtInLlmProviders + customLlm
                val llmProvider = allLlmProviders.find { it.id == settings.provider }

                if (llmProvider != null) {
                    val hasKey = keyCache.getKey(llmProvider.id)?.also { it.fill(0) } != null
                    if (hasKey) {
                        val clientKey = Pair(llmProvider.id, settings.model)
                        if (clientKey != lastLlmClientKey) {
                            lastLlmClientKey = clientKey
                            val result = LlmClientFactory.createWithProvider(llmProvider, keyCache, settings.model)
                            result.onSuccess { llmClientFlow.value = it }
                                .onFailure { llmClientFlow.value = null }
                        }
                    } else {
                        if (lastLlmClientKey != Pair("", "")) {
                            lastLlmClientKey = Pair("", "")
                            llmClientFlow.value = null
                        }
                    }
                } else {
                    if (lastLlmClientKey != Pair("", "")) {
                        lastLlmClientKey = Pair("", "")
                        llmClientFlow.value = null
                    }
                }

                val allWsProviders = builtInWebSearchProviders + customWs
                val wsProvider = allWsProviders.find { it.id == settings.webSearchProvider }

                if (wsProvider != null) {
                    val hasWsKey = keyCache.getKey("ws_${wsProvider.id}")?.also { it.fill(0) } != null
                    if (wsProvider.id != lastWebSearchProvider || (hasWsKey && webSearchFlow.value == null)) {
                        lastWebSearchProvider = wsProvider.id
                        webSearchFlow.value = if (hasWsKey) createWebSearch(keyCache, wsProvider.id) else null
                    } else if (!hasWsKey && webSearchFlow.value != null) {
                        lastWebSearchProvider = ""
                        webSearchFlow.value = null
                    }
                } else {
                    lastWebSearchProvider = ""
                    webSearchFlow.value = null
                }
            }.collect { }
        }

        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                MainContent(llmClientFlow, webSearchFlow, urlFetcherFlow, currentSettingsFlow, settingsRepository, keyCache)
            }
        }
    }

    private fun createWebSearch(keyCache: SessionKeyCache, providerId: String): WebSearch? {
        return when (providerId) {
            "linkup" -> {
                val httpClient = OkHttpClient.Builder()
                    .addInterceptor(ApiKeyInterceptor(keyCache, "ws_$providerId"))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(ConnectionPool(3, 3, TimeUnit.MINUTES))
                    .build()
                LinkUpWebSearch(httpClient, keyCache, "ws_$providerId")
            }
            "exa" -> {
                val httpClient = OkHttpClient.Builder()
                    .addInterceptor(ExaApiKeyInterceptor(keyCache, "ws_$providerId"))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(ConnectionPool(3, 3, TimeUnit.MINUTES))
                    .build()
                ExaWebSearch(httpClient, keyCache, "ws_$providerId")
            }
            "tavily" -> TavilyWebSearch(keyCache, "ws_$providerId")
            else -> null
        }
    }
}

@Composable
fun MainContent(
    llmClientFlow: StateFlow<LlmClient?>,
    webSearchFlow: StateFlow<WebSearch?>,
    urlFetcherFlow: StateFlow<UrlFetcher?>,
    currentSettingsFlow: StateFlow<LlmSettings>,
    settingsRepository: SettingsRepository,
    keyCache: SessionKeyCache
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
                        model = currentSettings.model,
                        temperature = temp,
                        topP = topP,
                        topK = topK,
                        systemMessage = sysMsg,
                        webSearchProvider = currentSettings.webSearchProvider
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
            initialSettings = currentSettings,
            settingsRepository = settingsRepository,
            keyCache = keyCache,
            onSaveModelSettings = { settings ->
                EventLog.info(TAG, "Model settings saved", "provider=${settings.provider}, model=${settings.model}")
                scope.launch {
                    settingsRepository.updateAll(
                        provider = currentSettings.provider,
                        model = settings.model,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        topK = settings.topK,
                        webSearchProvider = settings.webSearchProvider
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
