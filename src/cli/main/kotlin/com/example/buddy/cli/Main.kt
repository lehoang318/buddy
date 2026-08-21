package com.example.buddy.cli

import com.example.buddy.chat.ConversationEngine
import com.example.buddy.chat.ConversationEvent
import com.example.buddy.chat.TextAttachment
import com.example.buddy.chat.TextAttachmentRules
import com.example.buddy.config.AppConfigProvider
import com.example.buddy.crypto.EnvKeyProvider
import com.example.buddy.data.LlmProvider
import com.example.buddy.data.WebSearchProvider
import com.example.buddy.fetch.JsoupUrlFetcher
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmClientFactory
import com.example.buddy.search.WebSearch
import com.example.buddy.search.WebSearchFactory
import com.example.buddy.logging.DesktopLogger
import com.example.buddy.logging.Log
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() = runBlocking {
    System.setProperty("org.jline.utils.log.level", "OFF")
    Log.logger = DesktopLogger("BuddyCLI")
    CliApplication().run()
}

private class CliApplication {
    private val keyProvider = EnvKeyProvider()
    private val engine = ConversationEngine(urlFetcher = JsoupUrlFetcher())
    private val providers = AppConfigProvider.current.providers
    private var pendingAttachment: TextAttachment? = null

    suspend fun run() {
        printBanner()
        TerminalInput().use { input ->
            if (!configureProviders(input)) return
            while (true) {
                when (val result = input.readLine("You> ")) {
                    TerminalReadResult.Terminate -> return
                    is TerminalReadResult.Line -> {
                        val line = result.text.trim()
                        if (line.isBlank()) continue
                        if (line.startsWith('/')) {
                            if (!handleCommand(line, input)) return
                        } else {
                            sendMessage(line)
                        }
                    }
                }
            }
        }
    }

    private suspend fun configureProviders(input: TerminalInput): Boolean {
        println("Select an LLM provider to continue.")
        while (engine.client == null) {
            if (!selectLlmProvider(input)) return false
            if (engine.client == null) {
                println("A usable LLM provider is required before chatting.")
            }
        }

        val configureWeb = readChoice(input, "Configure web search? (y/n): ", setOf("y", "n")) ?: return false
        if (configureWeb == "y") {
            if (!selectWebProvider(input)) return false
        } else {
            println("Web search disabled.")
        }
        return true
    }

    private suspend fun handleCommand(command: String, input: TerminalInput): Boolean {
        val parts = command.split(Regex("\\s+"), limit = 2)
        return when (parts[0].lowercase()) {
            "/help" -> {
                printHelp()
                true
            }
            "/provider" -> selectProvider(input)
            "/web" -> {
                if (engine.webSearch == null) {
                    println("Select a web provider first with /provider.")
                } else {
                    engine.webSearchEnabled = !engine.webSearchEnabled
                    println("Web search ${if (engine.webSearchEnabled) "enabled" else "disabled"}.")
                }
                true
            }
            "/attach" -> {
                if (parts.size < 2 || parts[1].isBlank()) {
                    println("Usage: /attach <file path>")
                } else {
                    attach(parts[1])
                }
                true
            }
            "/exit", "/quit" -> false
            else -> {
                println("Unknown command. Use /help.")
                true
            }
        }
    }

    private suspend fun selectProvider(input: TerminalInput): Boolean {
        val type = readChoice(input, "Provider type (llm/web): ", setOf("llm", "web")) ?: return false
        return if (type == "llm") selectLlmProvider(input) else selectWebProvider(input)
    }

    private suspend fun selectLlmProvider(input: TerminalInput): Boolean {
        val provider = chooseProvider(input, providers.llm, "LLM") ?: return false
        val envName = EnvKeyProvider.envVarName(provider.id)
        if (keyProvider.getKey(provider.id) == null) {
            println("Missing API key. Set $envName and try again.")
            return true
        }

        println("Fetching models from ${provider.name}...")
        val tempClient = LlmClientFactory.createTempForModels(provider, keyProvider).getOrElse {
            println("Could not create ${provider.name} client: ${it.message}")
            return true
        }
        val models = tempClient.getModels()
        val model = chooseModel(input, models.map { it.id }) ?: return false
        val client = LlmClientFactory.createWithProvider(provider, keyProvider, model).getOrElse {
            println("Could not create ${provider.name} client: ${it.message}")
            return true
        }
        updateEngine(client = client)
        println("LLM provider: ${provider.name}; model: $model")
        return true
    }

