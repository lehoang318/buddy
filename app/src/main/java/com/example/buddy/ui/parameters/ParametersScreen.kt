package com.example.buddy.ui.parameters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.data.LlmSettings
import com.example.buddy.data.SettingsRepository
import com.example.buddy.ui.components.SliderWithLabel
import com.example.buddy.ui.theme.OnSurfaceVariant
import com.example.buddy.ui.theme.Outline
import com.example.buddy.ui.theme.SendButton
import com.example.buddy.ui.theme.SurfaceVariant
import com.example.buddy.ui.theme.TextColor
import java.util.Locale

private const val TAG = "Settings"

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

    val initialTemperature = effectiveInitial.temperature.takeIf { it > 0f } ?: AppResources.llm.temperature
    val initialTopP = effectiveInitial.topP.takeIf { it > 0f } ?: AppResources.llm.topP
    val initialTopK = effectiveInitial.topK.takeIf { it > 0 } ?: AppResources.llm.topK

    var temperature by remember(initialTemperature) { mutableFloatStateOf(initialTemperature) }
    var topP by remember(initialTopP) { mutableFloatStateOf(initialTopP) }
    var topK by remember(initialTopK) { mutableIntStateOf(initialTopK) }
    var systemMessage by remember(effectiveInitial.systemMessage) { mutableStateOf(effectiveInitial.systemMessage) }

    fun resetToDefaults() {
        temperature = AppResources.llm.temperature
        topP = AppResources.llm.topP
        topK = AppResources.llm.topK
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                navigationIcon = {
                    IconButton(onClick = {
                        EventLog.info(TAG, "Parameters updated", "temp=$temperature, topP=$topP, topK=$topK")
                        onBack()
                    }) {
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
                valueDisplay = String.format(Locale.US, "%.1f", temperature)
            )

            SliderWithLabel(
                label = "Top-p",
                tooltip = "0.1 \u2013 0.5: more focused, deterministic output\n0.7 \u2013 0.95: good balance (0.9 is very common)\n1.0: no restriction (consider all tokens)",
                value = topP,
                valueRange = 0f..1f,
                steps = 19,
                onValueChange = { topP = it; onSaveParameters(temperature, topP, topK, systemMessage) },
                valueDisplay = String.format(Locale.US, "%.2f", topP)
            )

            SliderWithLabel(
                label = "Top-k",
                tooltip = "1: greedy decoding (very deterministic)\n40 \u2013 100: common default in many open-source setups\nHigher values: more diversity",
                value = topK.toFloat(),
                valueRange = 1f..100f,
                steps = 19,
                onValueChange = { topK = it.toInt(); onSaveParameters(temperature, topP, topK, systemMessage) },
                valueDisplay = topK.toString()
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
