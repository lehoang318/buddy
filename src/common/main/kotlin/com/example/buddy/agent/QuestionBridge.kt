package com.example.buddy.agent

import kotlinx.coroutines.channels.Channel

/**
 * Single-slot handoff between a suspended [AskUserTool] and whoever can supply the user's answer.
 * The tool suspends on [receive] until an answer arrives through [send]. One buffered slot lets an
 * answer that arrives just before the tool starts waiting still be accepted; a second answer while
 * the slot is full is ignored.
 */
class QuestionBridge {
    private val answers = Channel<String>(capacity = 1)

    suspend fun receive(): String = answers.receive()

    fun send(text: String): Boolean = answers.trySend(text).isSuccess
}
