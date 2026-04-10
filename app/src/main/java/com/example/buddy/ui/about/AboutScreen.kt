package com.example.buddy.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.buddy.R
import com.example.buddy.ui.theme.*
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val versionMajor = integerResource(R.integer.app_version_major)
    val versionMinor = integerResource(R.integer.app_version_minor)
    val buildDate = stringResource(R.string.app_build_date)
    val author = stringResource(R.string.app_author).trim()
    val email = stringResource(R.string.app_email).trim()
    val github = stringResource(R.string.app_github).trim()
    val githubName = github.removePrefix("https://")
        .removePrefix("github.com")
        .replace("/", "")
    val linkedin = stringResource(R.string.app_linkedin).trim()
    val linkedinName = linkedin.removePrefix("https://")
        .removePrefix("www.linkedin.com/in")
        .replace("/", "")

    fun openEmail() {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri())
        context.startActivity(intent)
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
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
                    Text("About", color = MaterialTheme.colorScheme.onSurface)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.avatar),
                contentDescription = "Buddy Logo",
                modifier = Modifier.size(Dimens.BuddyAvatarSize * 2),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "Buddy",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Version $versionMajor.$versionMinor",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Build: $buildDate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Contact & Links",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(color = Outline)

                    ClickableRow(
                        icon = Icons.Default.Person,
                        label = "Author",
                        value = author,
                        onClick = null
                    )

                    ClickableRow(
                        icon = Icons.Default.Email,
                        label = "Email",
                        value = email,
                        onClick = { openEmail() }
                    )

                    ClickableRow(
                        icon = Icons.Default.Code,
                        label = "GitHub",
                        value = githubName,
                        onClick = { openUrl(github) }
                    )

                    ClickableRow(
                        icon = Icons.Default.Work,
                        label = "LinkedIn",
                        value = linkedinName,
                        onClick = { openUrl(linkedin) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (onClick != null) SendButton else OnSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onClick != null) SendButton else MaterialTheme.colorScheme.onSurface
            )
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SendButton,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}