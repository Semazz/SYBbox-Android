package com.sybbox.ui.logs

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.R
import com.sybbox.core.CoreLog
import com.sybbox.core.LogSource
import com.sybbox.service.SybBoxVpnService
import com.sybbox.ui.components.EmptyState
import com.sybbox.ui.components.IconTile
import com.sybbox.ui.components.SectionHeader
import com.sybbox.ui.components.SybCard
import com.sybbox.ui.theme.SybSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsIndexScreen(onOpen: (String?) -> Unit, onBack: () -> Unit) {
    val sources by CoreLog.sources.collectAsStateWithLifecycle()
    val used by CoreLog.used.collectAsStateWithLifecycle()
    val limit by CoreLog.limit.collectAsStateWithLifecycle()
    val entries by CoreLog.entries.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { CoreLog.prune() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.logs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { CoreLog.clear() }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            stringResource(R.string.clear_logs),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SybSpacing.screen,
                end = SybSpacing.screen,
                bottom = SybSpacing.listBottom,
            ),
        ) {
            item { UsageCard(used = used, limit = limit, lines = entries.size, onClick = { onOpen(null) }) }

            if (sources.isEmpty()) {
                item {
                    Spacer(Modifier.height(SybSpacing.xlarge))
                    EmptyState(
                        icon = Icons.Rounded.Article,
                        title = stringResource(R.string.no_logs),
                        hint = stringResource(R.string.tap_to_connect),
                    )
                }
                return@LazyColumn
            }

            item { SectionHeader(stringResource(R.string.logs_by_server)) }

            items(sources, key = { it.name }) { source ->
                Spacer(Modifier.height(SybSpacing.small))
                SourceRow(source = source, onClick = { onOpen(source.name) })
            }
        }
    }
}

@Composable
private fun UsageCard(used: Long, limit: Long, lines: Int, onClick: () -> Unit) {
    val fraction = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    Spacer(Modifier.height(SybSpacing.small))
    SybCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SybSpacing.cardH, vertical = SybSpacing.cardV),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Article)
                Spacer(Modifier.width(SybSpacing.iconGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.all_logs),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(SybSpacing.labelGap))
                    Text(
                        stringResource(R.string.log_usage, format(used), format(limit)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.log_lines, lines, lines),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = SybSpacing.tight),
                )
            }
            Spacer(Modifier.height(SybSpacing.medium))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
private fun SourceRow(source: LogSource, onClick: () -> Unit) {
    val stamp = remember(source.lastAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(source.lastAt))
    }
    SybCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SybSpacing.cardH, vertical = SybSpacing.cardV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Rounded.Dns)
            Spacer(Modifier.width(SybSpacing.iconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SybSpacing.labelGap))
                Text(
                    stamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(SybSpacing.small))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    format(source.bytes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(SybSpacing.labelGap))
                Text(
                    pluralStringResource(R.plurals.log_lines, source.lines, source.lines),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SybSpacing.tight),
            )
        }
    }
}

private fun format(bytes: Long) = SybBoxVpnService.formatBytes(bytes)
