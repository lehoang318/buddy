package com.example.buddy.ui.parameters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.buddy.data.LlmDefaults
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SettingsRepository
import com.example.buddy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametersScreen(
    onBack: () -> Unit,
    initialSettings: LlmSettings? = null,
    onSaveParameters: (temperature: Float, topP: Float, topK: Int, systemMessage: String) -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }

    val savedSettings by settingsRepository.settings.collectAsState(initial = initialSettings ?: LlmSettings())
    val effectiveInitial = initialSettings ?: savedSettings

    val initialTemperature = effectiveInitial.temperature.takeIf { it > 0f } ?: LlmDefaults.temperature
    val initialTopP = effectiveInitial.topP.takeIf { it > 0f } ?: LlmDefaults.topP
    val initialTopK = effectiveInitial.topK.takeIf { it > 0 } ?: LlmDefaults.topK
    
    var temperature by remember(initialTemperature) { mutableStateOf(initialTemperature) }
    var topP by remember(initialTopP) { mutableStateOf(initialTopP) }
    var topK by remember(initialTopK) { mutableStateOf(initialTopK) }
    var systemMessage by remember(effectiveInitial.systemMessage) { mutableStateOf(effectiveInitial.systemMessage) }

    fun resetToDefaults() {
        temperature = LlmDefaults.temperature
        topP = LlmDefaults.topP
        topK = LlmDefaults.topK
    }

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
                    Text("Parameters", color = MaterialTheme.colorScheme.onSurface)
                },
                actions = {
                    IconButton(onClick = { resetToDefaults() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset to defaults",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SliderWithLabel(
                label = "Temperature",
                tooltip = "0.0 \u2013 0.3: factual answers, math, code, precise tasks\n0.5 \u2013 0.8: balanced chat, reasoning, general use (0.7 is a popular default)\n0.9 \u2013 1.2+: creative writing, brainstorming, storytelling",
                value = temperature,
                valueRange = 0f..1f,
                steps = 9,
                onValueChange = { temperature = it; onSaveParameters(temperature, topP, topK, systemMessage) },
                valueDisplay = String.format("%.1f", temperature)
            )

            SliderWithLabel(
                label = "Top-p",
                tooltip = "0.1 \u2013 0.5: more focused, deterministic output\n0.7 \u2013 0.95: good balance (0.9 is very common)\n1.0: no restriction (consider all tokens)",
                value = topP,
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = { topP = it; onSaveParameters(temperature, topP, topK, systemMessage) },
                valueDisplay = String.format("%.2f", topP)
            )

            SliderWithLabel(
                label = "Top-k",
                tooltip = "1: greedy decoding (very deterministic)\n40 \u2013 100: common default in many open-source setups\nHigher values: more diversity",
                value = topK.toFloat(),
                valueRange = 1f..100f,
                steps = 19,
                onValueChange = { topK = it.toInt(); onSaveParameters(temperature, topP, topK, systemMessage) },
                valueDisplay = topK.toString(),
                isInt = true
            )

            OutlinedTextField(
                value = systemMessage,
                onValueChange = { systemMessage = it; onSaveParameters(temperature, topP, topK, systemMessage) },
                label = { Text("System Message") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextColor,
                    unfocusedTextColor = TextColor,
                    focusedContainerColor = SurfaceVariant,
                    unfocusedContainerColor = SurfaceVariant,
                    focusedBorderColor = SendButton,
                    unfocusedBorderColor = Outline,
                    focusedLabelColor = SendButton,
                    unfocusedLabelColor = OnSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    tooltip: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueDisplay: String,
    isInt: Boolean = false
) {
    var showTooltip by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { showTooltip = !showTooltip },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Tooltip for $label",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(valueDisplay, color = SendButton, style = MaterialTheme.typography.labelMedium)
        }

        if (showTooltip) {
            Surface(
                color = SurfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    tooltip,
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = SendButton,
                activeTrackColor = SendButton,
                inactiveTrackColor = Outline
            )
        )
    }
}
