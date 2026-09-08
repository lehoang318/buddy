package com.example.buddy.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSegmentsTest {

    @Test
    fun `plain prose returns single text segment`() {
        assertEquals(
            listOf(MessageSegment.Text("Hello world")),
            splitIntoSegments("  Hello world  \n")
        )
    }

    @Test
    fun `entire message is one code block`() {
        val input = "```kotlin\nval x = 1\n```"
        assertEquals(
            listOf(MessageSegment.Code(lang = "kotlin", code = "val x = 1")),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `prose code prose yields three segments`() {
        val input = "Here is the fix:\n```python\nprint(1)\n```\nThat's it."
        assertEquals(
            listOf(
                MessageSegment.Text("Here is the fix:"),
                MessageSegment.Code(lang = "python", code = "print(1)"),
                MessageSegment.Text("That's it.")
            ),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `adjacent code blocks are not joined by empty text`() {
        val input = "```a\n1\n```\n```b\n2\n```"
        assertEquals(
            listOf(
                MessageSegment.Code(lang = "a", code = "1"),
                MessageSegment.Code(lang = "b", code = "2")
            ),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `unclosed trailing fence is an open code segment`() {
        val input = "Some text\n```py\nprint(1)"
        assertEquals(
            listOf(
                MessageSegment.Text("Some text"),
                MessageSegment.Code(lang = "py", code = "print(1)")
            ),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `open code segment with only a language line`() {
        assertEquals(
            listOf(MessageSegment.Code(lang = "java", code = "")),
            splitIntoSegments("```java")
        )
    }

    @Test
    fun `blank and empty input return empty list`() {
        assertEquals(emptyList<MessageSegment>(), splitIntoSegments(""))
        assertEquals(emptyList<MessageSegment>(), splitIntoSegments("   \n \n"))
    }

    @Test
    fun `inline backticks do not open a fence`() {
        val input = "Use the `code` function now."
        assertEquals(
            listOf(MessageSegment.Text("Use the `code` function now.")),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `longer backtick close lines are accepted leniently`() {
        val input = "```\na\n````"
        assertEquals(
            listOf(MessageSegment.Code(lang = "", code = "a")),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `crlf is normalized and lang lowercased`() {
        val input = "Intro\r\n```SQL\r\nSELECT 1\r\n```\r\nOutro"
        assertEquals(
            listOf(
                MessageSegment.Text("Intro"),
                MessageSegment.Code(lang = "sql", code = "SELECT 1"),
                MessageSegment.Text("Outro")
            ),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `indented fences are detected leniently`() {
        val input = "Text\n  ```py\nx\n  ```"
        assertEquals(
            listOf(
                MessageSegment.Text("Text"),
                MessageSegment.Code(lang = "py", code = "x")
            ),
            splitIntoSegments(input)
        )
    }

    @Test
    fun `text between fences is trimmed`() {
        val input = "```\na\n```\n\n\nTrailing after\n"
        assertEquals(
            listOf(
                MessageSegment.Code(lang = "", code = "a"),
                MessageSegment.Text("Trailing after")
            ),
            splitIntoSegments(input)
        )
        assertTrue(splitIntoSegments(input).none { it is MessageSegment.Text && it.content.isBlank() })
    }
}