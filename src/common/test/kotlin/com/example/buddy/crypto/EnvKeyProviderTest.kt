package com.example.buddy.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvKeyProviderTest {
    @Test
    fun `maps provider ids to requested environment variables`() {
        val provider = EnvKeyProvider { name -> if (name == "TOGETHER_AI_API_KEY") " secret " else null }

        assertArrayEquals("secret".toByteArray(), provider.getKey("together"))
        assertEquals("EXA_API_KEY", EnvKeyProvider.envVarName("ws_exa"))
        assertEquals("OPEN_ROUTER_API_KEY", EnvKeyProvider.envVarName("openrouter"))
        assertNull(provider.getKey("unknown"))
    }
}
