package com.sybbox.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.BuildConfig
import com.sybbox.R
import com.sybbox.data.remote.ReleaseCheck
import com.sybbox.ui.components.SettingsAction
import com.sybbox.ui.components.SettingsChoice
import com.sybbox.ui.components.SettingsDivider
import com.sybbox.ui.components.SettingsGroup
import com.sybbox.ui.components.SettingsText
import com.sybbox.ui.components.SettingsToggle
import com.sybbox.ui.components.SybCard
import com.sybbox.ui.theme.LocaleHelper
import com.sybbox.ui.theme.SybSpacing
import com.sybbox.ui.theme.THEME_DARK
import com.sybbox.ui.theme.THEME_LIGHT
import com.sybbox.ui.theme.THEME_SYSTEM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    onOpenPerApp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(section.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = SybSpacing.screen,
                end = SybSpacing.screen,
                top = SybSpacing.small,
                bottom = SybSpacing.listBottom,
            ),
        ) {
            item {
                SybCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = SybSpacing.small)) {
                        when (section) {
                            SettingsSection.APPEARANCE -> AppearanceSettings(state, viewModel)
                            SettingsSection.CONNECTION -> ConnectionSettings(state, viewModel)
                            SettingsSection.ROUTING -> RoutingSettings(state, viewModel)
                            SettingsSection.DNS -> DnsSettings(state, viewModel)
                            SettingsSection.TRANSPORT -> TransportSettings(state, viewModel)
                            SettingsSection.TUNNEL -> TunnelSettings(state, viewModel)
                            SettingsSection.LOCAL_PROXY -> LocalProxySettings(state, viewModel)
                            SettingsSection.SUBSCRIPTIONS -> SubscriptionSettings(state, viewModel)
                            SettingsSection.STARTUP -> StartupSettings(state, viewModel)
                            SettingsSection.DIAGNOSTICS -> DiagnosticsSettings(state, viewModel)
                            SettingsSection.MAINTENANCE -> MaintenanceSettings(state, viewModel)
                            SettingsSection.ABOUT -> AboutSettings(viewModel)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(SybSpacing.xlarge)) }
        }
    }
}

@Composable
private fun AppearanceSettings(state: SettingsState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    SettingsChoice(
        title = stringResource(R.string.language),
        options = LocaleHelper.supported,
        selected = state.language,
        onSelect = {
            viewModel.setLanguage(it)
            (context as? android.app.Activity)?.recreate()
        },
        label = {
            when (it) {
                "EN" -> stringResource(R.string.language_en)
                "RU" -> stringResource(R.string.language_ru)
                "ES" -> stringResource(R.string.language_es)
                "ZH" -> stringResource(R.string.language_zh)
                else -> stringResource(R.string.language_system)
            }
        },
        icon = Icons.Rounded.Translate,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.theme),
        options = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK),
        selected = state.themeMode,
        onSelect = viewModel::setThemeMode,
        label = {
            stringResource(
                when (it) {
                    THEME_LIGHT -> R.string.theme_light
                    THEME_DARK -> R.string.theme_dark
                    else -> R.string.theme_system
                },
            )
        },
        icon = Icons.Rounded.DarkMode,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.dynamic_color),
        summary = stringResource(R.string.dynamic_color_summary),
        checked = state.dynamicColor,
        onCheckedChange = viewModel::setDynamicColor,
        icon = Icons.Rounded.Palette,
    )
}

@Composable
private fun ConnectionSettings(state: SettingsState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    SettingsToggle(
        title = stringResource(R.string.tcp_fast_open),
        summary = stringResource(R.string.tcp_fast_open_summary),
        checked = state.tcpFastOpen,
        onCheckedChange = viewModel::setTcpFastOpen,
        icon = Icons.Rounded.Bolt,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.auto_connect_boot),
        summary = stringResource(R.string.auto_connect_boot_summary),
        checked = state.autoConnectOnBoot,
        onCheckedChange = viewModel::setAutoConnectOnBoot,
        icon = Icons.Rounded.PowerSettingsNew,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.connection_timeout),
        options = listOf(10, 15, 30, 60),
        selected = state.connectionTimeout,
        onSelect = viewModel::setConnectionTimeout,
        label = { stringResource(R.string.seconds_value, it) },
        icon = Icons.Rounded.Timer,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.auto_failover),
        summary = stringResource(R.string.auto_failover_summary),
        checked = state.autoFailover,
        onCheckedChange = viewModel::setAutoFailover,
        icon = Icons.Rounded.SwapHoriz,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.resolve_server),
        summary = stringResource(R.string.resolve_server_summary),
        checked = state.resolveServer,
        onCheckedChange = viewModel::setResolveServer,
        icon = Icons.Rounded.Dns,
    )
    SettingsDivider()
    SettingsAction(
        title = stringResource(R.string.always_on_vpn),
        summary = stringResource(R.string.always_on_vpn_summary),
        icon = Icons.Rounded.Shield,
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_VPN_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}

