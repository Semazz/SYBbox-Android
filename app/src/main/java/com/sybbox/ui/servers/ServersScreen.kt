package com.sybbox.ui.servers

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.R
import com.sybbox.domain.model.ConnectionState
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.Subscription
import com.sybbox.service.ConfigShare
import com.sybbox.service.SybBoxVpnService
import com.sybbox.ui.components.EmptyState
import com.sybbox.ui.components.IconTile
import com.sybbox.ui.components.LatencyBadge
import com.sybbox.ui.components.ProtocolChip
import com.sybbox.ui.components.SectionHeader
import com.sybbox.ui.components.SybCard
import com.sybbox.ui.components.protocolColor
import java.text.DateFormat
import java.util.Date

@Composable
fun ServersScreen(
    onScanQr: () -> Unit,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val latencies by viewModel.latencies.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val vpnConnected by SybBoxVpnService.appState.collectAsStateWithLifecycle()
    val pingVisibleUntil by viewModel.pingVisibleUntil.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var nowTick by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(pingVisibleUntil.isNotEmpty()) {
        while (pingVisibleUntil.isNotEmpty()) {
            kotlinx.coroutines.delay(1000)
            nowTick = System.currentTimeMillis()
        }
        nowTick = System.currentTimeMillis()
    }

    var showAddMenu by remember { mutableStateOf(false) }
    var addServerDialog by remember { mutableStateOf(false) }
    var addSubscriptionDialog by remember { mutableStateOf(false) }
    var expandedSubscription by remember { mutableStateOf<Long?>(null) }
    var expandedManual by remember { mutableStateOf(true) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrDialogProfile by remember { mutableStateOf<ServerProfile?>(null) }

    val manual = profiles.filter { it.subscriptionId == 0L }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (!text.isNullOrBlank()) viewModel.importText(text)
            } catch (_: Exception) {}
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.nav_servers),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(
                        onClick = { showAddMenu = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            stringResource(R.string.cd_add),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    AddMenu(
                        expanded = showAddMenu,
                        onDismiss = { showAddMenu = false },
                        onScan = { showAddMenu = false; onScanQr() },
                        onPaste = {
                            showAddMenu = false
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val text = clip?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (text.isNullOrBlank()) viewModel.notifyClipboardEmpty()
                            else viewModel.importText(text)
                        },
                        onLink = { showAddMenu = false; addServerDialog = true },
                        onFile = { showAddMenu = false; filePickerLauncher.launch("*/*") },
                    )
                }
            }
        }

        if (profiles.isEmpty() && subscriptions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.no_servers_title),
                    hint = stringResource(R.string.no_servers_hint),
                )
            }
        }

        if (manual.isNotEmpty()) {
            item(key = "manual-group") {
                SybCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { expandedManual = !expandedManual }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.servers),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.server_count, manual.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    expandedManual = true
                                    viewModel.measureAll(manual)
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Speed,
                                        stringResource(R.string.ping_all),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { expandedManual = !expandedManual },
                                modifier = Modifier.size(36.dp),
                            ) {
                                val rot by animateFloatAsState(
                                    targetValue = if (expandedManual) 180f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                    label = "expandManual",
                                )
                                Icon(
                                    Icons.Rounded.ExpandMore,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp).rotate(rot),
                                )
                            }
                        }
                    }
            }
            val manualHasSelected = manual.any { it.id == selectedId }
            val manualVisible = when {
                expandedManual -> manual
                manualHasSelected -> manual.filter { it.id == selectedId }
                else -> emptyList()
            }
            items(manualVisible, key = { "m-${it.id}" }) { profile ->
                val showLatency = nowTick < (pingVisibleUntil[profile.id] ?: 0L)
                Box(modifier = Modifier.padding(bottom = 6.dp)) {
                    ServerRow(
                        profile = profile,
                        selected = profile.id == selectedId,
                        latency = if (showLatency) latencies[profile.id] ?: profile.lastLatency else 0,
                        testing = profile.id in testing,
                        onSelect = { viewModel.select(profile.id) },
                        onPing = {
                            expandedManual = true
                            viewModel.measureLatency(profile)
                        },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onCopied = { viewModel.notifyCopied() },
                        onShareQr = { qrDialogProfile = profile; showQrDialog = true },
                    )
                }
            }
        }

        subscriptions.forEach { subscription ->
            val members = profiles.filter { it.subscriptionId == subscription.id }
            val isSelectedInside = members.any { it.id == selectedId }
            val isExpanded = expandedSubscription == subscription.id
            val visibleMembers = when {
                isExpanded -> members
                isSelectedInside -> members.filter { it.id == selectedId }
                else -> emptyList()
            }
            item(key = "sub-${subscription.id}") {
                SubscriptionHeader(
                    subscription = subscription,
                    count = members.size,
                    expanded = isExpanded,
                    refreshing = subscription.id in refreshing,
                    showPing = true,
                    onToggle = {
                        expandedSubscription =
                            if (expandedSubscription == subscription.id) null else subscription.id
                    },
                    onRefresh = { viewModel.refreshSubscription(subscription) },
                    onPingAll = {
                        expandedSubscription = subscription.id
                        viewModel.measureAll(members)
                    },
                    onCopyLink = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("subscription", subscription.url))
                        viewModel.notifyCopied()
                    },
                    onOpenLink = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(subscription.url)))
                        } catch (_: Exception) {}
                    },
                    onDelete = { viewModel.deleteSubscription(subscription) },
                )
            }
            items(visibleMembers, key = { "s${subscription.id}-${it.id}" }) { profile ->
                val showLatency = nowTick < (pingVisibleUntil[profile.id] ?: 0L)
                Box(modifier = Modifier.padding(bottom = 6.dp)) {
                    ServerRow(
                        profile = profile,
                        selected = profile.id == selectedId,
                        latency = if (showLatency) latencies[profile.id] ?: profile.lastLatency else 0,
                        testing = profile.id in testing,
                        onSelect = { viewModel.select(profile.id) },
                        onPing = {
                            expandedSubscription = subscription.id
                            viewModel.measureLatency(profile)
                        },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onCopied = { viewModel.notifyCopied() },
                        onShareQr = { qrDialogProfile = profile; showQrDialog = true },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (addServerDialog) {
        TextEntryDialog(
            title = stringResource(R.string.add_server),
            label = stringResource(R.string.server_link),
            hint = stringResource(R.string.server_link_hint),
            onConfirm = { viewModel.importText(it) },
            onDismiss = { addServerDialog = false },
        )
    }

    if (addSubscriptionDialog) {
        SubscriptionDialog(
            onConfirm = { url, name -> viewModel.addSubscription(url, name) },
            onDismiss = { addSubscriptionDialog = false },
        )
    }

    if (showQrDialog && qrDialogProfile != null) {
        QrShareDialog(
            profile = qrDialogProfile!!,
            onDismiss = { showQrDialog = false; qrDialogProfile = null },
        )
    }
}

@Composable
private fun AddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp),
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scan_qr)) },
            leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, null) },
            onClick = onScan,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.import_from_clipboard)) },
            leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
            onClick = onPaste,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.import_link)) },
            leadingIcon = { Icon(Icons.Rounded.Link, null) },
            onClick = onLink,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.import_from_file)) },
            leadingIcon = { Icon(Icons.Rounded.Storage, null) },
            onClick = onFile,
        )
    }
}

