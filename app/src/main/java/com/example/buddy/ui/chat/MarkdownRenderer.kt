package com.example.buddy.ui.chat

import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlin.text.replace
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.graphics.toColorInt
import com.example.buddy.ui.theme.TextColor as VintageTextColor


@Composable
fun MarkdownRenderer(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: ComposeColor = VintageTextColor,
) {
    val context = LocalContext.current
    val textColorArgb = textColor.toArgb()

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .build()
    }

    val normalised = remember(markdown) { markdown.replace("\r\n", "\n").replace("\r", "\n") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColorArgb)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.2f)
                movementMethod = LinkMovementMethod.getInstance()
//                highlightColor = Color.TRANSPARENT
                setLinkTextColor("#58A6FF".toColorInt())
                setBackgroundColor(Color.TRANSPARENT)
                TextViewCompat.setCustomSelectionActionModeCallback(this, object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = true
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
                    override fun onDestroyActionMode(mode: ActionMode) {}
                })
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColorArgb)
            markwon.setMarkdown(textView, normalised)
        }
    )
}

@Composable
fun RawTextRenderer(
    text: String,
    modifier: Modifier = Modifier,
    textColor: ComposeColor = VintageTextColor,
) {
    val textColorArgb = textColor.toArgb()
    val normalised = remember(text) {
        text.replace("\r\n", "\n").replace("\r", "\n")
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColorArgb)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.2f)
                setBackgroundColor(Color.TRANSPARENT)
                TextViewCompat.setCustomSelectionActionModeCallback(this, object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = true
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
                    override fun onDestroyActionMode(mode: ActionMode) {}
                })
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColorArgb)
            textView.text = normalised
        }
    )
}