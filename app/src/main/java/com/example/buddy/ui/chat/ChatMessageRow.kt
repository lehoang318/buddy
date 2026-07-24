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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.buddy.R
import com.example.buddy.data.ChatMessage
import com.example.buddy.data.Role
import com.example.buddy.ui.theme.Dimens
import com.example.buddy.ui.theme.OnSurfaceVariant
import com.example.buddy.ui.theme.Outline
import com.example.buddy.ui.theme.SecondaryIcons
import com.example.buddy.ui.theme.SendButton
import com.example.buddy.ui.theme.SurfaceVariant
import com.example.buddy.ui.theme.TextColor
import com.example.buddy.ui.theme.UserBubble
import com.example.buddy.ui.theme.VintageBackground

@Composable
fun MessageRow(message: ChatMessage) {
    val isUser = message.role == Role.USER
    val windowInfo = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    val maxBubbleWidth = screenWidth * 0.8f
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var copied by remember { mutableStateOf(false) }
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
            if (message.webSearchUsed) WebSearchPill(message.webSearchQuery)
            if (message.webSearchSkipped) WebSearchSkippedPill()

            message.imageBase64?.let { base64 ->
                val bitmap = decodeBase64ToBitmap(base64)
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

            if (message.content.isNotEmpty()) {
                val bubbleBg = if (isUser) UserBubble else SurfaceVariant
                val textColor = if (isUser) Color.White else TextColor
                val shape = if (isUser)
                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                else
                    RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

                val infiniteTransition = rememberInfiniteTransition(label = "glow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                    label = "glow_alpha"
                )
                val glowBorderWidth = if (!isUser && message.isStreaming) 2.dp else 0.dp
                val glowBorderColor = if (!isUser && message.isStreaming) SendButton.copy(alpha = glowAlpha) else Color.Transparent

                Surface(
                    color = bubbleBg,
                    shape = shape,
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .then(if (glowBorderWidth > 0.dp) Modifier.border(glowBorderWidth, glowBorderColor, shape) else Modifier)
                ) {
                    if (message.content.startsWith("```")) {
                        CodeBubble(message.content)
                    } else if (!isUser && message.isComplete) {
                        MarkdownRenderer(
                            markdown = message.content,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                        )
                    } else if (!isUser && message.isStreaming) {
                        RawTextRenderer(
                            text = message.content,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                            )
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
fun CodeBubble(raw: String) {
    val lines = raw.trimIndent().lines()
    val lang = lines.firstOrNull()?.removePrefix("```") ?: ""
    val code = lines.drop(1).dropLastWhile { it.trim() == "```" }.joinToString("\n")
    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .padding(10.dp)
    ) {
        if (lang.isNotEmpty()) {
            Text(lang, color = OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
        }
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
