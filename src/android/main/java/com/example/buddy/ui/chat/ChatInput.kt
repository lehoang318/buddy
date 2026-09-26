package com.example.buddy.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.buddy.llm.ReasoningEffort
import com.example.buddy.ui.theme.OnSurfaceVariant
import com.example.buddy.ui.theme.Outline
import com.example.buddy.ui.theme.SecondaryIcons
import com.example.buddy.ui.theme.SendButton
import com.example.buddy.ui.theme.SurfaceVariant
import com.example.buddy.ui.theme.TextColor
import com.example.buddy.ui.theme.VintageBackground
import kotlinx.coroutines.delay

private const val REASONING_LOCK_MS = 1_000L

@Composable
fun InputBar(
    text: String,
    pendingImage: String?,
    pendingFile: Uri?,
    pendingFileName: String?,
    attachmentError: String?,
    isOffline: Boolean,
    isProcessing: Boolean,
    isCancelling: Boolean,
    answeringQuestion: Boolean,
    reasoningEffort: ReasoningEffort?,
    onToggleReasoning: () -> Unit,
    webSearchEnabled: Boolean,
    webSearchAvailable: Boolean,
    onToggleWebSearch: () -> Unit,
    onTextChange: (String) -> Unit,
    onClearImage: () -> Unit,
    onClearFile: () -> Unit,
    onPickAttachment: () -> Unit,
    onTakePhoto: () -> Unit,
    showPairNavigation: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoFirst: () -> Unit,
    onGoLast: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val canSend = text.isNotBlank()
    val imageOnly = pendingImage != null && text.isBlank()

    val context = LocalContext.current
    var lastReasoning by remember { mutableStateOf(reasoningEffort) }
    var reasoningLocked by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    LaunchedEffect(reasoningEffort) {
        if (reasoningEffort == ReasoningEffort.DEEP && reasoningEffort != lastReasoning) {
            val toast = Toast.makeText(context, "Reasoning: deep research", Toast.LENGTH_SHORT)
            toast.show()
            reasoningLocked = true
            delay(REASONING_LOCK_MS)
            toast.cancel()
            reasoningLocked = false
        }
        lastReasoning = reasoningEffort
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VintageBackground)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        pendingImage?.let { base64 ->
            val bitmap = remember(base64) { decodeBase64ToBitmap(base64) }
            if (bitmap != null) {
                Box(modifier = Modifier.padding(bottom = 6.dp)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
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

        attachmentError?.let { error ->
            Text(error, color = SendButton, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
        }

        if (imageOnly && attachmentError == null) {
            Text(
                "Add a message to send with the image",
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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
                placeholder = {
                    Text(
                        when {
                            answeringQuestion -> "Type your answer..."
                            isOffline -> "Offline mode"
                            else -> "Message Buddy..."
                        },
                        color = OnSurfaceVariant
                    )
                },
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            onSend()
                            true
                        } else false
                    }
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { showAddMenu = true },
                        enabled = !isOffline,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            tint = if (isOffline) OnSurfaceVariant else SecondaryIcons
                        )
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        offset = DpOffset(0.dp, 36.dp)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AttachFile,
                                    null,
                                    tint = if (isOffline) OnSurfaceVariant else SecondaryIcons
                                )
                            },
                            text = {
                                Text(
                                    "Attach file",
                                    color = if (isOffline) OnSurfaceVariant else TextColor
                                )
                            },
                            enabled = !isOffline,
                            onClick = {
                                showAddMenu = false
                                onPickAttachment()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    null,
                                    tint = if (isOffline) OnSurfaceVariant else SecondaryIcons
                                )
                            },
                            text = {
                                Text(
                                    "Take photo",
                                    color = if (isOffline) OnSurfaceVariant else TextColor
                                )
                            },
                            enabled = !isOffline,
                            onClick = {
                                showAddMenu = false
                                onTakePhoto()
                            }
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { onToggleWebSearch() },
                        enabled = webSearchAvailable && !isOffline,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Web search",
                            tint = if (isOffline || !webSearchAvailable) OnSurfaceVariant else if (webSearchEnabled) SendButton else SecondaryIcons
                        )
                    }
                }
            }

            if (showPairNavigation) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .combinedClickable(
                                enabled = canGoBack,
                                role = Role.Button,
                                onClick = onGoBack,
                                onLongClick = onGoFirst
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous",
                            tint = if (canGoBack) TextColor else SecondaryIcons
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .combinedClickable(
                                enabled = canGoForward,
                                role = Role.Button,
                                onClick = onGoForward,
                                onLongClick = onGoLast
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next",
                            tint = if (canGoForward) TextColor else SecondaryIcons
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (reasoningEffort == ReasoningEffort.DEEP) {
                        IconButton(
                            onClick = onToggleReasoning,
                            enabled = !isOffline && !reasoningLocked,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.AllInclusive,
                                contentDescription = "Reasoning: deep research",
                                tint = if (isOffline) OnSurfaceVariant else SendButton
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isOffline && !reasoningLocked, onClick = onToggleReasoning)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (reasoningEffort == ReasoningEffort.HIGH) "High" else "Low",
                                color = when {
                                    isOffline -> OnSurfaceVariant
                                    reasoningEffort == ReasoningEffort.HIGH -> SendButton
                                    else -> SecondaryIcons
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        when {
                            isCancelling -> Unit
                            answeringQuestion -> if (canSend) onSend() else onCancel()
                            isProcessing -> onCancel()
                            else -> onSend()
                        }
                    },
                    enabled = !isOffline && (isProcessing || canSend) && !isCancelling,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            when {
                                isCancelling -> Outline
                                answeringQuestion && canSend -> SendButton
                                isProcessing -> SendButton
                                canSend && !isOffline -> SendButton
                                else -> Outline
                            },
                            CircleShape
                        )
                ) {
                    when {
                        isCancelling -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TextColor
                        )
                        answeringQuestion && canSend -> Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        isProcessing -> Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        else -> Icon(
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
