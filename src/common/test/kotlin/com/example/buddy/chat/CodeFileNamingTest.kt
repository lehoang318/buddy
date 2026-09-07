package com.example.buddy.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFileNamingTest {

    @Test
    fun `known language maps to its extension`() {
        assertEquals("kt", extensionForLang("kotlin"))
        assertEquals("py", extensionForLang("python"))
        assertEquals("md", extensionForLang("markdown"))
        assertEquals("sh", extensionForLang("bash"))
        assertEquals("html", extensionForLang("html"))
    }

    @Test
    fun `aliases map to the canonical extension`() {
        assertEquals("kt", extensionForLang("kt"))
        assertEquals("kts", extensionForLang("kts"))
        assertEquals("py", extensionForLang("py"))
        assertEquals("js", extensionForLang("javascript"))
        assertEquals("js", extensionForLang("js"))
        assertEquals("jsx", extensionForLang("jsx"))
        assertEquals("ts", extensionForLang("typescript"))
        assertEquals("ts", extensionForLang("ts"))
        assertEquals("html", extensionForLang("htm"))
        assertEquals("css", extensionForLang("scss"))
        assertEquals("md", extensionForLang("md"))
        assertEquals("sh", extensionForLang("sh"))
        assertEquals("sh", extensionForLang("shell"))
        assertEquals("sh", extensionForLang("zsh"))
        assertEquals("yaml", extensionForLang("yml"))
        assertEquals("ini", extensionForLang("conf"))
        assertEquals("cpp", extensionForLang("c++"))
        assertEquals("cpp", extensionForLang("cxx"))
        assertEquals("cs", extensionForLang("csharp"))
        assertEquals("rs", extensionForLang("rust"))
        assertEquals("rb", extensionForLang("ruby"))
        assertEquals("pl", extensionForLang("perl"))
    }

    @Test
    fun `unknown and empty language fall back to txt`() {
        assertEquals("txt", extensionForLang("?"))
        assertEquals("txt", extensionForLang(""))
        assertEquals("txt", extensionForLang("   "))
        assertEquals("txt", extensionForLang("foobarbaz"))
    }

    @Test
    fun `language is matched case-insensitively and trimmed`() {
        assertEquals("kt", extensionForLang("Kotlin"))
        assertEquals("py", extensionForLang("  Python "))
        assertEquals("sql", extensionForLang("SQL"))
    }

    @Test
    fun `file name embeds timestamp and extension`() {
        val stamp = 1200000000000L
        val name = codeFileName("kotlin", stamp)
        assertEquals("buddy_code_20080110_212000.kt", name)
        assertEquals("txt", codeFileName("unknown", stamp).substringAfterLast('.'))
    }
}