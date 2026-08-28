package com.example.buddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.buddy.crypto.SessionKeyCache
import com.example.buddy.config.AndroidAppConfig
import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.BuiltInProviders
import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SessionImageStore
import com.example.buddy.data.SessionRepository
import com.example.buddy.data.SettingsRepository
import com.example.buddy.fetch.JsoupUrlFetcher
import com.example.buddy.fetch.UrlFetcher
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmClientFactory
import com.example.buddy.search.WebSearch
import com.example.buddy.search.WebSearchFactory
import com.example.buddy.logging.Log
import com.example.buddy.service.BackgroundScheduler
import com.example.buddy.ui.about.AboutScreen
import com.example.buddy.ui.chat.ChatScreen
import com.example.buddy.ui.chat.ChatViewModel
import com.example.buddy.ui.chat.ChatViewModelFactory
import com.example.buddy.ui.events.EventsScreen
import com.example.buddy.ui.history.HistoryScreen
import com.example.buddy.ui.parameters.ParametersScreen
import com.example.buddy.ui.providers.ProvidersScreen
import com.example.buddy.ui.theme.BuddyTheme
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
    private lateinit var keyCache: SessionKeyCache
    private val llmClientFlow = MutableStateFlow<LlmClient?>(null)
    private val webSearchFlow = MutableStateFlow<WebSearch?>(null)
    private val urlFetcherFlow = MutableStateFlow<UrlFetcher?>(JsoupUrlFetcher())
    private val currentSettingsFlow = MutableStateFlow(LlmSettings())
    private var lastLlmClientKey = Pair("", "")
    private var lastWebSearchProvider = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfigProvider.current = AndroidAppConfig(this)
        Log.logger = EventLog
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
            val sessionRepository = SessionRepository(this@MainActivity)
            if (sessionRepository.autoDeleteEnabled()) {
                val removed = sessionRepository.purgeOlderThan(SessionRepository.AUTO_DELETE_AGE_MILLIS)
                if (removed.isNotEmpty()) {
                    SessionImageStore(this@MainActivity).delete(removed)
                }
            }
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
                        webSearchFlow.value = if (hasWsKey) WebSearchFactory.create(keyCache, wsProvider.id) else null
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

    var showProviders by remember { mutableStateOf(false) }
    var showParameters by remember { mutableStateOf(false) }
    var showEvents by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val application = LocalContext.current.applicationContext as android.app.Application

    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(application)
    )

    val resumeRepository = remember { SessionRepository(application) }
    LaunchedEffect(Unit) {
        val last = resumeRepository.latestSession()
        if (last != null) chatViewModel.resumeSession(last)
    }

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
    } else if (showHistory) {
        HistoryScreen(
            onBack = { showHistory = false },
            onResume = { session ->
                chatViewModel.resumeSession(session)
                showHistory = false
            }
        )
    } else if (showProviders) {
        ProvidersScreen(
            onBack = { showProviders = false },
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
                            onNavigateToProviders = { showProviders = true },
                            onNavigateToParameters = { showParameters = true },
                            onNavigateToEvents = { showEvents = true },
                            onNavigateToAbout = { showAbout = true },
                            onNavigateToHistory = { showHistory = true }
                        )
                    }
                }
            } else {
                ProvideWebSearch(webSearch) {
                    ChatScreen(
                        onNavigateToProviders = { showProviders = true },
                        onNavigateToParameters = { showParameters = true },
                        onNavigateToEvents = { showEvents = true },
                        onNavigateToAbout = { showAbout = true },
                        onNavigateToHistory = { showHistory = true }
                    )
                }
            }
        }
    }
}
