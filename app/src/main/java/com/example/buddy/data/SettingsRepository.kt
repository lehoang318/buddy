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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ApiType {
    OPENAI_COMPATIBLE
}

object SettingsKeys {
    val PROVIDER = stringPreferencesKey("llm_provider")
    val MODEL = stringPreferencesKey("llm_model")
    val TEMPERATURE = floatPreferencesKey("llm_temperature")
    val TOP_P = floatPreferencesKey("llm_top_p")
    val TOP_K = intPreferencesKey("llm_top_k")
    val MAX_TOKENS = intPreferencesKey("llm_max_tokens")
    val REASONING_EFFORT = stringPreferencesKey("llm_reasoning_effort")
    val SYSTEM_MESSAGE = stringPreferencesKey("llm_system_message")
    val WEBSEARCH_PROVIDER = stringPreferencesKey("websearch_provider")
    val CUSTOM_LLM_PROVIDERS = stringPreferencesKey("custom_llm_providers")
    val CUSTOM_WEBSEARCH_PROVIDERS = stringPreferencesKey("custom_websearch_providers")
}

data class LlmSettings(
    val provider: String = "",
    val model: String = "",
    val temperature: Float = 0f,
    val topP: Float = 0f,
    val topK: Int = 0,
    val maxTokens: Int = 0,
    val reasoningEffort: String = "",
    val systemMessage: String = "",
    val webSearchProvider: String = "",
    val customLlmProvidersJson: String = "",
    val customWebSearchProvidersJson: String = ""
)

class SettingsRepository(private val context: Context) {
    private val dataStore = context.dataStore

    val settings: Flow<LlmSettings> = dataStore.data.map { prefs ->
        LlmSettings(
            provider = prefs[SettingsKeys.PROVIDER] ?: "",
            model = prefs[SettingsKeys.MODEL] ?: "",
            temperature = prefs[SettingsKeys.TEMPERATURE] ?: LlmDefaults.temperature,
            topP = prefs[SettingsKeys.TOP_P] ?: LlmDefaults.topP,
            topK = prefs[SettingsKeys.TOP_K] ?: LlmDefaults.topK,
            maxTokens = prefs[SettingsKeys.MAX_TOKENS] ?: LlmDefaults.maxTokens,
            reasoningEffort = prefs[SettingsKeys.REASONING_EFFORT] ?: "",
            systemMessage = prefs[SettingsKeys.SYSTEM_MESSAGE] ?: LlmDefaults.defaultSystemMessage,
            webSearchProvider = prefs[SettingsKeys.WEBSEARCH_PROVIDER] ?: "",
            customLlmProvidersJson = prefs[SettingsKeys.CUSTOM_LLM_PROVIDERS] ?: "",
            customWebSearchProvidersJson = prefs[SettingsKeys.CUSTOM_WEBSEARCH_PROVIDERS] ?: ""
        )
    }

    suspend fun updateAll(
        provider: String,
        model: String,
        temperature: Float = LlmDefaults.temperature,
        topP: Float = LlmDefaults.topP,
        topK: Int = LlmDefaults.topK,
        maxTokens: Int = LlmDefaults.maxTokens,
        reasoningEffort: String = "",
        systemMessage: String = LlmDefaults.defaultSystemMessage,
        webSearchProvider: String = ""
    ) {
        dataStore.edit {
            it[SettingsKeys.PROVIDER] = provider
            it[SettingsKeys.MODEL] = model
            it[SettingsKeys.TEMPERATURE] = temperature
            it[SettingsKeys.TOP_P] = topP
            it[SettingsKeys.TOP_K] = topK
            it[SettingsKeys.MAX_TOKENS] = maxTokens
            it[SettingsKeys.REASONING_EFFORT] = reasoningEffort
            it[SettingsKeys.SYSTEM_MESSAGE] = systemMessage
            it[SettingsKeys.WEBSEARCH_PROVIDER] = webSearchProvider
        }
    }

    suspend fun addCustomLlmProvider(provider: LlmProvider) {
        dataStore.edit {
            val current = it[SettingsKeys.CUSTOM_LLM_PROVIDERS] ?: ""
            val list = BuiltInProviders.deserializeProviderData(current).toMutableList()
            list.removeAll { p -> p.id == provider.id }
            list.add(provider.toProviderDataWithoutKey())
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

    suspend fun migrateKeysToSessionCache(keyCache: com.example.buddy.crypto.SessionKeyCache) {
        dataStore.data.first().let { prefs ->
            val llmKeysJson = prefs[stringPreferencesKey("llm_api_keys")] ?: ""
            if (llmKeysJson.isNotBlank()) {
                try {
                    val map: Map<String, String> = Gson().fromJson(llmKeysJson, object : TypeToken<Map<String, String>>() {}.type)
                    map.forEach { (id, key) -> if (key.isNotBlank()) keyCache.saveKey(id, key) }
                } catch (_: Exception) {}
            }
            val wsKeysJson = prefs[stringPreferencesKey("websearch_api_keys")] ?: ""
            if (wsKeysJson.isNotBlank()) {
                try {
                    val map: Map<String, String> = Gson().fromJson(wsKeysJson, object : TypeToken<Map<String, String>>() {}.type)
                    map.forEach { (id, key) -> if (key.isNotBlank()) keyCache.saveKey("ws_$id", key) }
                } catch (_: Exception) {}
            }
            val legacyApiKey = prefs[stringPreferencesKey("llm_api_key")] ?: ""
            val legacyWsKey = prefs[stringPreferencesKey("websearch_api_key")] ?: ""
            val provider = prefs[SettingsKeys.PROVIDER] ?: ""
            val wsProvider = prefs[SettingsKeys.WEBSEARCH_PROVIDER] ?: ""
            if (legacyApiKey.isNotBlank() && provider.isNotBlank()) {
                keyCache.saveKey(provider, legacyApiKey)
            }
            if (legacyWsKey.isNotBlank() && wsProvider.isNotBlank()) {
                keyCache.saveKey("ws_$wsProvider", legacyWsKey)
            }
        }
        dataStore.edit {
            it.remove(stringPreferencesKey("llm_api_key"))
            it.remove(stringPreferencesKey("websearch_api_key"))
            it.remove(stringPreferencesKey("llm_api_keys"))
            it.remove(stringPreferencesKey("websearch_api_keys"))
        }
    }
}
