package com.example.buddy.chat

data class TextAttachment(
    val name: String,
    val text: String
)

const val MAX_TEXT_ATTACHMENT_SIZE_BYTES = 100 * 1024

val SUPPORTED_TEXT_EXTENSIONS = listOf(
    ".txt", ".md", ".log", ".rst", ".adoc", ".asciidoc", ".rtf", ".json", ".xml", ".html", ".py", ".js"
)

object TextAttachmentRules {
    fun extensionOf(fileName: String): String = ".${fileName.substringAfterLast('.', "").lowercase()}"

    fun isSupported(fileName: String): Boolean = extensionOf(fileName) in SUPPORTED_TEXT_EXTENSIONS

    fun validate(fileName: String, size: Long): String? {
        if (!isSupported(fileName)) return "Unsupported file type: ${extensionOf(fileName)}"
        if (size > MAX_TEXT_ATTACHMENT_SIZE_BYTES) {
            return "File too large (${formatFileSize(size)}, max 100KB)"
        }
        return null
    }

    fun fromBytes(fileName: String, bytes: ByteArray): Result<TextAttachment> {
        validate(fileName, bytes.size.toLong())?.let { return Result.failure(IllegalArgumentException(it)) }
        return Result.success(TextAttachment(fileName, String(bytes, Charsets.UTF_8)))
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
}
