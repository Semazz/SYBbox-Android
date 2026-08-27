package com.sybbox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.ChevronRight
import com.sybbox.ui.components.IconTile
import com.sybbox.ui.components.SettingsRowCard
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.BuildConfig
import com.sybbox.R
import com.sybbox.ui.components.SettingsAction
import com.sybbox.ui.components.SettingsGroup
import com.sybbox.ui.components.PillShape
import com.sybbox.ui.theme.SybSpacing

enum class SettingsSection(val titleRes: Int, val icon: ImageVector, val accent: Long) {
    APPEARANCE(R.string.group_appearance, Icons.Rounded.Palette, 0xFFB86FA8),
    CONNECTION(R.string.group_connection, Icons.Rounded.Bolt, 0xFF3F7ED8),
    ROUTING(R.string.group_routing, Icons.Rounded.AltRoute, 0xFF3FA98A),
    DNS(R.string.group_dns, Icons.Rounded.Dns, 0xFF7A6FD8),
    TRANSPORT(R.string.group_tls, Icons.Rounded.Layers, 0xFFD8863F),
    TUNNEL(R.string.group_tun, Icons.Rounded.Shield, 0xFF4FA33F),
    LOCAL_PROXY(R.string.group_local_proxy, Icons.Rounded.Router, 0xFF3FA0B8),
    SUBSCRIPTIONS(R.string.group_subscriptions, Icons.Rounded.Sync, 0xFFD85F7A),
    STARTUP(R.string.group_startup, Icons.Rounded.PowerSettingsNew, 0xFF8A8F3F),
    DIAGNOSTICS(R.string.group_diagnostics, Icons.Rounded.BugReport, 0xFF6F8FA8),
    MAINTENANCE(R.string.group_maintenance, Icons.Rounded.RestartAlt, 0xFFB8763F),
    ABOUT(R.string.group_about, Icons.Rounded.Info, 0xFF7F7F8F),
}

@Composable
fun SettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenPerApp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val results = remember(query) {
        val needle = query.trim()
        if (needle.isBlank()) {
            emptyList()
        } else {
            settingsIndex.filter { entry ->
                context.getString(entry.titleRes).contains(needle, ignoreCase = true) ||
                    entry.summaryRes?.let {
                        context.getString(it).contains(needle, ignoreCase = true)
                    } == true ||
                    context.getString(entry.section.titleRes).contains(needle, ignoreCase = true)
            }
        }
    }
    val searching = query.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SybSpacing.screen,
            end = SybSpacing.screen,
            bottom = SybSpacing.listBottom,
        ),
    ) {
        item {
            Text(
                stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = SybSpacing.small, bottom = SybSpacing.medium),
            )
        }

        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(bottom = SybSpacing.small),
            )
        }

        if (searching) {
            if (results.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.nothing_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SybSpacing.xlarge),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                item {
                    SettingsGroup(stringResource(R.string.settings_search)) {
                        results.forEachIndexed { index, entry ->
                            if (index > 0) SettingsDividerRow()
                            SettingsAction(
                                title = stringResource(entry.titleRes),
                                summary = stringResource(entry.section.titleRes),
                                icon = entry.section.icon,
                                onClick = { onOpenSection(entry.section) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(SybSpacing.xlarge)) }
            return@LazyColumn
        }

        item {
            SettingsGroup(stringResource(R.string.group_interface)) {
                SectionRow(SettingsSection.APPEARANCE, onOpenSection)
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_tunnel_settings)) {
                SectionRow(SettingsSection.CONNECTION, onOpenSection)
                SettingsDividerRow()
                SectionRow(SettingsSection.ROUTING, onOpenSection)
                SettingsDividerRow()
                SettingsAction(
                    title = stringResource(R.string.per_app_routing),
                    value = if (state.perAppProxy) {
                        stringResource(
                            R.string.apps_selected,
                            state.includedApps.size + state.excludedApps.size,
                        )
                    } else {
                        null
                    },
                    icon = Icons.Rounded.Apps,
                    onClick = onOpenPerApp,
                )
                SettingsDividerRow()
                SectionRow(SettingsSection.DNS, onOpenSection)
                SettingsDividerRow()
                SectionRow(SettingsSection.TRANSPORT, onOpenSection)
                SettingsDividerRow()
                SectionRow(SettingsSection.TUNNEL, onOpenSection)
                SettingsDividerRow()
                SectionRow(SettingsSection.LOCAL_PROXY, onOpenSection)
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_advanced)) {
                SectionRow(SettingsSection.SUBSCRIPTIONS, onOpenSection)
                SettingsDividerRow()
                SectionRow(SettingsSection.STARTUP, onOpenSection)
                SettingsDividerRow()
                SectionRow(SettingsSection.DIAGNOSTICS, onOpenSection)
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_other)) {
                SettingsAction(
                    title = stringResource(R.string.open_logs),
                    icon = Icons.Rounded.Article,
                    onClick = onOpenLogs,
                )
                SettingsDividerRow()
                SectionRow(SettingsSection.MAINTENANCE, onOpenSection)
                SettingsDividerRow()
                SectionRow(SettingsSection.ABOUT, onOpenSection)
            }
        }

        item {
            Spacer(Modifier.height(SybSpacing.xlarge))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(SybSpacing.tight))
                Text(
                    stringResource(R.string.developed_by),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SectionRow(section: SettingsSection, onOpen: (SettingsSection) -> Unit) {
    val accent = Color(section.accent)
    SettingsRowCard(onClick = { onOpen(section) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SybSpacing.rowH, vertical = SybSpacing.cardV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                section.icon,
                tint = accent,
                container = accent.copy(alpha = 0.18f),
            )
            Spacer(Modifier.width(SybSpacing.iconGap))
            Text(
                stringResource(section.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingsDividerRow() {
    com.sybbox.ui.components.SettingsDivider()
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                stringResource(R.string.settings_search),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Clear,
                        stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        shape = PillShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    )
}
