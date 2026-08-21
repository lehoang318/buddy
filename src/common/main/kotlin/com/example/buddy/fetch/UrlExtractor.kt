package com.example.buddy.fetch

fun extractUrls(text: String): List<String> = Regex("""https://[^\s<>"{}|\\^`\[\]]+""")
    .findAll(text)
    .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'') }
    .toList()
