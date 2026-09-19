package com.example.buddy.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.buddy.R
import com.example.buddy.agent.ASK_USER_SKIP_ANSWER
import com.example.buddy.chat.MessageSegment
import com.example.buddy.chat.splitIntoSegments
import com.example.buddy.data.ChatMessage
import com.example.buddy.data.Role
import com.example.buddy.ui.theme.Dimens
import com.example.buddy.ui.theme.OnSurfaceVariant
import com.example.buddy.ui.theme.Outline
import com.example.buddy.ui.theme.SecondaryIcons
import com.example.buddy.ui.theme.SendButton
import com.example.buddy.ui.theme.SurfaceVariant
import com.example.buddy.ui.theme.UserBubble
import com.example.buddy.ui.theme.VintageBackground

private const val THOUGHT_PREVIEW_LINES = 3

@Composable
fun MessageRow(
    message: ChatMessage,
    pendingOptions: List<String> = emptyList(),
    onAnswerOption: (String) -> Unit = {},
    onSkipAnswer: () -> Unit = {}
) {
    val isUser = message.role == Role.USER
    val windowInfo = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    val maxBubbleWidth = screenWidth * 0.8f
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var copied by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val hasQa = !message.questionAsked.isNullOrBlank() || !message.questionAnswer.isNullOrBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            AvatarCircle()
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!hasQa && message.webSearchUsed) WebSearchPills(message.webSearchQueries)
            if (!hasQa && message.webSearchSkipped) WebSearchSkippedPill()

            message.imageBase64?.let { base64 ->
                val bitmap = remember(base64) { decodeBase64ToBitmap(base64) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Outline, RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            message.attachedFileName?.let { fileName ->
                FileChip(fileName = fileName)
                Spacer(Modifier.height(4.dp))
            }

            if (!isUser && message.agentThoughts.isNotBlank()) {
                ThoughtsCard(
                    text = message.agentThoughts,
                    maxWidth = maxBubbleWidth,
                    streaming = message.thoughtsStreaming,
                    glowAlpha = glowAlpha
                )
                Spacer(Modifier.height(4.dp))
            }

            if (isUser) {
                if (message.content.isNotEmpty()) {
                    val shape = RoundedCornerShape(8.dp, 8.dp, 0.dp, 8.dp)
                    Surface(
                        color = UserBubble,
                        shape = shape,
                        modifier = Modifier.width(maxBubbleWidth)
                    ) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            } else {
                val question = message.questionAsked?.takeIf { it.isNotBlank() }
                val answer = message.questionAnswer?.takeIf { it.isNotBlank() }
                val segments = remember(message.content) { splitIntoSegments(message.content) }
                if (question != null || answer != null || segments.isNotEmpty()) {
                    Column {
                        if (question != null) {
                            TextSegmentBubble(
                                text = question,
                                markdown = false,
                                streaming = false,
                                maxWidth = maxBubbleWidth,
                                glowAlpha = glowAlpha,
                                shape = segmentShape(0, 1)
                            )
                            if (pendingOptions.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                QuestionOptions(
                                    options = pendingOptions,
                                    maxWidth = maxBubbleWidth,
                                    onAnswerOption = onAnswerOption,
                                    onSkipAnswer = onSkipAnswer
                                )
                            }
                        }
                        if (answer != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                AnswerBubble(text = answer, maxWidth = maxBubbleWidth)
                            }
                        }
                        if (hasQa && (message.webSearchUsed || message.webSearchSkipped)) {
                            Spacer(Modifier.height(6.dp))
                            if (message.webSearchUsed) WebSearchPills(message.webSearchQueries)
                            if (message.webSearchSkipped) WebSearchSkippedPill()
                        }
                        if (segments.isNotEmpty()) {
                            if ((question != null || answer != null) &&
                                !message.webSearchUsed && !message.webSearchSkipped
                            ) {
                                Spacer(Modifier.height(6.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                segments.forEachIndexed { index, segment ->
                                    val isLast = index == segments.lastIndex
                                    val streaming = message.isStreaming && isLast
                                    val shape = segmentShape(index, segments.size)
                                    when (segment) {
                                        is MessageSegment.Text -> TextSegmentBubble(
                                            text = segment.content,
                                            markdown = !streaming,
                                            streaming = streaming,
                                            maxWidth = maxBubbleWidth,
                                            glowAlpha = glowAlpha,
                                            shape = shape
                                        )
                                        is MessageSegment.Code -> CodeSegmentBubble(
                                            lang = segment.lang,
                                            code = segment.code,
                                            streaming = streaming,
                                            maxWidth = maxBubbleWidth,
                                            glowAlpha = glowAlpha,
                                            shape = shape
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (message.isStreaming) {
                BlinkingCursor()

                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(3) { i ->
                            AnimatedDot(delayMs = i * 200)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatTime(message.timestamp),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (message.content.isNotBlank()) {
                    IconButton(
                        onClick = {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("copied text", message.content)
                            )
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            copied = true
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy to clipboard",
                            tint = if (copied) UserBubble else SecondaryIcons,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        if (!isUser) Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun AnswerBubble(text: String, maxWidth: Dp) {
    val display = if (text == ASK_USER_SKIP_ANSWER) "(skipped)" else text
    Surface(
        color = UserBubble,
        shape = RoundedCornerShape(8.dp, 8.dp, 0.dp, 8.dp),
        modifier = Modifier.width(maxWidth)
    ) {
        SelectionContainer {
            Text(
                text = display,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun QuestionOptions(
    options: List<String>,
    maxWidth: Dp,
    onAnswerOption: (String) -> Unit,
    onSkipAnswer: () -> Unit
) {
    Column(
        modifier = Modifier.width(maxWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            OptionPill(label = option, onClick = { onAnswerOption(option) })
        }
        OptionPill(label = "Skip", onClick = onSkipAnswer)
    }
}

@Composable
private fun OptionPill(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceVariant,
        border = BorderStroke(1.dp, Outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = SendButton,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ThoughtsCard(text: String, maxWidth: Dp, streaming: Boolean, glowAlpha: Float) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var copied by remember { mutableStateOf(false) }
    var userCollapsed by remember { mutableStateOf<Boolean?>(null) }
    val collapsed = userCollapsed ?: true
    val wordCount = remember(text) { text.trim().split(Regex("\\s+")).count { it.isNotEmpty() } }
    val shape = RoundedCornerShape(8.dp)

    Surface(
        color = SurfaceVariant,
        shape = shape,
        modifier = Modifier
            .width(maxWidth)
            .then(if (streaming) Modifier.border(2.dp, SendButton.copy(alpha = glowAlpha), shape) else Modifier)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Thoughts",
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                if (collapsed) {
                    Text(
                        text = "$wordCount words",
                        color = OnSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { userCollapsed = !collapsed },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (collapsed) "Expand thoughts" else "Collapse thoughts",
                        tint = SecondaryIcons,
                        modifier = Modifier.size(14.dp)
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("copied thoughts", text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        copied = true
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy thoughts",
                        tint = if (copied) UserBubble else SecondaryIcons,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (collapsed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { userCollapsed = false }
                ) {
                    Surface(
                        color = VintageBackground,
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            text = text.lines().take(THOUGHT_PREVIEW_LINES).joinToString("\n"),
                            color = OnSurfaceVariant,
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
                    shape = RoundedCornerShape(2.dp)
                ) {
                    val bodyModifier = Modifier.padding(10.dp)
                    if (streaming) {
                        RawTextRenderer(text = text, modifier = bodyModifier, textColor = OnSurfaceVariant)
                    } else {
                        MarkdownRenderer(markdown = text, modifier = bodyModifier, textColor = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun segmentShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(8.dp, 8.dp, 8.dp, 0.dp)
    index == 0 -> RoundedCornerShape(8.dp, 8.dp, 0.dp, 0.dp)
    index == count - 1 -> RoundedCornerShape(0.dp, 0.dp, 8.dp, 0.dp)
    else -> RectangleShape
}

@Composable
private fun TextSegmentBubble(
    text: String,
    markdown: Boolean,
    streaming: Boolean,
    maxWidth: Dp,
    glowAlpha: Float,
    shape: Shape
) {
    Surface(
        color = SurfaceVariant,
        shape = shape,
        modifier = Modifier
            .width(maxWidth)
            .then(if (streaming) Modifier.border(2.dp, SendButton.copy(alpha = glowAlpha), shape) else Modifier)
    ) {
        val innerModifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
        if (markdown) {
            MarkdownRenderer(markdown = text, modifier = innerModifier)
        } else {
            RawTextRenderer(text = text, modifier = innerModifier)
        }
    }
}

@Composable
fun AvatarCircle() {
    Box(
        modifier = Modifier
            .size(Dimens.BuddyAvatarSize)
            .clip(CircleShape)
            .background(SendButton),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.avatar),
            contentDescription = "Buddy Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun decodeBase64ToBitmap(base64: String): android.graphics.Bitmap? {
    return try {
        val base64Data = if (base64.contains(",")) base64.substringAfter(",") else base64
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
