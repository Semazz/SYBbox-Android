package com.sybbox.ui.servers

import com.sybbox.ui.theme.SybSpacing
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
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
    val collapsedGroups by viewModel.collapsedGroups.collectAsStateWithLifecycle()
    val expandedManual = MANUAL_GROUP !in collapsedGroups
    var showQrDialog by remember { mutableStateOf(false) }
    var qrDialogProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var manualMenu by remember { mutableStateOf(false) }
    var confirmDeleteAllManual by remember { mutableStateOf(false) }

    val manual = profiles.filter { it.subscriptionId == 0L }

    val settledAt = remember(collapsedGroups, selectedId, profiles.size, subscriptions.size) {
        SystemClock.elapsedRealtime()
    }
    fun steady(): Boolean = SystemClock.elapsedRealtime() - settledAt >= MISCLICK_GUARD_MS

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
        contentPadding = PaddingValues(start = SybSpacing.screen, end = SybSpacing.screen, bottom = SybSpacing.listBottom),
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
                Spacer(Modifier.height(SybSpacing.tight))
                SybCard(modifier = Modifier.fillMaxWidth(), onClick = { if (steady()) viewModel.toggleGroup(MANUAL_GROUP) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = SybSpacing.cardH, top = SybSpacing.cardV, bottom = SybSpacing.cardV, end = SybSpacing.cardEndInset),
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
                                val chosen = manual.firstOrNull { it.id == selectedId }
                                if (chosen != null && !expandedManual) {
                                    Spacer(Modifier.height(SybSpacing.labelGap))
                                    Text(
                                        chosen.displayName(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { if (steady()) viewModel.toggleGroup(MANUAL_GROUP) },
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
                            Spacer(Modifier.width(SybSpacing.tight))
                            Box {
                                IconButton(
                                    onClick = { manualMenu = true },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
                                        stringResource(R.string.cd_more),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                DropdownMenu(expanded = manualMenu, onDismissRequest = { manualMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ping_all)) },
                                        leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                                        onClick = {
                                            manualMenu = false
                                            viewModel.expandGroup(MANUAL_GROUP)
                                            viewModel.measureAll(manual)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_all_servers)) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = { manualMenu = false; confirmDeleteAllManual = true },
                                    )
                                }
                            }
                        }
                    }
                if (expandedManual && manual.isNotEmpty()) Spacer(Modifier.height(SybSpacing.small))
            }
            val manualVisible = if (expandedManual) manual else emptyList()
            items(manualVisible, key = { "m-${it.id}" }) { profile ->
                val showLatency = nowTick < (pingVisibleUntil[profile.id] ?: 0L)
                Box(modifier = Modifier.padding(bottom = SybSpacing.small)) {
                    ServerRow(
                        profile = profile,
                        selected = profile.id == selectedId,
                        latency = if (showLatency) latencies[profile.id] ?: profile.lastLatency else 0,
                        testing = profile.id in testing,
                        onSelect = { if (steady()) viewModel.select(profile.id) },
                        onPing = { viewModel.measureLatency(profile) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onCopied = { viewModel.notifyCopied() },
                        onShareQr = { qrDialogProfile = profile; showQrDialog = true },
                    )
                }
            }
        }

        subscriptions.forEach { subscription ->
            val members = profiles.filter { it.subscriptionId == subscription.id }
            val groupKey = "sub-${subscription.id}"
            val isExpanded = groupKey !in collapsedGroups
            val visibleMembers = if (isExpanded) members else emptyList()
            item(key = "sub-${subscription.id}") {

                Spacer(Modifier.height(SybSpacing.medium))
                SubscriptionHeader(
                    subscription = subscription,
                    count = members.size,
                    expanded = isExpanded,
                    refreshing = subscription.id in refreshing,
                    showPing = true,
                    selectedName = members.firstOrNull { it.id == selectedId }?.displayName(),
                    onToggle = {
                        if (steady()) viewModel.toggleGroup(groupKey)
                    },
                    onRefresh = { viewModel.refreshSubscription(subscription) },
                    onPingAll = {
                        viewModel.expandGroup(groupKey)
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

                if (visibleMembers.isNotEmpty()) Spacer(Modifier.height(SybSpacing.small))
            }
            items(visibleMembers, key = { "s${subscription.id}-${it.id}" }) { profile ->
                val showLatency = nowTick < (pingVisibleUntil[profile.id] ?: 0L)
                Box(modifier = Modifier.padding(bottom = SybSpacing.small)) {
                    ServerRow(
                        profile = profile,
                        selected = profile.id == selectedId,
                        latency = if (showLatency) latencies[profile.id] ?: profile.lastLatency else 0,
                        testing = profile.id in testing,
                        onSelect = { if (steady()) viewModel.select(profile.id) },
                        onPing = { viewModel.measureLatency(profile) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onCopied = { viewModel.notifyCopied() },
                        onShareQr = { qrDialogProfile = profile; showQrDialog = true },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(SybSpacing.small)) }
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

    if (confirmDeleteAllManual) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAllManual = false },
            title = { Text(stringResource(R.string.delete_all_servers)) },
            text = { Text(stringResource(R.string.delete_all_servers_confirm, manual.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAllManual = false
                    viewModel.deleteAllManualProfiles()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAllManual = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private const val MISCLICK_GUARD_MS = 400L

private const val MANUAL_GROUP = "manual"

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
            modifier = Modifier.fillMaxWidth().padding(start = SybSpacing.cardH, top = SybSpacing.cardV, bottom = SybSpacing.cardV, end = SybSpacing.cardEndInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pColor = protocolColor(profile.protocol)
            val originalName = profile.displayName()
            val cleanName = remember(profile.name) { com.sybbox.ui.components.stripFlagEmoji(originalName) }
            val isAuto = remember(originalName) { originalName.contains("автоматический", ignoreCase = true) || originalName.contains("auto", ignoreCase = true) }
            val code = remember(profile.name, profile.address, isAuto) {
                if (isAuto) null else com.sybbox.ui.components.countryCodeForProfile(originalName, profile.address)
            }
            val ctx = LocalContext.current
            val flagRes = remember(code, ctx) {
                if (code == null) 0 else ctx.resources.getIdentifier("flag_$code", "drawable", ctx.packageName)
            }
            when {
                isAuto -> IconTile(
                    Icons.Rounded.Bolt,
                    tint = pColor,
                    container = pColor.copy(alpha = 0.14f),
                )
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
            Spacer(Modifier.width(SybSpacing.iconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    cleanName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(SybSpacing.labelGap))
                val infoLine = remember(profile.protocol, profile.transport, profile.security) {
                    profile.subInfoLine()
                }
                Text(
                    infoLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            LatencyBadge(latency, testing)
            Spacer(Modifier.width(2.dp))
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
    selectedName: String?,
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
            Column(modifier = Modifier.fillMaxWidth().padding(start = SybSpacing.cardH, top = SybSpacing.cardV, bottom = SybSpacing.cardV, end = SybSpacing.cardEndInset)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.CloudSync)
                    Spacer(Modifier.width(SybSpacing.iconGap))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            subscription.name.ifBlank { stringResource(R.string.subscriptions) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selectedName != null && !expanded) {
                            Spacer(Modifier.height(SybSpacing.labelGap))
                            Text(
                                selectedName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
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
                    Spacer(Modifier.width(SybSpacing.tight))
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
                            if (showPing) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ping_all)) },
                                    leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                                    onClick = { menu = false; onPingAll() },
                                )
                            }
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
                        lineHeight = 15.sp,
                        maxLines = 3,
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
        if (subscription.updateInterval > 0) {
            add(stringResource(R.string.updates_every, subscription.updateInterval))
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
                Spacer(Modifier.height(SybSpacing.small))
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
                Spacer(Modifier.height(SybSpacing.medium))
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
                    Spacer(Modifier.width(SybSpacing.tight))
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
                    Spacer(Modifier.width(SybSpacing.tight))
                    Text(stringResource(R.string.share), maxLines = 1, softWrap = false)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