@Composable
private fun RoutingSettings(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsChoice(
        title = stringResource(R.string.routing_mode),
        options = listOf("BALANCED", "GLOBAL", "DIRECT_ONLY", "CUSTOM"),
        selected = state.routingMode,
        onSelect = viewModel::setRoutingMode,
        label = {
            stringResource(
                when (it) {
                    "GLOBAL" -> R.string.mode_global
                    "DIRECT_ONLY" -> R.string.mode_direct_only
                    "CUSTOM" -> R.string.mode_custom
                    else -> R.string.mode_balanced
                },
            )
        },
        icon = Icons.Rounded.AltRoute,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.bypass_local),
        summary = stringResource(R.string.bypass_local_summary),
        checked = state.bypassLocalNetwork,
        onCheckedChange = viewModel::setBypassLocalNetwork,
        icon = Icons.Rounded.Home,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.block_ads),
        summary = stringResource(R.string.block_ads_summary),
        checked = state.blockAds,
        onCheckedChange = viewModel::setBlockAds,
        icon = Icons.Rounded.Block,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.block_trackers),
        summary = stringResource(R.string.block_trackers_summary),
        checked = state.blockTrackers,
        onCheckedChange = viewModel::setBlockTrackers,
        icon = Icons.Rounded.VisibilityOff,
    )
}

@Composable
private fun DnsSettings(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsText(
        title = stringResource(R.string.remote_dns),
        summary = stringResource(R.string.remote_dns_summary),
        value = state.remoteDns,
        onValueChange = viewModel::setRemoteDns,
        placeholder = "udp://8.8.8.8",
        icon = Icons.Rounded.Public,
    )
    SettingsDivider()
    SettingsText(
        title = stringResource(R.string.direct_dns),
        summary = stringResource(R.string.direct_dns_summary),
        value = state.directDns,
        onValueChange = viewModel::setDirectDns,
        placeholder = "77.88.8.8",
        icon = Icons.Rounded.Dns,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.dns_strategy),
        options = listOf("PreferIPv4", "PreferIPv6", "IPv4Only", "IPv6Only"),
        selected = state.dnsQueryStrategy,
        onSelect = viewModel::setDnsQueryStrategy,
        label = {
            stringResource(
                when (it) {
                    "PreferIPv6" -> R.string.strategy_prefer_ipv6
                    "IPv4Only" -> R.string.strategy_ipv4_only
                    "IPv6Only" -> R.string.strategy_ipv6_only
                    else -> R.string.strategy_prefer_ipv4
                },
            )
        },
        icon = Icons.Rounded.Sort,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.fake_ip),
        summary = stringResource(R.string.fake_ip_summary),
        checked = state.enableFakeIp,
        onCheckedChange = viewModel::setEnableFakeIp,
        icon = Icons.Rounded.Layers,
    )
    if (state.enableFakeIp) {
        SettingsDivider()
        SettingsText(
            title = stringResource(R.string.fake_ip_range),
            value = state.fakeIpRange,
            onValueChange = viewModel::setFakeIpRange,
            placeholder = "198.18.0.0/15",
            icon = Icons.Rounded.Storage,
        )
    }
}

