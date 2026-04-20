package com.example.buddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ApiType {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI
}

object LlmProviders {
data class ProviderInfo(
        val id: String,
        val name: String,
        val baseUrl: String,
        val apiType: ApiType = ApiType.OPENAI_COMPATIBLE
    )

    val ALL = listOf(
        ProviderInfo("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", ApiType.OPENAI_COMPATIBLE),
        ProviderInfo("ollama", "Ollama Cloud", "https://ollama.com/v1/", ApiType.OPENAI_COMPATIBLE),
        ProviderInfo("anthropic", "Anthropic", "https://api.anthropic.com", ApiType.ANTHROPIC),
        ProviderInfo("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta", ApiType.GEMINI),
        ProviderInfo("openai", "OpenAI", "https://api.openai.com/v1", ApiType.OPENAI_COMPATIBLE),
        ProviderInfo("xai", "xAI", "https://api.x.ai/v1", ApiType.OPENAI_COMPATIBLE)
    )

    fun getBaseUrl(providerId: String): String {
        return ALL.find { it.id == providerId }?.baseUrl ?: ""
    }

    fun getApiType(providerId: String): ApiType {
        return ALL.find { it.id == providerId }?.apiType ?: ApiType.OPENAI_COMPATIBLE
    }

    fun fromBaseUrl(baseUrl: String): String {
        return ALL.find { it.baseUrl == baseUrl }?.id ?: ""
    }
}

object WebSearchProviders {
    data class ProviderInfo(
        val id: String,
        val name: String
    )

    val ALL = listOf(
        ProviderInfo("tavily", "Tavily")
    )
}

object SettingsKeys {
    val PROVIDER = stringPreferencesKey("llm_provider")
    val API_KEY = stringPreferencesKey("llm_api_key")
    val MODEL = stringPreferencesKey("llm_model")
    val TEMPERATURE = floatPreferencesKey("llm_temperature")
    val TOP_P = floatPreferencesKey("llm_top_p")
    val TOP_K = intPreferencesKey("llm_top_k")
    val MAX_TOKENS = intPreferencesKey("llm_max_tokens")
    val REASONING_EFFORT = stringPreferencesKey("llm_reasoning_effort")
    val SYSTEM_MESSAGE = stringPreferencesKey("llm_system_message")
    val WEBSEARCH_PROVIDER = stringPreferencesKey("websearch_provider")
    val TAVILY_API_KEY = stringPreferencesKey("tavily_api_key")
}

data class LlmSettings(
    val provider: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Float = 0f,
    val topP: Float = 0f,
    val topK: Int = 0,
    val maxTokens: Int = 0,
    val reasoningEffort: String = "",
    val systemMessage: String = "",
    val webSearchProvider: String = "",
    val tavilyApiKey: String = ""
) {
    val baseUrl: String
        get() = LlmProviders.getBaseUrl(provider)
}

class SettingsRepository(context: Context) {
    private val dataStore = context.dataStore

    val settings: Flow<LlmSettings> = dataStore.data.map { prefs ->
        LlmSettings(
            provider = prefs[SettingsKeys.PROVIDER] ?: "",
            apiKey = prefs[SettingsKeys.API_KEY] ?: "",
            model = prefs[SettingsKeys.MODEL] ?: "",
            temperature = prefs[SettingsKeys.TEMPERATURE] ?: LlmDefaults.temperature,
            topP = prefs[SettingsKeys.TOP_P] ?: LlmDefaults.topP,
            topK = prefs[SettingsKeys.TOP_K] ?: LlmDefaults.topK,
            maxTokens = prefs[SettingsKeys.MAX_TOKENS] ?: LlmDefaults.maxTokens,
            reasoningEffort = prefs[SettingsKeys.REASONING_EFFORT] ?: "",
            systemMessage = prefs[SettingsKeys.SYSTEM_MESSAGE] ?: LlmDefaults.defaultSystemMessage,
            webSearchProvider = prefs[SettingsKeys.WEBSEARCH_PROVIDER] ?: "",
            tavilyApiKey = prefs[SettingsKeys.TAVILY_API_KEY] ?: ""
        )
    }

    val isConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        !prefs[SettingsKeys.PROVIDER].isNullOrEmpty() && !prefs[SettingsKeys.API_KEY].isNullOrEmpty()
    }

    suspend fun updateAll(
        provider: String,
        apiKey: String,
        model: String,
        temperature: Float = LlmDefaults.temperature,
        topP: Float = LlmDefaults.topP,
        topK: Int = LlmDefaults.topK,
        maxTokens: Int = LlmDefaults.maxTokens,
        reasoningEffort: String = "",
        systemMessage: String = LlmDefaults.defaultSystemMessage,
        webSearchProvider: String = "",
        tavilyApiKey: String = ""
    ) {
        dataStore.edit {
            it[SettingsKeys.PROVIDER] = provider
            it[SettingsKeys.API_KEY] = apiKey
            it[SettingsKeys.MODEL] = model
            it[SettingsKeys.TEMPERATURE] = temperature
            it[SettingsKeys.TOP_P] = topP
            it[SettingsKeys.TOP_K] = topK
            it[SettingsKeys.MAX_TOKENS] = maxTokens
            it[SettingsKeys.REASONING_EFFORT] = reasoningEffort
            it[SettingsKeys.SYSTEM_MESSAGE] = systemMessage
            it[SettingsKeys.WEBSEARCH_PROVIDER] = webSearchProvider
            it[SettingsKeys.TAVILY_API_KEY] = tavilyApiKey
        }
    }
}
