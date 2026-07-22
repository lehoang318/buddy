package com.example.buddy.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.buddy.R
import com.example.buddy.ui.theme.Outline
import com.example.buddy.ui.theme.SendButton
import com.example.buddy.ui.theme.SurfaceVariant

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
            Text(stringResource(R.string.web_search_used), color = SendButton, style = MaterialTheme.typography.labelSmall)
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
            Text(stringResource(R.string.web_search_failed, error), color = SendButton, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun WebSearchSkippedPill() {
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
            Icon(Icons.Default.Block, null, tint = SendButton, modifier = Modifier.size(11.dp))
            Text(stringResource(R.string.web_search_skipped), color = SendButton, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun WebSearchCancelledPill() {
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
            Icon(Icons.Default.Stop, null, tint = SendButton, modifier = Modifier.size(11.dp))
            Text(stringResource(R.string.web_search_cancelled), color = SendButton, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
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
