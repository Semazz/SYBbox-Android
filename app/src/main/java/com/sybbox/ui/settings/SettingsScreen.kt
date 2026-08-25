package com.sybbox.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.BuildConfig
import com.sybbox.R
import com.sybbox.core.Core
import com.sybbox.ui.components.SettingsAction
import com.sybbox.ui.components.SettingsChoice
import com.sybbox.ui.components.SettingsDivider
import com.sybbox.ui.components.SettingsGroup
import com.sybbox.ui.components.SettingsText
import com.sybbox.ui.components.SettingsToggle
import com.sybbox.ui.theme.SybSpacing
import com.sybbox.ui.theme.LocaleHelper
import com.sybbox.ui.theme.THEME_DARK
import com.sybbox.ui.theme.THEME_LIGHT
import com.sybbox.ui.theme.THEME_SYSTEM

@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit,
    onOpenPerApp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SybSpacing.screen, end = SybSpacing.screen, bottom = SybSpacing.xlarge),
    ) {
        item {
            Text(
                stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.statusBarsPadding().padding(vertical = 12.dp),
            )
        }

        item {
            SettingsGroup(stringResource(R.string.group_appearance)) {
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
        }

        item {
            SettingsGroup(stringResource(R.string.group_connection)) {
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
        }

        item {
            SettingsGroup(stringResource(R.string.group_routing)) {
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
                SettingsDivider()
                SettingsAction(
                    title = stringResource(R.string.per_app_routing),
                    summary = stringResource(R.string.per_app_summary),
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
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_dns)) {
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
        }

        item {
            SettingsGroup(stringResource(R.string.group_tls)) {
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
                SettingsToggle(
                    title = stringResource(R.string.multiplex),
                    summary = stringResource(R.string.multiplex_summary),
                    checked = state.enableMux,
                    onCheckedChange = viewModel::setEnableMux,
                    icon = Icons.Rounded.MergeType,
                )
                SettingsDivider()
                SettingsChoice(
                    title = stringResource(R.string.mux_protocol),
                    options = listOf("h2mux", "smux", "yamux"),
                    selected = state.muxProtocol,
                    onSelect = viewModel::setMuxProtocol,
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
        }

        item {
            SettingsGroup(stringResource(R.string.group_tun)) {
                SettingsChoice(
                    title = stringResource(R.string.tun_stack),
                    options = listOf("gvisor", "system", "mixed"),
                    selected = state.tunStack,
                    onSelect = viewModel::setTunStack,
                    label = {
                        stringResource(
                            when (it) {
                                "system" -> R.string.tun_stack_system
                                "mixed" -> R.string.tun_stack_mixed
                                else -> R.string.tun_stack_gvisor
                            },
                        )
                    },
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
                    title = stringResource(R.string.block_webrtc),
                    summary = stringResource(R.string.block_webrtc_summary),
                    checked = state.blockWebRtc,
                    onCheckedChange = viewModel::setBlockWebRtc,
                    icon = Icons.Rounded.Lock,
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_subscriptions)) {
                SettingsToggle(
                    title = stringResource(R.string.sub_auto_update),
                    checked = state.subAutoUpdate,
                    onCheckedChange = viewModel::setSubAutoUpdate,
                    icon = Icons.Rounded.Sync,
                )
                SettingsDivider()
                SettingsChoice(
                    title = stringResource(R.string.sub_interval),
                    options = listOf(1, 3, 6, 12, 24),
                    selected = state.defaultSubInterval,
                    onSelect = viewModel::setSubInterval,
                    label = { stringResource(R.string.hours_value, it) },
                    icon = Icons.Rounded.Schedule,
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_diagnostics)) {
                SettingsToggle(
                    title = stringResource(R.string.tunnel_check),
                    summary = stringResource(R.string.tunnel_check_summary),
                    checked = state.tunnelCheck,
                    onCheckedChange = viewModel::setTunnelCheck,
                    icon = Icons.Rounded.Shield,
                )
                SettingsDivider()
                SettingsChoice(
                    title = stringResource(R.string.log_level),
                    options = listOf("error", "warn", "info", "debug", "trace"),
                    selected = state.logLevel.lowercase(),
                    onSelect = viewModel::setLogLevel,
                    label = { it.uppercase() },
                    icon = Icons.Rounded.Terminal,
                )
                SettingsDivider()
                SettingsAction(
                    title = stringResource(R.string.open_logs),
                    icon = Icons.Rounded.Article,
                    onClick = onOpenLogs,
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text(
                    "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${stringResource(R.string.core_version)} sing-box ${runCatching { Core.version() }.getOrDefault("—")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
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
