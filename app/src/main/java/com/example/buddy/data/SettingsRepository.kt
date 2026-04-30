package com.example.buddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ApiType {
    OPENAI_COMPATIBLE
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
    val WEBSEARCH_API_KEY = stringPreferencesKey("websearch_api_key")
    val CUSTOM_LLM_PROVIDERS = stringPreferencesKey("custom_llm_providers")
    val CUSTOM_WEBSEARCH_PROVIDERS = stringPreferencesKey("custom_websearch_providers")
    val LLM_API_KEYS = stringPreferencesKey("llm_api_keys")
    val WEBSEARCH_API_KEYS = stringPreferencesKey("websearch_api_keys")
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
    val webSearchApiKey: String = "",
    val customLlmProvidersJson: String = "",
    val customWebSearchProvidersJson: String = ""
)

class SettingsRepository(private val context: Context) {
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
            webSearchApiKey = prefs[SettingsKeys.WEBSEARCH_API_KEY] ?: "",
            customLlmProvidersJson = prefs[SettingsKeys.CUSTOM_LLM_PROVIDERS] ?: "",
            customWebSearchProvidersJson = prefs[SettingsKeys.CUSTOM_WEBSEARCH_PROVIDERS] ?: ""
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
        webSearchApiKey: String = ""
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
            it[SettingsKeys.WEBSEARCH_API_KEY] = webSearchApiKey
        }
    }

    suspend fun addCustomLlmProvider(provider: LlmProvider) {
        dataStore.edit {
            val current = it[SettingsKeys.CUSTOM_LLM_PROVIDERS] ?: ""
            val list = BuiltInProviders.deserializeProviderData(current).toMutableList()
            list.removeAll { p -> p.id == provider.id }
            list.add(provider.toProviderData())
            it[SettingsKeys.CUSTOM_LLM_PROVIDERS] = BuiltInProviders.serializeProviderData(list)
        }
    }

    suspend fun removeCustomLlmProvider(providerId: String) {
        dataStore.edit {
            val current = it[SettingsKeys.CUSTOM_LLM_PROVIDERS] ?: ""
            val list = BuiltInProviders.deserializeProviderData(current).toMutableList()
            list.removeAll { p -> p.id == providerId }
            it[SettingsKeys.CUSTOM_LLM_PROVIDERS] = BuiltInProviders.serializeProviderData(list)
        }
    }

    val customLlmProviders: Flow<List<LlmProvider>> = dataStore.data.map { prefs ->
        val json = prefs[SettingsKeys.CUSTOM_LLM_PROVIDERS] ?: ""
        BuiltInProviders.deserializeProviderData(json).map { it.toLlmProvider() }
    }

    val customWebSearchProviders: Flow<List<WebSearchProvider>> = dataStore.data.map { prefs ->
        val json = prefs[SettingsKeys.CUSTOM_WEBSEARCH_PROVIDERS] ?: ""
        BuiltInProviders.deserializeProviderData(json).map { it.toWebSearchProvider() }
    }

    val allLlmProviders: Flow<List<LlmProvider>> = dataStore.data.map { prefs ->
        val builtIn = BuiltInProviders.loadLlmProviders(context)
        val custom = BuiltInProviders.deserializeProviderData(
            prefs[SettingsKeys.CUSTOM_LLM_PROVIDERS] ?: ""
        ).map { it.toLlmProvider() }
        builtIn + custom
    }

    val allWebSearchProviders: Flow<List<WebSearchProvider>> = dataStore.data.map { prefs ->
        val builtIn = BuiltInProviders.loadWebSearchProviders(context)
        val custom = BuiltInProviders.deserializeProviderData(
            prefs[SettingsKeys.CUSTOM_WEBSEARCH_PROVIDERS] ?: ""
        ).map { it.toWebSearchProvider() }
        builtIn + custom
    }

    val llmApiKeysByProvider: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        val json = prefs[SettingsKeys.LLM_API_KEYS] ?: ""
        if (json.isBlank()) emptyMap()
        else try { Gson().fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap() }
        catch (_: Exception) { emptyMap() }
    }

    val webSearchApiKeysByProvider: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        val json = prefs[SettingsKeys.WEBSEARCH_API_KEYS] ?: ""
        if (json.isBlank()) emptyMap()
        else try { Gson().fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap() }
        catch (_: Exception) { emptyMap() }
    }

    suspend fun saveLlmApiKey(providerId: String, apiKey: String) {
        val gson = Gson()
        dataStore.edit {
            val json = it[SettingsKeys.LLM_API_KEYS] ?: ""
            val map = if (json.isBlank()) mutableMapOf<String, String>()
            else try { gson.fromJson<MutableMap<String, String>>(json, object : TypeToken<MutableMap<String, String>>() {}.type) }
            catch (_: Exception) { mutableMapOf() }
            map[providerId] = apiKey
            it[SettingsKeys.LLM_API_KEYS] = gson.toJson(map)
        }
    }

    suspend fun saveWebSearchApiKey(providerId: String, apiKey: String) {
        val gson = Gson()
        dataStore.edit {
            val json = it[SettingsKeys.WEBSEARCH_API_KEYS] ?: ""
            val map = if (json.isBlank()) mutableMapOf<String, String>()
            else try { gson.fromJson<MutableMap<String, String>>(json, object : TypeToken<MutableMap<String, String>>() {}.type) }
            catch (_: Exception) { mutableMapOf() }
            map[providerId] = apiKey
            it[SettingsKeys.WEBSEARCH_API_KEYS] = gson.toJson(map)
        }
    }
}
