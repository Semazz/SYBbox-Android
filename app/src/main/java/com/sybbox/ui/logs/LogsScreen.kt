package com.sybbox.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.R
import com.sybbox.core.CoreLog
import com.sybbox.service.SybBoxVpnService
import com.sybbox.core.LogEntry
import com.sybbox.core.LogLevel
import com.sybbox.ui.components.EmptyState
import com.sybbox.ui.theme.SybSpacing
import com.sybbox.ui.theme.LatencyFast
import com.sybbox.ui.theme.LatencyMedium
import com.sybbox.ui.theme.LatencySlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(server: String?, onBack: () -> Unit) {
    val entries by CoreLog.entries.collectAsStateWithLifecycle()
    val used by CoreLog.used.collectAsStateWithLifecycle()
    val limit by CoreLog.limit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<LogLevel?>(null) }

    LaunchedEffect(Unit) { CoreLog.prune() }
    val listState = rememberLazyListState()

    val visible = remember(entries, filter, server) {
        entries.filter { entry ->
            (filter == null || entry.level == filter) && (server == null || entry.server == server)
        }
    }

    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(server ?: stringResource(R.string.logs), maxLines = 1)
                        Text(
                            stringResource(
                                R.string.log_usage,
                                SybBoxVpnService.formatBytes(used),
                                SybBoxVpnService.formatBytes(limit),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = visible.joinToString("\n") { "${it.level} ${it.message}" }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("SYBbox logs", text))
                        },
                    ) {
                        Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy_logs))
                    }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = SybSpacing.screen),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = filter == level,
                        onClick = { filter = if (filter == level) null else level },
                        label = { Text(level.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = levelColor(level).copy(alpha = 0.2f),
                            selectedLabelColor = levelColor(level),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Terminal,
                    title = stringResource(R.string.no_logs),
                    hint = stringResource(R.string.tap_to_connect),
                )
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = SybSpacing.screen, vertical = SybSpacing.small),
                ) {
                    items(visible.asReversed(), key = { it.id }) { entry -> LogRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val timestamp = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestamp))
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            timestamp,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(levelColor(entry.level).copy(alpha = 0.16f), RoundedCornerShape(50))
                .padding(horizontal = SybSpacing.chipH, vertical = SybSpacing.hair),
        ) {
            Text(
                entry.level.name.take(1),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = levelColor(entry.level),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (entry.repeats > 1) {
            Spacer(Modifier.width(6.dp))
            Text(
                "×${entry.repeats}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> LatencySlow
    LogLevel.WARN -> LatencyMedium
    LogLevel.INFO -> LatencyFast
    LogLevel.DEBUG -> MaterialTheme.colorScheme.tertiary
    LogLevel.TRACE -> MaterialTheme.colorScheme.onSurfaceVariant
}
