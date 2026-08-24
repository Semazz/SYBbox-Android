package com.sybbox.ui.routing

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sybbox.R
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.repository.RoutingRepository
import com.sybbox.domain.model.RoutingRule
import com.sybbox.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
)

data class PerAppState(
    val enabled: Boolean = false,
    val includeMode: Boolean = false,
    val selected: Set<String> = emptySet(),
)

@HiltViewModel
class RoutingViewModel @Inject constructor(
    application: Application,
    private val repository: RoutingRepository,
    private val store: SettingsDataStore,
) : AndroidViewModel(application) {

    val rules: StateFlow<List<RoutingRule>> = repository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val perApp: StateFlow<PerAppState> = combine(
        store.perAppProxy, store.includedApps, store.excludedApps,
    ) { enabled, included, excluded ->

        if (included.isNotEmpty()) {
            PerAppState(enabled, includeMode = true, selected = included.toSet())
        } else {
            PerAppState(enabled, includeMode = false, selected = excluded.toSet())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerAppState())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _loadingApps = MutableStateFlow(false)
    val loadingApps: StateFlow<Boolean> = _loadingApps.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun saveRule(rule: RoutingRule) {
        viewModelScope.launch {
            if (rule.id == 0L) repository.insertRule(rule) else repository.updateRule(rule)
        }
    }

    fun deleteRule(rule: RoutingRule) {
        viewModelScope.launch {
            repository.deleteRule(rule)
            _messages.tryEmit(UiMessage(R.string.msg_rule_deleted))
        }
    }

    fun setRuleEnabled(rule: RoutingRule, enabled: Boolean) {
        viewModelScope.launch { repository.updateRule(rule.copy(enabled = enabled)) }
    }

    fun setPerAppEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setPerAppProxy(enabled) }
    }

    fun setIncludeMode(include: Boolean) {
        viewModelScope.launch {
            val current = perApp.value.selected.toList()
            if (include) {
                store.setExcludedApps(emptyList())
                store.setIncludedApps(current)
            } else {
                store.setIncludedApps(emptyList())
                store.setExcludedApps(current)
            }
        }
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            val state = perApp.value
            val next = if (packageName in state.selected) {
                state.selected - packageName
            } else {
                state.selected + packageName
            }
            if (state.includeMode) store.setIncludedApps(next.toList())
            else store.setExcludedApps(next.toList())
        }
    }

    fun loadApps() {
        if (_apps.value.isNotEmpty() || _loadingApps.value) return
        viewModelScope.launch {
            _loadingApps.value = true
            val packageManager = getApplication<Application>().packageManager
            val self = getApplication<Application>().packageName
            val loaded = withContext(Dispatchers.IO) {
                // No GET_META_DATA: the metadata bundles are never read here and loading
                // them for every installed package is pure cost.
                packageManager.getInstalledApplications(0)
                    .asSequence()
                    .filter { it.packageName != self }
                    .map { info ->
                        InstalledApp(
                            packageName = info.packageName,
                            label = runCatching { packageManager.getApplicationLabel(info).toString() }
                                .getOrDefault(info.packageName),
                            system = info.isSystem(),
                        )
                    }
                    .sortedWith(compareBy({ it.system }, { it.label.lowercase() }))
                    .toList()
            }
            _apps.value = loaded
            _loadingApps.value = false
        }
    }

    private fun ApplicationInfo.isSystem() = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
}