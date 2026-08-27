package com.sybbox.ui.routing

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
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sybbox.R
import com.sybbox.domain.model.RoutingAction
import com.sybbox.domain.model.RoutingRule
import com.sybbox.domain.model.RoutingRuleType
import com.sybbox.ui.components.EmptyState
import com.sybbox.ui.components.SectionHeader
import com.sybbox.ui.components.SettingsAction
import com.sybbox.ui.components.SybCard
import com.sybbox.ui.components.PillShape
import com.sybbox.ui.theme.SybSpacing
import com.sybbox.ui.theme.LatencyFast
import com.sybbox.ui.theme.LatencySlow

@Composable
fun RoutingScreen(
    onOpenPerApp: () -> Unit,
    viewModel: RoutingViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val perApp by viewModel.perApp.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RoutingRule?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SybSpacing.screen, end = SybSpacing.screen, bottom = SybSpacing.listBottom),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.nav_routing),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { editing = RoutingRule() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        stringResource(R.string.add_rule),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        item {
            SybCard(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(
                    title = stringResource(R.string.per_app_routing),
                    summary = stringResource(R.string.per_app_summary),
                    value = if (perApp.enabled) stringResource(R.string.apps_selected, perApp.selected.size) else null,
                    icon = Icons.Rounded.Apps,
                    onClick = onOpenPerApp,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.routing_rules)) }

        if (rules.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.AltRoute,
                    title = stringResource(R.string.no_rules_title),
                    hint = stringResource(R.string.no_rules_hint),
                )
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    RuleRow(
                        rule = rule,
                        onClick = { editing = rule },
                        onToggle = { viewModel.setRuleEnabled(rule, it) },
                        onDelete = { viewModel.deleteRule(rule) },
                    )
                }
            }
        }
    }

    editing?.let { rule ->
        RuleDialog(
            rule = rule,
            onSave = { viewModel.saveRule(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RuleRow(
    rule: RoutingRule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    SybCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = SybSpacing.cardH, top = SybSpacing.cardV, bottom = SybSpacing.cardV, end = SybSpacing.cardEndInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rule.name.ifBlank { rule.value },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionChip(rule.action)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${ruleTypeLabel(rule.type)} · ${rule.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionChip(action: RoutingAction) {
    val (labelRes, color) = when (action) {
        RoutingAction.PROXY -> R.string.action_proxy to MaterialTheme.colorScheme.primary
        RoutingAction.DIRECT -> R.string.action_direct to LatencyFast
        RoutingAction.BLOCK -> R.string.action_block to LatencySlow
        RoutingAction.DNS -> R.string.action_dns to MaterialTheme.colorScheme.tertiary
    }
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = SybSpacing.chipH, vertical = SybSpacing.chipV),
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun ruleTypeLabel(type: RoutingRuleType) = when (type) {
    RoutingRuleType.DOMAIN -> "domain"
    RoutingRuleType.DOMAIN_SUFFIX -> "domain_suffix"
    RoutingRuleType.DOMAIN_KEYWORD -> "domain_keyword"
    RoutingRuleType.IP_CIDR -> "ip_cidr"
    RoutingRuleType.GEOIP -> "geoip"
    RoutingRuleType.GEOSITE -> "geosite"
    RoutingRuleType.PROCESS_NAME -> "process_name"
    RoutingRuleType.PACKAGE_NAME -> "package_name"
    RoutingRuleType.PORT -> "port"
    RoutingRuleType.NETWORK -> "network"
}

@Composable
private fun RuleDialog(rule: RoutingRule, onSave: (RoutingRule) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(rule.name) }
    var value by remember { mutableStateOf(rule.value) }
    var type by remember { mutableStateOf(rule.type) }
    var action by remember { mutableStateOf(rule.action) }
    var typeMenu by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (rule.id == 0L) R.string.add_rule else R.string.edit_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rule_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedTextField(
                        value = ruleTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rule_type)) },
                        modifier = Modifier.fillMaxWidth().clickable { typeMenu = true },
                        enabled = false,
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { typeMenu = true })
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        RoutingRuleType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(ruleTypeLabel(option)) },
                                onClick = { type = option; typeMenu = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.rule_value)) },
                    supportingText = { Text(stringResource(R.string.rule_value_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedTextField(
                        value = stringResource(actionLabel(action)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rule_action)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { actionMenu = true })
                    DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                        RoutingAction.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(actionLabel(option))) },
                                onClick = { action = option; actionMenu = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(rule.copy(name = name.trim(), value = value.trim(), type = type, action = action))
                },
                enabled = value.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun actionLabel(action: RoutingAction) = when (action) {
    RoutingAction.PROXY -> R.string.action_proxy
    RoutingAction.DIRECT -> R.string.action_direct
    RoutingAction.BLOCK -> R.string.action_block
    RoutingAction.DNS -> R.string.action_dns
}