@Composable
private fun TransportSettings(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsText(
        title = stringResource(R.string.custom_sni),
        summary = stringResource(R.string.custom_sni_summary),
        value = state.customSni,
        onValueChange = viewModel::setCustomSni,
        icon = Icons.Rounded.Language,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.tls_fragment),
        summary = stringResource(R.string.tls_fragment_summary),
        checked = state.fragmentEnabled,
        onCheckedChange = viewModel::setFragmentEnabled,
        icon = Icons.Rounded.Bolt,
    )
    if (state.fragmentEnabled) {
        SettingsDivider()
        SettingsChoice(
            title = stringResource(R.string.fragment_delay),
            options = listOf("10", "50", "100", "200", "500"),
            selected = state.fragmentSleep,
            onSelect = viewModel::setFragmentSleep,
            label = { "$it ms" },
            icon = Icons.Rounded.Timer,
        )
    }
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.record_fragment),
        summary = stringResource(R.string.record_fragment_summary),
        checked = state.recordFragment,
        onCheckedChange = viewModel::setRecordFragment,
        icon = Icons.Rounded.Layers,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.fragment_packets),
        options = listOf("tlshello", "1-3", "1-2"),
        selected = state.fragmentPackets,
        onSelect = viewModel::setFragmentPackets,
        label = { it },
        icon = Icons.Rounded.Layers,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.noise),
        summary = stringResource(R.string.noise_summary),
        checked = state.noiseEnabled,
        onCheckedChange = viewModel::setNoiseEnabled,
        icon = Icons.Rounded.Layers,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.multiplex),
        summary = stringResource(R.string.multiplex_summary),
        checked = state.enableMux,
        onCheckedChange = viewModel::setEnableMux,
        icon = Icons.Rounded.MergeType,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.xudp_udp443),
        options = listOf("reject", "allow", "skip"),
        selected = state.xudpUdp443,
        onSelect = viewModel::setXudpUdp443,
        label = { it },
        icon = Icons.Rounded.Tune,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.mux_max_streams),
        options = listOf(4, 8, 16, 32),
        selected = state.muxMaxStreams,
        onSelect = viewModel::setMuxMaxStreams,
        label = { it.toString() },
        icon = Icons.Rounded.Tune,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.mux_padding),
        summary = stringResource(R.string.mux_padding_summary),
        checked = state.muxPadding,
        onCheckedChange = viewModel::setMuxPadding,
        icon = Icons.Rounded.Lock,
    )
}

@Composable
private fun TunnelSettings(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsChoice(
        title = stringResource(R.string.domain_strategy),
        options = listOf("AsIs", "IPIfNonMatch", "IPOnDemand"),
        selected = state.domainStrategy,
        onSelect = viewModel::setDomainStrategy,
        label = { it },
        icon = Icons.Rounded.SettingsEthernet,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.tun_mtu),
        options = listOf(1400, 1500, 4064, 9000),
        selected = state.tunMTU,
        onSelect = viewModel::setTunMTU,
        label = { it.toString() },
        icon = Icons.Rounded.Tune,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.auto_route),
        summary = stringResource(R.string.auto_route_summary),
        checked = state.autoRoute,
        onCheckedChange = viewModel::setAutoRoute,
        icon = Icons.Rounded.Route,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.strict_route),
        summary = stringResource(R.string.strict_route_summary),
        checked = state.strictRoute,
        onCheckedChange = viewModel::setStrictRoute,
        icon = Icons.Rounded.Lock,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.leak_protection),
        summary = stringResource(R.string.leak_protection_summary),
        checked = state.leakProtection,
        onCheckedChange = viewModel::setLeakProtection,
        icon = Icons.Rounded.Shield,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.hide_tunnel_address),
        summary = stringResource(R.string.hide_tunnel_address_summary),
        checked = state.hideTunnelAddress,
        onCheckedChange = viewModel::setHideTunnelAddress,
        icon = Icons.Rounded.Route,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.block_webrtc),
        summary = stringResource(R.string.block_webrtc_summary),
        checked = state.blockWebRtc,
        onCheckedChange = viewModel::setBlockWebRtc,
        icon = Icons.Rounded.Lock,
    )
}

