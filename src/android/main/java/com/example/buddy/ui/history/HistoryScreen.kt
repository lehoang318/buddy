package com.example.buddy.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.buddy.data.SavedSession
import com.example.buddy.data.SessionRepository
import com.example.buddy.ui.theme.OnSurfaceVariant
import com.example.buddy.ui.theme.SurfaceVariant
import com.example.buddy.ui.theme.TextColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.ROOT)

private enum class SessionFilter(val millis: Long?) {
    ALL(null),
    SEVEN_DAYS(7L * DAY_MILLIS),
    THIRTY_DAYS(30L * DAY_MILLIS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onResume: (SavedSession) -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val repository = remember { SessionRepository(appContext) }
    val scaffoldScope = rememberCoroutineScope()
    val sessions by repository.sessions.collectAsState(initial = emptyList())
    val autoDeleteOld by repository.autoDeleteOld.collectAsState(initial = false)

    var filter by remember { mutableStateOf<SessionFilter>(SessionFilter.ALL) }
    var showPurgeConfirm by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateMapOf<String, Boolean>() }

    val now = System.currentTimeMillis()
    val filtered = sessions.filter { s ->
        filter.millis == null || s.createdAt >= now - filter.millis!!
    }
    val selectedCount = selectedIds.values.count { it }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Text("History", color = MaterialTheme.colorScheme.onSurface)
                },
                actions = {
                    if (filtered.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = filtered.isNotEmpty() && filtered.all { selectedIds[it.id] == true },
                                onCheckedChange = { checked ->
                                    if (checked) filtered.forEach { selectedIds[it.id] = true }
                                    else selectedIds.clear()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = filter == SessionFilter.ALL,
                            onClick = { filter = SessionFilter.ALL },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = filter == SessionFilter.SEVEN_DAYS,
                            onClick = { filter = SessionFilter.SEVEN_DAYS },
                            label = { Text("7 days") }
                        )
                        FilterChip(
                            selected = filter == SessionFilter.THIRTY_DAYS,
                            onClick = { filter = SessionFilter.THIRTY_DAYS },
                            label = { Text("30 days") }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-delete chats older than 30 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                        Switch(
                            checked = autoDeleteOld,
                            onCheckedChange = { enabled ->
                                scaffoldScope.launch { repository.setAutoDeleteOld(enabled) }
                                if (enabled && sessions.any { it.createdAt < now - SessionRepository.AUTO_DELETE_AGE_MILLIS }) {
                                    showPurgeConfirm = true
                                }
                            }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (sessions.isEmpty()) "No saved chats yet" else "No chats in this period",
                                color = OnSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { session ->
                        val selected = selectedIds[session.id] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariant, RoundedCornerShape(8.dp))
                                .clickable { onResume(session) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked -> selectedIds[session.id] = checked }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    color = TextColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = dateFormat.format(Date(session.createdAt)),
                                    color = OnSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                val ids = selectedIds.filterValues { it }.keys
                selectedIds.clear()
                scaffoldScope.launch { repository.deleteSessions(ids) }
            },
                enabled = selectedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Delete selected")
            }
        }
    }

    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            title = { Text("Delete Old Chats", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Delete chats older than 30 days?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurgeConfirm = false
                        scaffoldScope.launch {
                            repository.purgeOlderThan(SessionRepository.AUTO_DELETE_AGE_MILLIS)
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}