package com.example.buddy.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class SessionKeyCache(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        PREF_FILE_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val lock = ReentrantReadWriteLock()

    private val _keyIds = MutableStateFlow<Set<String>>(loadKeyIds())
    val keyIds: StateFlow<Set<String>> = _keyIds.asStateFlow()

    private fun loadKeyIds(): Set<String> {
        return prefs.all.keys.filter { key ->
            !key.startsWith("_") && !key.startsWith("__")
        }.toSet()
    }

    fun getKey(providerId: String): ByteArray? {
        lock.read {
            cache[providerId]?.let { return it.copyOf() }
        }
        lock.write {
            cache[providerId]?.let { return it.copyOf() }
            val encrypted = prefs.getString(providerId, null) ?: return null
            val bytes = encrypted.toByteArray(Charsets.UTF_8)
            cache[providerId] = bytes
            return bytes.copyOf()
        }
    }

    fun saveKey(providerId: String, key: String) {
        prefs.edit().putString(providerId, key).apply()
        lock.write {
            cache[providerId]?.fill(0)
            cache.remove(providerId)
        }
        _keyIds.value = _keyIds.value + providerId
    }

    fun clearCache() {
        lock.write {
            cache.values.forEach { it.fill(0) }
            cache.clear()
        }
    }

    fun removeKey(providerId: String) {
        prefs.edit().remove(providerId).apply()
        lock.write {
            cache[providerId]?.fill(0)
            cache.remove(providerId)
        }
        _keyIds.value = _keyIds.value - providerId
    }

    fun hasKey(providerId: String): Boolean = providerId in _keyIds.value

    companion object {
        private const val PREF_FILE_NAME = "buddy_secure_keys"
    }
}
