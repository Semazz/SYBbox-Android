package com.sybbox.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
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
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.dynamic_color),
                    summary = stringResource(R.string.dynamic_color_summary),
                    checked = state.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_connection)) {
                SettingsToggle(
                    title = stringResource(R.string.auto_connect_boot),
                    summary = stringResource(R.string.auto_connect_boot_summary),
                    checked = state.autoConnectOnBoot,
                    onCheckedChange = viewModel::setAutoConnectOnBoot,
                )
                SettingsDivider()
                SettingsChoice(
                    title = stringResource(R.string.connection_timeout),
                    options = listOf(10, 15, 30, 60),
                    selected = state.connectionTimeout,
                    onSelect = viewModel::setConnectionTimeout,
                    label = { stringResource(R.string.seconds_value, it) },
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.auto_failover),
                    summary = stringResource(R.string.auto_failover_summary),
                    checked = state.autoFailover,
                    onCheckedChange = viewModel::setAutoFailover,
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
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.bypass_local),
                    summary = stringResource(R.string.bypass_local_summary),
                    checked = state.bypassLocalNetwork,
                    onCheckedChange = viewModel::setBypassLocalNetwork,
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.block_ads),
                    summary = stringResource(R.string.block_ads_summary),
                    checked = state.blockAds,
                    onCheckedChange = viewModel::setBlockAds,
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.block_trackers),
                    summary = stringResource(R.string.block_trackers_summary),
                    checked = state.blockTrackers,
                    onCheckedChange = viewModel::setBlockTrackers,
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
                )
                SettingsDivider()
                SettingsText(
                    title = stringResource(R.string.direct_dns),
                    summary = stringResource(R.string.direct_dns_summary),
                    value = state.directDns,
                    onValueChange = viewModel::setDirectDns,
                    placeholder = "77.88.8.8",
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
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.fake_ip),
                    summary = stringResource(R.string.fake_ip_summary),
                    checked = state.enableFakeIp,
                    onCheckedChange = viewModel::setEnableFakeIp,
                )
                if (state.enableFakeIp) {
                    SettingsDivider()
                    SettingsText(
                        title = stringResource(R.string.fake_ip_range),
                        value = state.fakeIpRange,
                        onValueChange = viewModel::setFakeIpRange,
                        placeholder = "198.18.0.0/15",
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
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.tls_fragment),
                    summary = stringResource(R.string.tls_fragment_summary),
                    checked = state.fragmentEnabled,
                    onCheckedChange = viewModel::setFragmentEnabled,
                )
                if (state.fragmentEnabled) {
                    SettingsDivider()
                    SettingsChoice(
                        title = stringResource(R.string.fragment_delay),
                        options = listOf("10", "50", "100", "200", "500"),
                        selected = state.fragmentSleep,
                        onSelect = viewModel::setFragmentSleep,
                        label = { "$it ms" },
                    )
                }
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.record_fragment),
                    summary = stringResource(R.string.record_fragment_summary),
                    checked = state.recordFragment,
                    onCheckedChange = viewModel::setRecordFragment,
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.multiplex),
                    summary = stringResource(R.string.multiplex_summary),
                    checked = state.enableMux,
                    onCheckedChange = viewModel::setEnableMux,
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
                )
                SettingsDivider()
                SettingsChoice(
                    title = stringResource(R.string.tun_mtu),
                    options = listOf(1400, 1500, 4064, 9000),
                    selected = state.tunMTU,
                    onSelect = viewModel::setTunMTU,
                    label = { it.toString() },
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.auto_route),
                    summary = stringResource(R.string.auto_route_summary),
                    checked = state.autoRoute,
                    onCheckedChange = viewModel::setAutoRoute,
                )
                SettingsDivider()
                SettingsToggle(
                    title = stringResource(R.string.strict_route),
                    summary = stringResource(R.string.strict_route_summary),
                    checked = state.strictRoute,
                    onCheckedChange = viewModel::setStrictRoute,
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_subscriptions)) {
                SettingsToggle(
                    title = stringResource(R.string.sub_auto_update),
                    checked = state.subAutoUpdate,
                    onCheckedChange = viewModel::setSubAutoUpdate,
                )
                SettingsDivider()
                SettingsChoice(
                    title = stringResource(R.string.sub_interval),
                    options = listOf(1, 3, 6, 12, 24),
                    selected = state.defaultSubInterval,
                    onSelect = viewModel::setSubInterval,
                    label = { stringResource(R.string.hours_value, it) },
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.group_diagnostics)) {
                SettingsChoice(
                    title = stringResource(R.string.log_level),
                    options = listOf("error", "warn", "info", "debug", "trace"),
                    selected = state.logLevel.lowercase(),
                    onSelect = viewModel::setLogLevel,
                    label = { it.uppercase() },
                )
                SettingsDivider()
                SettingsAction(
                    title = stringResource(R.string.open_logs),
                    icon = Icons.Rounded.BugReport,
                    onClick = onOpenLogs,
                )
            }
        }

        item {
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
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
