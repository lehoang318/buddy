package com.example.buddy.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.buddy.BuildConfig
import com.example.buddy.data.AppEvent
import com.example.buddy.data.EventLevel
import com.example.buddy.data.EventLog
import com.example.buddy.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val levelColors = mapOf(
    EventLevel.DEBUG to Color(0xFF9E9E9E),
    EventLevel.INFO to Color(0xFF829377),
    EventLevel.WARNING to Color(0xFFD4A017),
    EventLevel.ERROR to Color(0xFFBC6C4D)
)

private val levelIcons = mapOf(
    EventLevel.DEBUG to Icons.Filled.BugReport,
    EventLevel.ERROR to Icons.Filled.Error,
    EventLevel.WARNING to Icons.Filled.Warning,
    EventLevel.INFO to Icons.Filled.Info
)

private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val events by EventLog.events.collectAsState()
    var selectedEvent by remember { mutableStateOf<AppEvent?>(null) }
    var filterLevel by remember { mutableStateOf<EventLevel?>(null) }
    var filterTag by remember { mutableStateOf<String?>(null) }
    var filterCorrelation by remember { mutableStateOf<String?>(null) }

    val uniqueTags = remember(events) { events.map { it.tag }.distinct().sorted() }

    val filteredEvents = events.filter { event ->
        (filterLevel == null || event.level == filterLevel) &&
        (filterTag == null || event.tag == filterTag) &&
        (filterCorrelation == null || event.correlationId == filterCorrelation)
    }

    val hasActiveFilters = filterLevel != null || filterTag != null || filterCorrelation != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Text("Events", color = MaterialTheme.colorScheme.onSurface)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasActiveFilters) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append("Filtered")
                            filterCorrelation?.let { append(" (related)") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SendButton
                    )
                    TextButton(onClick = {
                        filterLevel = null
                        filterTag = null
                        filterCorrelation = null
                    }) {
                        Text("Clear filters", color = SendButton)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                            selected = filterLevel == null,
                            onClick = { filterLevel = null },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = filterLevel == EventLevel.ERROR,
                            onClick = { filterLevel = EventLevel.ERROR },
                            label = { Text("Errors") }
                        )
                        FilterChip(
                            selected = filterLevel == EventLevel.WARNING,
                            onClick = { filterLevel = EventLevel.WARNING },
                            label = { Text("Warnings") }
                        )
                        if (BuildConfig.DEBUG) {
                            FilterChip(
                                selected = filterLevel == EventLevel.INFO,
                                onClick = { filterLevel = EventLevel.INFO },
                                label = { Text("Info") }
                            )
                            FilterChip(
                                selected = filterLevel == EventLevel.DEBUG,
                                onClick = { filterLevel = EventLevel.DEBUG },
                                label = { Text("Debug") }
                            )
                        }
                    }
                }

                if (uniqueTags.isNotEmpty()) {
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = filterTag == null,
                                onClick = { filterTag = null },
                                label = { Text("All tags") }
                            )
                            uniqueTags.forEach { tag ->
                                FilterChip(
                                    selected = filterTag == tag,
                                    onClick = { filterTag = tag },
                                    label = { Text(tag) }
                                )
                            }
                        }
                    }
                }

                items(filteredEvents) { event ->
                    EventRow(
                        event = event,
                        onClick = { selectedEvent = event }
                    )
                }
            }
        }
    }

    selectedEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text("Event Details") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    DetailRow("Level", event.level.name)
                    DetailRow("Tag", event.tag)
                    DetailRow("Time", dateFormat.format(Date(event.timestamp)))
                    event.durationMs?.let { DetailRow("Duration", "${it}ms") }
                    event.correlationId?.let { DetailRow("Correlation", it.take(8) + "...") }
                    Spacer(Modifier.height(8.dp))
                    Text("Message", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    SelectionContainer {
                        Text(
                            text = event.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Data", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    SelectionContainer {
                        Text(
                            text = event.data ?: "No additional data",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEvent = null }) {
                    Text("Close")
                }
            },
            dismissButton = event.correlationId?.let { cid ->
                {
                    TextButton(onClick = {
                        filterCorrelation = cid
                        selectedEvent = null
                    }) {
                        Text("Show related")
                    }
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun EventRow(event: AppEvent, onClick: () -> Unit) {
    val levelColor = levelColors[event.level] ?: Color.Gray
    val levelIcon = levelIcons[event.level]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (levelIcon != null) {
                    Icon(
                        imageVector = levelIcon,
                        contentDescription = event.level.name,
                        tint = levelColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    color = levelColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = event.tag,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = levelColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = dateFormat.format(Date(event.timestamp)),
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
            Text(
                text = event.text,
                color = TextColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        event.durationMs?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${it}ms",
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
