package com.example.buddy.chat

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val LANG_EXTENSIONS = mapOf(
    "kotlin" to "kt",
    "kt" to "kt",
    "kts" to "kts",
    "java" to "java",
    "python" to "py",
    "py" to "py",
    "javascript" to "js",
    "js" to "js",
    "jsx" to "jsx",
    "typescript" to "ts",
    "ts" to "ts",
    "tsx" to "tsx",
    "html" to "html",
    "htm" to "html",
    "css" to "css",
    "scss" to "css",
    "markdown" to "md",
    "md" to "md",
    "bash" to "sh",
    "sh" to "sh",
    "shell" to "sh",
    "zsh" to "sh",
    "json" to "json",
    "xml" to "xml",
    "yaml" to "yaml",
    "yml" to "yaml",
    "toml" to "toml",
    "ini" to "ini",
    "conf" to "ini",
    "sql" to "sql",
    "c" to "c",
    "cpp" to "cpp",
    "c++" to "cpp",
    "cxx" to "cpp",
    "csharp" to "cs",
    "cs" to "cs",
    "go" to "go",
    "rust" to "rs",
    "rs" to "rs",
    "php" to "php",
    "ruby" to "rb",
    "rb" to "rb",
    "swift" to "swift",
    "dart" to "dart",
    "lua" to "lua",
    "r" to "r",
    "perl" to "pl",
    "pl" to "pl",
    "csv" to "csv",
    "text" to "txt",
    "txt" to "txt"
)

private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

fun extensionForLang(lang: String): String =
    LANG_EXTENSIONS[lang.trim().lowercase()] ?: "txt"

fun codeFileName(lang: String, timestampMillis: Long): String {
    val ts = LocalDateTime.ofEpochSecond(timestampMillis / 1000, 0, java.time.ZoneOffset.UTC)
        .format(TIMESTAMP)
    return "buddy_code_$ts.${extensionForLang(lang)}"
}