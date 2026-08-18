package com.musicedge.visualizer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicedge.visualizer.R
import com.musicedge.visualizer.ui.components.AppToggleRow
import com.musicedge.visualizer.ui.components.OutlinedInfoBox
import com.musicedge.visualizer.ui.components.SectionTitle
import com.musicedge.visualizer.ui.components.SettingsCard

/** One installed app, already rasterised for the list. */
data class AppRowItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isKnownMediaApp: Boolean,
)

/**
 * Lets the user decide which apps the visualizer reacts to. Music players are listed
 * first; everything else that is launchable follows, because a player the system does
 * not advertise as a music app can still own the media session.
 *
 * @param apps null while the package list is still being loaded off the main thread.
 */
@Composable
fun MusicAppsScreen(
    apps: List<AppRowItem>?,
    allowedPackages: Set<String>,
    onToggle: (packageName: String, allowed: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaApps = apps?.filter { it.isKnownMediaApp }.orEmpty()
    val otherApps = apps?.filterNot { it.isKnownMediaApp }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        Text(
            text = stringResource(R.string.music_apps),
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (apps == null) {
            Text(
                text = stringResource(R.string.music_apps_loading),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                OutlinedInfoBox(stringResource(R.string.music_apps_hint))
            }
            if (mediaApps.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.music_apps_players)) }
                items(mediaApps, key = { it.packageName }) { app ->
                    AppCard(app, app.packageName in allowedPackages, onToggle)
                }
            }
            if (otherApps.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.music_apps_other)) }
                items(otherApps, key = { it.packageName }) { app ->
                    AppCard(app, app.packageName in allowedPackages, onToggle)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppCard(
    app: AppRowItem,
    checked: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    SettingsCard {
        AppToggleRow(
            label = app.label,
            packageName = app.packageName,
            icon = app.icon,
            checked = checked,
            onCheckedChange = { allowed -> onToggle(app.packageName, allowed) },
        )
    }
}
