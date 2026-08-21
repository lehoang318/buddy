package com.example.buddy.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentsTest {
    @Test
    fun `supported text attachment is decoded offline`() {
        val result = TextAttachmentRules.fromBytes("notes.md", "hello".toByteArray())

        assertTrue(result.isSuccess)
        assertEquals("hello", result.getOrThrow().text)
    }

    @Test
    fun `unsupported and oversized attachments are rejected`() {
        assertNotNull(TextAttachmentRules.validate("image.png", 10))
        assertNotNull(TextAttachmentRules.validate("notes.txt", (MAX_TEXT_ATTACHMENT_SIZE_BYTES + 1).toLong()))
        assertNull(TextAttachmentRules.validate("notes.txt", MAX_TEXT_ATTACHMENT_SIZE_BYTES.toLong()))
    }
}