@Composable
private fun LocalProxySettings(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsToggle(
        title = stringResource(R.string.local_proxy),
        summary = stringResource(R.string.local_proxy_summary),
        checked = state.localProxy,
        onCheckedChange = viewModel::setLocalProxy,
        icon = Icons.Rounded.Router,
    )
    if (state.localProxy) {
        SettingsDivider()
        SettingsText(
            title = stringResource(R.string.local_proxy_port),
            value = state.localProxyPort.toString(),
            onValueChange = { entered ->
                entered.trim().toIntOrNull()?.takeIf { it in 1024..65535 }
                    ?.let(viewModel::setLocalProxyPort)
            },
            placeholder = "10808",
            icon = Icons.Rounded.SettingsEthernet,
        )
        SettingsDivider()
        SettingsText(
            title = stringResource(R.string.proxy_user),
            summary = stringResource(R.string.proxy_auth_summary),
            value = state.localProxyUser,
            onValueChange = viewModel::setLocalProxyUser,
            icon = Icons.Rounded.PhoneAndroid,
        )
        SettingsDivider()
        SettingsText(
            title = stringResource(R.string.proxy_password),
            value = state.localProxyPassword,
            onValueChange = viewModel::setLocalProxyPassword,
            icon = Icons.Rounded.Lock,
        )
        SettingsDivider()
        SettingsToggle(
            title = stringResource(R.string.allow_lan),
            summary = stringResource(R.string.allow_lan_summary),
            checked = state.allowLan,
            onCheckedChange = viewModel::setAllowLan,
            icon = Icons.Rounded.Wifi,
        )
        if (state.allowLan) {
            SettingsDivider()
            val proxyContext = LocalContext.current
            val address = remember(state.localProxyPort) { lanAddress() }
            val shown = if (address.isBlank()) {
                stringResource(R.string.lan_address_missing)
            } else {
                "$address:${state.localProxyPort}"
            }
            SettingsAction(
                title = stringResource(R.string.lan_address),
                summary = stringResource(R.string.lan_address_summary),
                value = shown,
                icon = Icons.Rounded.Share,
                onClick = { if (address.isNotBlank()) copy(proxyContext, "proxy", shown) },
            )
        }
    }
}

private fun lanAddress(): String = runCatching {
    java.net.NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<java.net.Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
        .orEmpty()
}.getOrDefault("")

@Composable
private fun StartupSettings(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsToggle(
        title = stringResource(R.string.update_on_start),
        checked = state.updateOnStart,
        onCheckedChange = viewModel::setUpdateOnStart,
        icon = Icons.Rounded.Sync,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.connect_on_start),
        summary = stringResource(R.string.connect_on_start_summary),
        checked = state.connectOnStart,
        onCheckedChange = viewModel::setConnectOnStart,
        icon = Icons.Rounded.Bolt,
    )
}

@Composable
private fun SubscriptionSettings(state: SettingsState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val hwid by viewModel.hwid.collectAsStateWithLifecycle()
    SettingsToggle(
        title = stringResource(R.string.sub_auto_update),
        summary = stringResource(R.string.sub_auto_update_summary),
        checked = state.subAutoUpdate,
        onCheckedChange = viewModel::setSubAutoUpdate,
        icon = Icons.Rounded.Sync,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.sub_interval),
        summary = stringResource(R.string.sub_interval_summary),
        options = listOf(1, 3, 6, 12, 24),
        selected = state.defaultSubInterval,
        onSelect = viewModel::setSubInterval,
        label = { stringResource(R.string.hours_value, it) },
        icon = Icons.Rounded.Schedule,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.sub_update_notify),
        summary = stringResource(R.string.sub_update_notify_summary),
        checked = state.subUpdateNotify,
        onCheckedChange = viewModel::setSubUpdateNotify,
        icon = Icons.Rounded.Sync,
    )
}

@Composable
private fun DiagnosticsSettings(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsToggle(
        title = stringResource(R.string.tunnel_check),
        summary = stringResource(R.string.tunnel_check_summary),
        checked = state.tunnelCheck,
        onCheckedChange = viewModel::setTunnelCheck,
        icon = Icons.Rounded.Shield,
    )
    SettingsDivider()
    SettingsText(
        title = stringResource(R.string.probe_url),
        summary = stringResource(R.string.probe_url_summary),
        value = state.probeUrl,
        onValueChange = viewModel::setProbeUrl,
        placeholder = "https://www.gstatic.com/generate_204",
        icon = Icons.Rounded.Public,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.ping_timeout),
        options = listOf(2, 3, 5, 10),
        selected = state.pingTimeout,
        onSelect = viewModel::setPingTimeout,
        label = { stringResource(R.string.seconds_value, it) },
        icon = Icons.Rounded.Timer,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.log_limit),
        summary = stringResource(R.string.log_limit_summary),
        options = listOf(5, 10, 30, 50),
        selected = state.logLimitMb,
        onSelect = viewModel::setLogLimitMb,
        label = { stringResource(R.string.megabytes_value, it) },
        icon = Icons.Rounded.Storage,
    )
    SettingsDivider()
    SettingsChoice(
        title = stringResource(R.string.log_level),
        summary = stringResource(R.string.log_level_summary),
        options = listOf("error", "warn", "info", "debug", "trace"),
        selected = state.logLevel.lowercase(),
        onSelect = viewModel::setLogLevel,
        label = { it.uppercase() },
        icon = Icons.Rounded.Terminal,
    )
}

