package com.example.buddy.llm

enum class ReasoningEffort { LOW, HIGH, DEEP }

fun ReasoningEffort?.cycle(deepAvailable: Boolean): ReasoningEffort = when (this) {
    ReasoningEffort.LOW -> ReasoningEffort.HIGH
    ReasoningEffort.HIGH -> if (deepAvailable) ReasoningEffort.DEEP else ReasoningEffort.LOW
    ReasoningEffort.DEEP -> ReasoningEffort.LOW
    null -> ReasoningEffort.HIGH
}

fun ReasoningEffort?.label(): String = when (this) {
    ReasoningEffort.LOW -> "low"
    ReasoningEffort.HIGH -> "high"
    ReasoningEffort.DEEP -> "deep research"
    null -> "default"
}