@Composable
private fun ServerRow(
    profile: ServerProfile,
    selected: Boolean,
    latency: Int,
    testing: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    onCopied: () -> Unit,
    onShareQr: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shareable = remember(profile) { ConfigShare.canShare(profile) }

    SybCard(modifier = Modifier.fillMaxWidth(), onClick = onSelect, selected = selected) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pColor = protocolColor(profile.protocol)
            val cleanName = remember(profile.name) { com.sybbox.ui.components.stripFlagEmoji(profile.displayName()) }
            val code = remember(cleanName) { com.sybbox.ui.components.countryCodeFromName(cleanName) }
            val ctx = LocalContext.current
            val flagRes = remember(code, ctx) {
                if (code == null) 0 else ctx.resources.getIdentifier("flag_$code", "drawable", ctx.packageName)
            }
            when {
                flagRes != 0 -> Image(
                    painter = androidx.compose.ui.res.painterResource(flagRes),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                code != null -> Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        com.sybbox.ui.components.flagEmojiIn(profile.displayName()) ?: "",
                        fontSize = 20.sp,
                    )
                }
                else -> IconTile(
                    Icons.Rounded.Shield,
                    tint = pColor,
                    container = pColor.copy(alpha = 0.14f),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    cleanName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    profile.subInfoLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            LatencyBadge(latency, testing)
            IconButton(
                onClick = onPing,
                modifier = Modifier.size(32.dp),
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Speed,
                        stringResource(R.string.check_ping),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        stringResource(R.string.cd_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.test_connection)) },
                        leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                        onClick = { menu = false; onPing() },
                    )

                    if (shareable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_link)) },
                            leadingIcon = { Icon(Icons.Rounded.Link, null) },
                            onClick = {
                                menu = false
                                if (ConfigShare.copyToClipboard(context, profile)) onCopied()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_link)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, null) },
                            onClick = { menu = false; ConfigShare.shareConfig(context, profile) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_qr)) },
                            leadingIcon = { Icon(Icons.Rounded.QrCode, null) },
                            onClick = { menu = false; onShareQr() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionHeader(
    subscription: Subscription,
    count: Int,
    expanded: Boolean,
    refreshing: Boolean,
    showPing: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onPingAll: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenLink: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val used = subscription.upload + subscription.download
    val progress = if (subscription.total > 0) (used.toFloat() / subscription.total).coerceIn(0f, 1f) else null
    Column {
        SybCard(modifier = Modifier.fillMaxWidth(), onClick = onToggle) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.CloudSync)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            subscription.name.ifBlank { stringResource(R.string.subscriptions) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.server_count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        if (showPing) {
                            IconButton(
                                onClick = onPingAll,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Speed,
                                        stringResource(R.string.ping_all),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    stringResource(R.string.cd_refresh),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(36.dp),
                    ) {
                        val rot by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "expandSub",
                        )
                        Icon(
                            Icons.Rounded.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).rotate(rot),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Box {
                        IconButton(
                            onClick = { menu = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                stringResource(R.string.cd_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.update)) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                onClick = { menu = false; onRefresh() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_link)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = { menu = false; onCopyLink() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_link)) },
                                leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                                onClick = { menu = false; onOpenLink() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() },
                            )
                        }
                    }
                }
                val detail = subscriptionDetail(subscription)
                if (detail != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = if (progress > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun subscriptionDetail(subscription: Subscription): String? {
    val used = subscription.upload + subscription.download
    val parts = buildList {
        if (subscription.total > 0) {
            add(
                stringResource(
                    R.string.traffic_used,
                    SybBoxVpnService.formatBytes(used),
                    SybBoxVpnService.formatBytes(subscription.total),
                ),
            )
        } else if (used > 0) {
            add("${SybBoxVpnService.formatBytes(used)} / \u221E")
        }
        if (subscription.expire > 0) {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(subscription.expire * 1000))
            add(stringResource(R.string.expires_on, date))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  \u00B7  ")
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    supportingText = { Text(hint, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value); onDismiss() },
                enabled = value.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SubscriptionDialog(onConfirm: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subscription)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.subscription_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url, name.ifBlank { null }); onDismiss() },
                enabled = url.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun QrShareDialog(profile: ServerProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = remember(profile) { ConfigShare.generateShareLink(profile) }
    val bitmap = remember(link) { ConfigShare.generateQrBitmap(link, 600) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.name.ifBlank { profile.address }) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = {
                        ConfigShare.copyToClipboard(context, profile)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.copy_link), maxLines = 1, softWrap = false)
                }
                TextButton(
                    onClick = {
                        ConfigShare.shareQrCode(context, profile)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.share), maxLines = 1, softWrap = false)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}