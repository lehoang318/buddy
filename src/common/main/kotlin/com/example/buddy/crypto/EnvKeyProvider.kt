package com.example.buddy.crypto

class EnvKeyProvider(
    private val environment: (String) -> String? = System::getenv
) : KeyProvider {
    override fun getKey(providerId: String): ByteArray? {
        val variable = envVarName(providerId) ?: return null
        return environment(variable)?.trim()?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun envVarName(providerId: String): String? = when (providerId) {
            "exa", "ws_exa" -> "EXA_API_KEY"
            "linkup", "ws_linkup" -> "LINKUP_API_KEY"
            "tavily", "ws_tavily" -> "TAVILY_API_KEY"
            "fireworks" -> "FIREWORKS_AI_API_KEY"
            "ollama" -> "OLLAMA_CLOUD_API_KEY"
            "openrouter" -> "OPEN_ROUTER_API_KEY"
            "siliconflow" -> "SILICON_FLOW_API_KEY"
            "together" -> "TOGETHER_AI_API_KEY"
            else -> null
        }
    }
}
