package com.sybbox.ui.home

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.R
import com.sybbox.domain.model.ConnectionState
import com.sybbox.domain.model.ServerProfile
import com.sybbox.service.SybBoxVpnService
import com.sybbox.ui.components.CardShape
import com.sybbox.ui.components.IconTile
import com.sybbox.ui.components.LatencyBadge
import com.sybbox.ui.components.PillShape
import com.sybbox.ui.components.SybCard
import com.sybbox.ui.theme.LatencySlow

@Composable
fun HomeScreen(
    onBrowseServers: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val latencies by viewModel.latencies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selected = profiles.firstOrNull { it.id == selectedId }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
        } else {
            viewModel.reportPermissionDenied()
        }
    }

    fun toggle() {
        when (appState.connectionState) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> viewModel.disconnect()
            else -> {
                if (selected == null) {
                    viewModel.reportNoServer()
                    onBrowseServers()
                    return
                }
                val consent = VpnService.prepare(context)
                if (consent != null) vpnPermission.launch(consent) else viewModel.connect()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.statusBarsPadding())
        Spacer(Modifier.height(12.dp))
        Wordmark()
        Spacer(Modifier.height(28.dp))

        ConnectButton(state = appState.connectionState, onClick = ::toggle)
        Spacer(Modifier.height(20.dp))

        StatusLine(appState.connectionState, appState.lastError)
        Spacer(Modifier.height(24.dp))

        val pingVisibleUntil by viewModel.pingVisibleUntil.collectAsStateWithLifecycle()
        val pingBusy by viewModel.pingTesting.collectAsStateWithLifecycle()
        var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(pingVisibleUntil) {
            while (nowTick < pingVisibleUntil) {
                kotlinx.coroutines.delay(1_000)
                nowTick = System.currentTimeMillis()
            }
        }

        ActiveServerCard(
            profile = selected,
            latency = latencies[selected?.id],
            showPing = selected != null && nowTick < pingVisibleUntil,
            pingBusy = pingBusy,
            onClick = onBrowseServers,
            onTest = viewModel::pingSelected,
        )

        AnimatedVisibility(
            visible = appState.connectionState == ConnectionState.CONNECTED,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(150)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = Icons.Rounded.ArrowDownward,
                        label = stringResource(R.string.download),
                        value = SybBoxVpnService.formatSpeed(appState.stats.downloadSpeed),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.Rounded.ArrowUpward,
                        label = stringResource(R.string.upload),
                        value = SybBoxVpnService.formatSpeed(appState.stats.uploadSpeed),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = Icons.Rounded.Schedule,
                        label = stringResource(R.string.uptime),
                        value = SybBoxVpnService.formatDuration(appState.stats.duration),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.Rounded.SwapVert,
                        label = stringResource(R.string.total_traffic),
                        value = SybBoxVpnService.formatBytes(
                            appState.stats.totalUpload + appState.stats.totalDownload,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Wordmark() {
    Image(
        painter = painterResource(R.drawable.ic_logo_sybbox),
        contentDescription = "SYBbox",
        modifier = Modifier.height(20.dp),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
    )
}

@Composable
private fun ConnectButton(state: ConnectionState, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "connect")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == ConnectionState.CONNECTING) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val halo by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = if (state == ConnectionState.CONNECTED) 0.24f else 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )

    val accent = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.primary
        ConnectionState.FAILED -> LatencySlow
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(208.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = halo), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(156.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (state == ConnectionState.CONNECTED) 1f else 0.14f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (state == ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = accent,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    Icons.Rounded.Power,
                    contentDescription = stringResource(
                        if (state == ConnectionState.CONNECTED) R.string.disconnect else R.string.connect,
                    ),
                    tint = if (state == ConnectionState.CONNECTED) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        accent
                    },
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: ConnectionState, error: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(
                when (state) {
                    ConnectionState.CONNECTED -> R.string.connected
                    ConnectionState.CONNECTING -> R.string.connecting
                    ConnectionState.FAILED -> R.string.connection_failed
                    ConnectionState.DISCONNECTED -> R.string.disconnected
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (state == ConnectionState.FAILED) LatencySlow else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = error?.takeIf { state == ConnectionState.FAILED }
                ?: stringResource(
                    if (state == ConnectionState.CONNECTED) R.string.tap_to_disconnect else R.string.tap_to_connect,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActiveServerCard(
    profile: ServerProfile?,
    latency: Int?,
    showPing: Boolean,
    pingBusy: Boolean,
    onClick: () -> Unit,
    onTest: () -> Unit,
) {
    SybCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Rounded.Public)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.active_server),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    profile?.name?.ifBlank { profile.address } ?: stringResource(R.string.no_server_selected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (profile != null) {
                Spacer(Modifier.width(10.dp))
                when {
                    pingBusy -> Icon(
                        Icons.Rounded.Speed,
                        contentDescription = stringResource(R.string.testing),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    showPing -> LatencyBadge(
                        latency = latency ?: profile.lastLatency,
                        modifier = Modifier.clickable(onClick = onTest),
                    )
                    else -> Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable(onClick = onTest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = stringResource(R.string.check_ping),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            IconTile(icon, size = 18)
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}