package com.example.buddy.crypto

interface KeyProvider {
    fun getKey(providerId: String): ByteArray?
}
