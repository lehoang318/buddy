package com.example.buddy.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.buddy.data.ChatMessage
import com.example.buddy.data.Role
import com.example.buddy.ui.theme.*
import androidx.compose.ui.res.painterResource
import com.example.buddy.R

@Composable
fun MessageRow(message: ChatMessage) {
    val isUser = message.role == Role.USER
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
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
            if (message.webSearchUsed) WebSearchPill()

            message.imageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Outline, RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(4.dp))
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
fun FileChip(fileName: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceVariant,
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.Description, null, tint = SendButton, modifier = Modifier.size(11.dp))
            Text(fileName, color = SendButton, style = MaterialTheme.typography.labelSmall)
        }
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

@Composable
fun WebSearchPill() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceVariant,
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.Search, null, tint = SendButton, modifier = Modifier.size(11.dp))
            Text("Web search used", color = SendButton, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun WebSearchErrorPill(error: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceVariant,
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.Warning, null, tint = SendButton, modifier = Modifier.size(11.dp))
            Text("Web search failed: $error", color = SendButton, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursor_alpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 2.dp, top = 2.dp)
            .size(2.dp, 14.dp)
            .background(TextColor.copy(alpha = alpha))
    )
}

@Composable
fun TypingIndicator() {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AvatarCircle()
        Surface(color = SurfaceVariant, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    AnimatedDot(delayMs = i * 200)
                }
            }
        }
    }
}

@Composable
fun AnimatedDot(delayMs: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot$delayMs")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = delayMs),
            RepeatMode.Reverse
        ),
        label = "dot_alpha_$delayMs"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(OnSurfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun InputBar(
    text: String,
    pendingImage: Uri?,
    pendingFile: Uri?,
    pendingFileName: String?,
    fileTooLargeError: String?,
    isOffline: Boolean,
    urlFetchInProgress: Boolean,
    onTextChange: (String) -> Unit,
    onClearImage: () -> Unit,
    onClearFile: () -> Unit,
    onPickAttachment: () -> Unit,
    onTakePhoto: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = text.isNotBlank() || pendingImage != null || pendingFile != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VintageBackground)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        pendingImage?.let { uri ->
            Box(modifier = Modifier.padding(bottom = 6.dp)) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(Outline, CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }

        pendingFileName?.let { fileName ->
            Box(modifier = Modifier.padding(bottom = 6.dp)) {
                FileChip(fileName = fileName)
                IconButton(
                    onClick = onClearFile,
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(Outline, CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }

        fileTooLargeError?.let { error ->
            Text(error, color = SendButton, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceVariant,
            shape = RoundedCornerShape(22.dp)
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                enabled = !isOffline,
                placeholder = { Text(if (isOffline) "Offline mode" else "Message Buddy...", color = OnSurfaceVariant) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = TextColor,
                    unfocusedTextColor = if (isOffline) OnSurfaceVariant else TextColor,
                    disabledTextColor = OnSurfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = SendButton
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPickAttachment,
                    enabled = !isOffline,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        tint = if (isOffline) OnSurfaceVariant else SecondaryIcons
                    )
                }

                IconButton(
                    onClick = onTakePhoto,
                    enabled = !isOffline,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Take Photo",
                        tint = if (isOffline) OnSurfaceVariant else SecondaryIcons
                    )
                }
            }

            IconButton(
                onClick = onSend,
                enabled = canSend && !isOffline && !urlFetchInProgress,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (canSend && !isOffline && !urlFetchInProgress) SendButton else Outline,
                        CircleShape
                    )
            ) {
                if (urlFetchInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextColor
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend && !isOffline) Color.White else OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DayLabel(label: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(label, color = OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

fun formatTime(ts: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val m = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    return "$h:$m"
}

@Composable
fun UrlFetchWarningPill(warning: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceVariant,
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.Warning, null, tint = SendButton, modifier = Modifier.size(11.dp))
            Text(warning, color = SendButton, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
}