    private fun selectWebProvider(input: TerminalInput): Boolean {
        val provider = chooseProvider(input, providers.webSearch, "web") ?: return false
        val envName = EnvKeyProvider.envVarName("ws_${provider.id}")
        if (keyProvider.getKey("ws_${provider.id}") == null) {
            println("Missing API key. Set $envName and try again.")
            return true
        }
        val search = WebSearchFactory.create(keyProvider, provider.id)
        if (search == null) {
            println("Unsupported web provider: ${provider.id}")
            return true
        }
        updateEngine(webSearch = search)
        println("Web provider: ${provider.name}")
        return true
    }

    private fun updateEngine(client: LlmClient? = engine.client, webSearch: WebSearch? = engine.webSearch) {
        engine.updateDependencies(client, webSearch, JsoupUrlFetcher())
    }

    private fun <T> chooseProvider(input: TerminalInput, values: List<T>, kind: String): T? {
        println("Supported $kind providers:")
        values.forEachIndexed { index, provider ->
            val name = when (provider) {
                is LlmProvider -> provider.name
                is WebSearchProvider -> provider.name
                else -> provider.toString()
            }
            println("${index + 1}. $name")
        }
        while (true) {
            val selected = readText(input, "Select provider number: ") ?: return null
            val index = selected.toIntOrNull()?.minus(1)
            if (index != null && index in values.indices) return values[index]
            println("Invalid provider selection.")
        }
    }

    private suspend fun chooseModel(input: TerminalInput, models: List<String>): String? {
        if (models.isEmpty()) {
            println("The provider returned no models.")
            return readText(input, "Enter model ID: ")?.takeIf { it.isNotBlank() }
        }
        println("Available models:")
        models.forEachIndexed { index, model -> println("${index + 1}. $model") }
        val selected = readText(input, "Select model number or enter model ID: ") ?: return null
        val index = selected.toIntOrNull()?.minus(1)
        return index?.takeIf { it in models.indices }?.let { models[it] } ?: selected.takeIf { it.isNotBlank() }
    }

    private fun readChoice(input: TerminalInput, prompt: String, allowed: Set<String>): String? {
        while (true) {
            val value = readText(input, prompt)?.lowercase() ?: return null
            if (value in allowed) return value
            println("Choose one of: ${allowed.joinToString(", ")}")
        }
    }

    private fun readText(input: TerminalInput, prompt: String): String? = when (val result = input.readLine(prompt)) {
        TerminalReadResult.Terminate -> null
        is TerminalReadResult.Line -> result.text.trim()
    }

    private fun attach(path: String) {
        val file = File(path.removeSurrounding("\""))
        if (!file.isFile) {
            println("File not found: ${file.path}")
            return
        }
        TextAttachmentRules.validate(file.name, file.length())?.let {
            println(it)
            return
        }
        val attachment = runCatching { TextAttachmentRules.fromBytes(file.name, file.readBytes()).getOrThrow() }
            .getOrElse {
                println("Could not read file: ${it.message}")
                return
            }
        pendingAttachment = attachment
        println("Attached ${file.name} for the next query.")
    }

    private suspend fun sendMessage(text: String) {
        val attachment = pendingAttachment
        pendingAttachment = null
        var responseStarted = false
        engine.send(text, attachment = attachment).collect { event ->
            when (event) {
                ConversationEvent.UrlFetchStarted -> println("Fetching URLs...")
                is ConversationEvent.UrlFetchFinished -> event.result.warnings.forEach { println("Warning: $it") }
                is ConversationEvent.UserMessageAccepted -> Unit
                ConversationEvent.SearchStarted -> println("Searching the web...")
                is ConversationEvent.SearchFinished -> event.outcome.errorMessage?.let { println("Web search: $it") }
                is ConversationEvent.AssistantStarted -> {
                    responseStarted = true
                    print("Buddy> ")
                    System.out.flush()
                }
                is ConversationEvent.Token -> {
                    print(event.text)
                    System.out.flush()
                }
                is ConversationEvent.Completed -> println()
                is ConversationEvent.Failed -> {
                    if (responseStarted) println()
                    println("Error: ${event.error.message}")
                }
            }
        }
    }

    private fun printBanner() {
        println("Buddy desktop CLI")
        println("Type /help for available commands.")
        println("Ctrl+D or double Esc exits.")
    }

    private fun printHelp() {
        println("Available commands:")
        println("  /provider       Select LLM or web-search provider")
        println("  /web            Toggle web search on/off")
        println("  /attach <path>  Attach a text file for the next query")
        println("  /help           Show this help message")
        println("  /exit           Exit the application")
        println("  /quit           Exit the application")
    }
}
