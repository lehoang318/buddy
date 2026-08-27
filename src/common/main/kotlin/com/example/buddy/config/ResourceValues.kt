package com.example.buddy.config

import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.net.JarURLConnection
import javax.xml.parsers.DocumentBuilderFactory

data class ResourceValues(
    val integers: Map<String, Int>,
    val booleans: Map<String, Boolean>,
    val dimensions: Map<String, Float>,
    val strings: Map<String, String>,
    val arrays: Map<String, List<String>>
) {
    fun integer(name: String): Int = integers[name] ?: error("Missing resource: integer '$name'")
    fun bool(name: String): Boolean = booleans[name] ?: error("Missing resource: bool '$name'")
    fun dimension(name: String): Float = dimensions[name] ?: error("Missing resource: dimen '$name'")
    fun string(name: String): String = strings[name] ?: error("Missing resource: string '$name'")
    fun array(name: String): List<String> = arrays[name] ?: error("Missing resource: string-array '$name'")
}

object ResourceValuesLoader {
    private class Accumulator {
        val integers = mutableMapOf<String, Int>()
        val booleans = mutableMapOf<String, Boolean>()
        val dimensions = mutableMapOf<String, Float>()
        val strings = mutableMapOf<String, String>()
        val arrays = mutableMapOf<String, List<String>>()
        fun toValues() = ResourceValues(integers, booleans, dimensions, strings, arrays)
    }

    fun loadFromDirectory(directory: File): ResourceValues {
        val files = directory.listFiles { file -> file.extension == "xml" }?.sortedBy { it.name }
            ?: error("Resource directory not found: ${directory.absolutePath}")
        val factory = newFactory()
        val accumulator = Accumulator()
        files.forEach { file -> file.inputStream().use { loadInto(factory, it, accumulator) } }
        return accumulator.toValues()
    }

    fun loadFromClasspath(prefix: String = "values"): ResourceValues {
        val classLoader = ResourceValuesLoader::class.java.classLoader
            ?: error("No class loader available to load resource bundle '$prefix'")
        val factory = newFactory()
        val accumulator = Accumulator()
        var found = false
        val urls = classLoader.getResources(prefix)
        while (urls.hasMoreElements()) {
            val url = urls.nextElement()
            when (url.protocol) {
                "file" -> {
                    val directory = runCatching { File(url.toURI()) }.getOrNull() ?: continue
                    val files = directory.listFiles { file -> file.extension == "xml" }?.sortedBy { it.name }
                        ?: continue
                    found = true
                    files.forEach { file -> file.inputStream().use { loadInto(factory, it, accumulator) } }
                }
                "jar" -> {
                    val connection = try {
                        url.openConnection() as JarURLConnection
                    } catch (_: Exception) {
                        continue
                    }
                    val jar = connection.jarFile
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.name.startsWith("$prefix/") || !entry.name.endsWith(".xml")) continue
                        found = true
                        jar.getInputStream(entry).use { loadInto(factory, it, accumulator) }
                    }
                }
            }
        }
        if (!found) {
            error("No resource files found on the classpath under '$prefix/'. Run the 'copyAndroidValues' task (wired into processResources).")
        }
        return accumulator.toValues()
    }

    private fun newFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

    private fun loadInto(factory: DocumentBuilderFactory, stream: InputStream, accumulator: Accumulator) {
        val document = factory.newDocumentBuilder().parse(stream)
        val resources = document.documentElement
        for (index in 0 until resources.childNodes.length) {
            val node = resources.childNodes.item(index)
            if (node !is Element) continue
            when (node.tagName) {
                "integer" -> accumulator.integers[node.getAttribute("name")] = node.textContent.trim().toInt()
                "bool" -> accumulator.booleans[node.getAttribute("name")] = node.textContent.trim().toBooleanStrict()
                "string" -> accumulator.strings[node.getAttribute("name")] = unescape(node.textContent)
                "item" -> if (node.getAttribute("type") == "dimen") {
                    accumulator.dimensions[node.getAttribute("name")] = node.textContent.trim().toFloat()
                }
                "string-array" -> {
                    accumulator.arrays[node.getAttribute("name")] = (0 until node.childNodes.length)
                        .map { node.childNodes.item(it) }
                        .filterIsInstance<Element>()
                        .filter { it.tagName == "item" }
                        .map { unescape(it.textContent) }
                }
            }
        }
    }

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '\\' || index == value.lastIndex) {
                result.append(value[index])
                index++
                continue
            }
            when (val escaped = value[index + 1]) {
                '\\' -> result.append('\\')
                '\'' -> result.append('\'')
                '"' -> result.append('"')
                'n' -> result.append('\n')
                't' -> result.append('\t')
                else -> result.append('\\').append(escaped)
            }
            index += 2
        }
        return result.toString()
    }
}