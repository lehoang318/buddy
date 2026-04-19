package com.example.buddy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.buddy.ext.LlmModel
import com.example.buddy.ui.theme.*

import androidx.compose.material.icons.filled.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    availableModels: List<LlmModel>,
    onBack: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedModelId by remember { mutableStateOf("") }

    val filteredModels = remember(searchQuery, availableModels) {
        availableModels.filter { model ->
            model.name.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Text("Select Model", color = MaterialTheme.colorScheme.onSurface)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search models...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextColor,
                    unfocusedTextColor = TextColor,
                    focusedContainerColor = SurfaceVariant,
                    unfocusedContainerColor = SurfaceVariant,
                    focusedBorderColor = Outline,
                    unfocusedBorderColor = Outline,
                    focusedLabelColor = OnSurface,
                    unfocusedLabelColor = OnSurfaceVariant
                )
            )

            if (filteredModels.isEmpty()) {
                EmptyState(
                    text = if (searchQuery.isNotEmpty()) "No models found" else "No models available",
                    subtext = if (searchQuery.isNotEmpty()) "Try a different search term" else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredModels) { model ->
                        ModelListItem(
                            model = model,
                            isSelected = selectedModelId == model.id,
                            onClick = {
                                selectedModelId = model.id
                                onModelSelected(model.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    text: String,
    subtext: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = OnSurfaceVariant
        )
        subtext?.let { subtitle ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ModelListItem(
    model: LlmModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) SendButton.copy(alpha = 0.15f)
                else SurfaceVariant,
                MaterialTheme.shapes.small
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Image,
            contentDescription = null,
            tint = if (model.isMultimodal) SendButton else OnSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = model.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) SendButton else TextColor
        )
    }
}

@Composable
fun ModelSelectionDialog(
    availableModels: List<LlmModel>,
    currentModelId: String,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredModels = remember(searchQuery, availableModels) {
        availableModels.filter { model ->
            model.name.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Model", color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models...") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedBorderColor = Outline,
                        unfocusedBorderColor = Outline,
                        focusedLabelColor = OnSurface,
                        unfocusedLabelColor = OnSurfaceVariant
                    )
                )

                if (filteredModels.isEmpty()) {
                    EmptyState(
                        text = if (searchQuery.isNotEmpty()) "No models found" else "No models available",
                        subtext = if (searchQuery.isNotEmpty()) "Try a different search term" else null
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredModels) { model ->
                            ModelListItem(
                                model = model,
                                isSelected = currentModelId == model.id,
                                onClick = { onModelSelected(model.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}