@Composable
private fun MaintenanceSettings(state: SettingsState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    SettingsAction(
        title = stringResource(R.string.check_update),
        value = when (val check = updateCheck) {
            is UpdateCheck.Checking -> stringResource(R.string.checking)
            is UpdateCheck.Available -> check.version
            else -> null
        },
        icon = Icons.Rounded.CloudDownload,
        onClick = viewModel::checkForUpdate,
    )
    SettingsDivider()
    SettingsToggle(
        title = stringResource(R.string.auto_update_check),
        summary = stringResource(R.string.auto_update_check_summary),
        checked = state.autoUpdateCheck,
        onCheckedChange = viewModel::setAutoUpdateCheck,
        icon = Icons.Rounded.Schedule,
    )
    SettingsDivider()
    SettingsAction(
        title = stringResource(R.string.reset_settings),
        summary = stringResource(R.string.reset_settings_summary),
        icon = Icons.Rounded.RestartAlt,
        onClick = { confirmReset = true },
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_settings)) },
            text = { Text(stringResource(R.string.reset_settings_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; viewModel.resetToDefaults() }) {
                    Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    when (val check = updateCheck) {
        is UpdateCheck.Available -> AlertDialog(
            onDismissRequest = viewModel::dismissUpdateCheck,
            title = { Text(stringResource(R.string.update_available)) },
            text = { Text(stringResource(R.string.update_available_body, check.version, BuildConfig.VERSION_NAME)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdateCheck()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(check.page))) }
                }) {
                    Text(stringResource(R.string.open_link))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUpdateCheck) { Text(stringResource(R.string.close)) }
            },
        )
        is UpdateCheck.UpToDate -> AlertDialog(
            onDismissRequest = viewModel::dismissUpdateCheck,
            title = { Text(stringResource(R.string.check_update)) },
            text = { Text(stringResource(R.string.update_none, BuildConfig.VERSION_NAME)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissUpdateCheck) { Text(stringResource(R.string.ok)) }
            },
        )
        is UpdateCheck.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissUpdateCheck,
            title = { Text(stringResource(R.string.check_update)) },
            text = { Text(stringResource(R.string.update_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissUpdateCheck) { Text(stringResource(R.string.ok)) }
            },
        )
        else -> {}
    }
}

@Composable
private fun AboutSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val hwid by viewModel.hwid.collectAsStateWithLifecycle()

    SettingsAction(
        title = stringResource(R.string.app_version),
        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        icon = Icons.Rounded.Bolt,
        onClick = { copy(context, "version", BuildConfig.VERSION_NAME) },
    )
    SettingsDivider()
    val coreVersion = remember { runCatching { com.sybbox.core.Core.version() }.getOrDefault("") }
    SettingsAction(
        title = stringResource(R.string.core_version),
        value = coreVersion.ifBlank { stringResource(R.string.unknown_value) },
        icon = Icons.Rounded.Memory,
        onClick = { copy(context, "core", coreVersion) },
    )
    SettingsDivider()
    SettingsAction(
        title = stringResource(R.string.android_version),
        value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        icon = Icons.Rounded.PhoneAndroid,
        onClick = {},
    )
    SettingsDivider()
    SettingsAction(
        title = stringResource(R.string.device_model),
        value = Build.MODEL,
        icon = Icons.Rounded.PhoneAndroid,
        onClick = { copy(context, "model", Build.MODEL) },
    )
    SettingsDivider()
    SettingsAction(
        title = stringResource(R.string.hwid),
        value = hwid,
        icon = Icons.Rounded.Fingerprint,
        onClick = { copy(context, "hwid", hwid) },
    )
    SettingsDivider()
    SettingsAction(
        title = stringResource(R.string.source_code),
        value = "GitHub",
        icon = Icons.Rounded.Link,
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseCheck.RELEASES_PAGE)))
            }
        },
    )
}

private fun copy(context: android.content.Context, label: String, value: String) {
    if (value.isBlank()) return
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
}
