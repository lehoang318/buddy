package com.example.buddy.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.buddy.chat.codeFileName
import com.example.buddy.chat.extensionForLang
import com.example.buddy.data.EventLog
import com.example.buddy.ui.theme.OnSurfaceVariant
import com.example.buddy.ui.theme.SecondaryIcons
import com.example.buddy.ui.theme.SendButton
import com.example.buddy.ui.theme.SurfaceVariant
import com.example.buddy.ui.theme.UserBubble
import com.example.buddy.ui.theme.VintageBackground
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_TAG = "Preview"
private const val SAVE_TAG = "Save"

private val HTML_LANGS = setOf("html", "htm")
private val MARKDOWN_LANGS = setOf("markdown", "md")
private val PREVIEWABLE_LANGS = HTML_LANGS + MARKDOWN_LANGS
private const val COLLAPSE_LINE_THRESHOLD = 12
private const val COLLAPSED_PREVIEW_LINES = 5

@Composable
fun CodeSegmentBubble(
    lang: String,
    code: String,
    streaming: Boolean,
    maxWidth: Dp,
    glowAlpha: Float
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var userCollapsed by remember { mutableStateOf<Boolean?>(null) }
    val lineCount = code.lines().size
    val collapsed = userCollapsed ?: (!streaming && lineCount > COLLAPSE_LINE_THRESHOLD)
    val shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

    Surface(
        color = SurfaceVariant,
        shape = shape,
        modifier = Modifier
            .widthIn(max = maxWidth)
            .then(if (streaming) Modifier.border(2.dp, SendButton.copy(alpha = glowAlpha), shape) else Modifier)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lang.isNotEmpty()) {
                    Text(lang, color = OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                if (collapsed) {
                    Text(
                        text = "$lineCount lines",
                        color = OnSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = if (lang.isNotEmpty()) 6.dp else 0.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!streaming) {
                    IconButton(
                        onClick = { userCollapsed = !collapsed },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (collapsed) "Expand code" else "Collapse code",
                            tint = SecondaryIcons,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (lang in PREVIEWABLE_LANGS && !streaming) {
                    val isHtml = lang in HTML_LANGS
                    IconButton(
                        onClick = {
                            if (isHtml) {
                                scope.launch { openHtmlInBrowser(context, code) }
                            } else {
                                showPreview = true
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Preview",
                            tint = SendButton,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (!streaming) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val name = saveCodeToDownloads(context, lang, code)
                                if (name != null) {
                                    saved = true
                                    Toast.makeText(context, "Saved to Downloads/$name", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (saved) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = "Save as file",
                            tint = if (saved) UserBubble else SecondaryIcons,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("copied code", code))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        copied = true
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) UserBubble else SecondaryIcons,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (collapsed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { userCollapsed = false }
                ) {
                    Surface(
                        color = VintageBackground,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = code.lines().take(COLLAPSED_PREVIEW_LINES).joinToString("\n"),
                            color = SendButton,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        VintageBackground
                                    )
                                )
                            )
                    )
                }
            } else {
                Surface(
                    color = VintageBackground,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = code,
                            color = SendButton,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPreview) {
        MarkdownPreviewDialog(markdown = code, onDismiss = { showPreview = false })
    }
}

private suspend fun openHtmlInBrowser(context: Context, html: String) {
    val file = withContext(Dispatchers.IO) {
        runCatching {
            File(context.cacheDir, "buddy_preview.html")
                .apply { writeText(html) }
        }.getOrNull()
    }
    if (file == null) {
        Toast.makeText(context, "Could not export HTML", Toast.LENGTH_SHORT).show()
        EventLog.error(PREVIEW_TAG, "HTML browser export failed", "${html.length} chars")
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "text/html")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onSuccess { EventLog.debug(PREVIEW_TAG, "HTML opened in browser", "${file.length()} bytes") }
        .onFailure {
            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
            EventLog.error(PREVIEW_TAG, "HTML browser launch failed", it.message.orEmpty())
        }
}

private suspend fun saveCodeToDownloads(context: Context, lang: String, code: String): String? {
    val name = codeFileName(lang, System.currentTimeMillis())
    val ext = extensionForLang(lang)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "text/plain"
    val resolver = context.contentResolver
    val failure = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("insert returned null")
            resolver.openOutputStream(uri)?.use { it.write(code.toByteArray(Charsets.UTF_8)) }
                ?: error("openOutputStream returned null")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            name
        }.exceptionOrNull()
    }
    if (failure != null) {
        Toast.makeText(context, "Could not save file", Toast.LENGTH_SHORT).show()
        EventLog.error(SAVE_TAG, "Code block save failed", failure.message.orEmpty())
    } else {
        EventLog.debug(SAVE_TAG, "Code block saved", "$name (${code.length} chars)")
    }
    return if (failure == null) name else null
}
