@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.home




import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.magisk317.smscode.presentation.BuildConfig
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.GeneralSettingsSection
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsCallbacks
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItem
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItems
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLabels
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLayout
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsState
import io.github.magisk317.uikit.preference.RuntimeLogShareEntryMode
import io.github.magisk317.uikit.preference.StatusSettingsSection
import io.github.magisk317.smscode.runtime.common.utils.BrowserUtils
import kotlinx.coroutines.launch

@Composable
internal fun SettingsEntitlementSection(state: SettingsBodyState) {
    val context = state.context
    val entitlementUiAvailable = state.entitlementUiAvailable
                    if (entitlementUiAvailable) {
                        StatusSettingsSection(
                            title = stringResource(id = R.string.mobile_entitlement_settings_title),
                            summary = stringResource(id = R.string.mobile_entitlement_settings_summary),
                        ) {
                            context.startActivity(
                                Intent().setClassName(
                                    context,
                                    "io.github.magisk317.smscode.entitlement.MobileEntitlementActivity",
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
}

@Composable
internal fun SettingsGeneralSection(state: SettingsBodyState) {
    var expandGeneral by state.expandGeneral
    val accordionMode = state.accordionMode
    val moduleEnabled = state.moduleEnabled
    val scope = state.scope
    val context = state.context
    val settingsViewModel = state.settingsViewModel
    val launcherIconVisible = state.launcherIconVisible
    val snackbarHostState = state.snackbarHostState
    val markPrefsSaved = state.markPrefsSaved
                    GeneralSettingsSection(
                        title = stringResource(id = R.string.settings_group_general),
                        summary = stringResource(id = R.string.settings_group_general_summary),
                        expanded = expandGeneral,
                        onExpandedChange = { expandGeneral = !expandGeneral },
                        accordionMode = accordionMode.value,
                        moduleEnabled = moduleEnabled.value,
                        onModuleEnabledChange = { checked ->
                            moduleEnabled.value = checked
                            scope.launch {
                                AppPreferencesDataStore.setBoolean(
                                    context,
                                    PrefConst.KEY_ENABLE,
                                    checked,
                                )
                                HookPreferenceMirror.publish(context)
                                markPrefsSaved()
                            }
                        },
                        moduleTitle = stringResource(id = UiKitR.string.pref_enable_title),
                        moduleSummary = stringResource(id = UiKitR.string.pref_enable_summary),
                        themeMode = null,
                        onThemeSelected = null,
                    ) {
                        val navigateToThemeSettings = LocalThemeSettingsNavigation.current
                        AppArrowItem(
                            title = stringResource(id = R.string.pref_theme_details_title),
                            summary = stringResource(id = R.string.pref_theme_details_summary),
                            onClick = {
                                navigateToThemeSettings?.invoke()
                            },
                        )
                        SwitchItem(
                            title = stringResource(id = R.string.pref_settings_display_mode_title),
                            summary = stringResource(id = UiKitR.string.pref_settings_display_mode_summary),
                            key = PrefConst.KEY_SETTINGS_ACCORDION_MODE,
                            defaultValue = true,
                            stateOverride = accordionMode,
                            onSaved = markPrefsSaved,
                        )
                        SwitchItem(
                            title = stringResource(id = UiKitR.string.pref_show_launcher_icon_title),
                            summary = stringResource(id = UiKitR.string.pref_show_launcher_icon_summary),
                            key = PrefConst.KEY_SHOW_LAUNCHER_ICON,
                            defaultValue = true,
                            stateOverride = launcherIconVisible,
                            onToggle = { visible ->
                                val success = settingsViewModel.setLauncherIconVisible(visible)
                                if (!success) {
                                    launcherIconVisible.value = !visible
                                    scope.launch {
                                        AppPreferencesDataStore.setBoolean(
                                            context,
                                            PrefConst.KEY_SHOW_LAUNCHER_ICON,
                                            !visible,
                                        )
                                        HookPreferenceMirror.publish(context)
                                    }
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(UiKitR.string.pref_show_launcher_icon_failed),
                                        )
                                    }
                                }
                            },
                            onSaved = markPrefsSaved,
                        )
                    }
}

@Composable
internal fun SettingsSmsCodeSection(state: SettingsBodyState) {
    var expandSmsCode by state.expandSmsCode
    val accordionMode = state.accordionMode
    val settingsViewModel = state.settingsViewModel
    var simSlot1Remark by state.simSlot1Remark
    var simSlot2Remark by state.simSlot2Remark
    var showKeywordsDialog by state.showKeywordsDialog
    var showSmsTestDialog by state.showSmsTestDialog
    var showSimSlotRemarkDialog by state.showSimSlotRemarkDialog
    val markPrefsSaved = state.markPrefsSaved
                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_smscode),
                        summary = stringResource(id = R.string.settings_group_smscode_summary),
                        expanded = expandSmsCode,
                        onExpandedChange = { expandSmsCode = !expandSmsCode },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = UiKitR.string.pref_copy_to_clipboard_title),
                            summary = stringResource(id = UiKitR.string.pref_copy_to_clipboard_summary),
                            key = PrefConst.KEY_COPY_TO_CLIPBOARD,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        AppArrowItem(
                            title = stringResource(id = R.string.pref_smscode_keywords_title),
                            summary = stringResource(id = R.string.pref_smscode_keywords_summary),
                        ) { showKeywordsDialog = true }
                        AppArrowItem(
                            title = stringResource(id = R.string.pref_smscode_test_title),
                            summary = stringResource(id = R.string.pref_smscode_test_summary),
                        ) { showSmsTestDialog = true }
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_sim_slot1_remark_title),
                            summary = simSlotRemarkSummary(simSlot1Remark),
                        ) { showSimSlotRemarkDialog = 0 }
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_sim_slot2_remark_title),
                            summary = simSlotRemarkSummary(simSlot2Remark),
                        ) { showSimSlotRemarkDialog = 1 }
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_code_rules_title),
                            summary = stringResource(id = UiKitR.string.pref_code_rules_summary),
                        ) {
                            settingsViewModel.openSmsCodeRules()
                        }
                    }
}

@Composable
internal fun SettingsAutoInputSection(state: SettingsBodyState) {
    var expandAutoInput by state.expandAutoInput
    val accordionMode = state.accordionMode
    val supportsAccessibilityAutoInput = state.supportsAccessibilityAutoInput
    var autoInputAccessibilityEnabled by state.autoInputAccessibilityEnabled
    val scope = state.scope
    val context = state.context
    val autoInputEnabled = state.autoInputEnabled
    var autoInputDelay by state.autoInputDelay
    var autoInputInterval by state.autoInputInterval
    var showAutoInputDialog by state.showAutoInputDialog
    var showAutoInputIntervalDialog by state.showAutoInputIntervalDialog
    val markPrefsSaved = state.markPrefsSaved
    val openAccessibilitySettings = state.openAccessibilitySettings
                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_auto_input),
                        summary = stringResource(id = R.string.settings_group_auto_input_summary),
                        expanded = expandAutoInput,
                        onExpandedChange = { expandAutoInput = !expandAutoInput },
                        accordionMode = accordionMode.value,
                    ) {
                        if (supportsAccessibilityAutoInput) {
                            io.github.magisk317.uikit.preference.ActionSwitchItem(
                                title = stringResource(id = UiKitR.string.pref_auto_input_accessibility_service_title),
                                summary = stringResource(id = UiKitR.string.pref_auto_input_accessibility_service_summary),
                                checked = autoInputAccessibilityEnabled,
                                onClick = {
                                    scope.launch {
                                        val success = toggleAccessibilityServiceViaRoot(context, !autoInputAccessibilityEnabled)
                                        if (success) {
                                            autoInputAccessibilityEnabled = !autoInputAccessibilityEnabled
                                        } else {
                                            openAccessibilitySettings()
                                        }
                                    }
                                },
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        val success = toggleAccessibilityServiceViaRoot(context, isChecked)
                                        if (success) {
                                            autoInputAccessibilityEnabled = isChecked
                                        } else {
                                            openAccessibilitySettings()
                                        }
                                    }
                                },
                            )
                        }
                        SwitchItem(
                            title = stringResource(id = UiKitR.string.pref_enable_auto_input_code_title),
                            summary = stringResource(id = UiKitR.string.pref_enable_auto_input_code_summary),
                            key = PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                            defaultValue = true,
                            stateOverride = autoInputEnabled,
                            onSaved = markPrefsSaved,
                        )
                        SwitchItem(
                            title = stringResource(id = UiKitR.string.pref_enable_auto_enter_code_title),
                            summary = stringResource(id = UiKitR.string.pref_enable_auto_enter_code_summary),
                            key = PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_auto_input_code_delay_title),
                            summary = stringResource(id = UiKitR.string.pref_auto_input_code_delay_summary, autoInputDelay),
                        ) { showAutoInputDialog = true }
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_auto_input_code_interval_title),
                            summary = stringResource(
                                id = UiKitR.string.pref_auto_input_code_interval_summary,
                                autoInputInterval,
                            ),
                        ) { showAutoInputIntervalDialog = true }
                    }
}

@Composable
internal fun SettingsNotificationSection(state: SettingsBodyState) {
    var expandNotification by state.expandNotification
    val accordionMode = state.accordionMode
    val markPrefsSaved = state.markPrefsSaved
    val showCodeNotificationEnabled = state.showCodeNotificationEnabled
    val handleCodeNotificationToggle = state.handleCodeNotificationToggle
    val autoCancelNotificationEnabled = state.autoCancelNotificationEnabled
    var retentionTime by state.retentionTime
    var showRetentionDialog by state.showRetentionDialog
                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_notification),
                        summary = stringResource(id = R.string.settings_group_notification_summary),
                        expanded = expandNotification,
                        onExpandedChange = { expandNotification = !expandNotification },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = UiKitR.string.pref_show_toast_title),
                            summary = stringResource(id = UiKitR.string.pref_show_toast_summary),
                            key = PrefConst.KEY_SHOW_TOAST,
                            defaultValue = true,
                            onSaved = markPrefsSaved,
                        )
                        io.github.magisk317.uikit.preference.ActionSwitchItem(
                            title = stringResource(id = UiKitR.string.pref_show_code_notification_title),
                            summary = stringResource(id = UiKitR.string.pref_show_code_notification_summary),
                            checked = showCodeNotificationEnabled.value,
                            onClick = {
                                handleCodeNotificationToggle(!showCodeNotificationEnabled.value)
                            },
                            onCheckedChange = handleCodeNotificationToggle,
                        )
                        if (showCodeNotificationEnabled.value) {
                            SwitchItem(
                                title = stringResource(id = UiKitR.string.pref_auto_cancel_notification_title),
                                summary = stringResource(id = UiKitR.string.pref_auto_cancel_notification_summary),
                                key = PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                                defaultValue = PrefConst.AUTO_CANCEL_CODE_NOTIFICATION_DEFAULT,
                                stateOverride = autoCancelNotificationEnabled,
                                onToggle = { autoCancelNotificationEnabled.value = it },
                                onSaved = markPrefsSaved,
                            )
                            if (autoCancelNotificationEnabled.value) {
                                AppArrowItem(
                                    title = stringResource(id = UiKitR.string.pref_notification_retention_time_title),
                                    summary = notificationRetentionEntryLabel(retentionTime),
                                ) { showRetentionDialog = true }
                            }
                        }
                    }
}

@Composable
internal fun SettingsExperimentalSection(state: SettingsBodyState) {
    var expandExperimental by state.expandExperimental
    val accordionMode = state.accordionMode
    val markPrefsSaved = state.markPrefsSaved
    val context = state.context
                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_experimental),
                        summary = stringResource(id = R.string.settings_group_experimental_summary),
                        expanded = expandExperimental,
                        onExpandedChange = { expandExperimental = !expandExperimental },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = UiKitR.string.pref_block_sms_title),
                            summary = stringResource(id = UiKitR.string.pref_block_sms_summary),
                            key = PrefConst.KEY_BLOCK_SMS,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        SwitchItem(
                            title = stringResource(id = R.string.pref_mark_as_read_title),
                            summary = stringResource(id = R.string.pref_mark_as_read_summary),
                            key = PrefConst.KEY_MARK_AS_READ,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                            onToggle = { enabled ->
                                if (enabled && !isCodeMarkReadListenerEnabled(context)) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                        AppArrowItem(
                            title = stringResource(id = R.string.pref_notification_listener_title),
                            summary = stringResource(id = R.string.pref_notification_listener_summary),
                        ) {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        SwitchItem(
                            title = stringResource(id = R.string.pref_delete_sms_title),
                            summary = stringResource(id = R.string.pref_delete_sms_summary),
                            key = PrefConst.KEY_DELETE_SMS,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        SwitchItem(
                            title = stringResource(id = R.string.pref_kill_me_title),
                            summary = stringResource(id = R.string.pref_kill_me_summary),
                            key = PrefConst.KEY_KILL_ME,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                    }
}

@Composable
internal fun SettingsOthersSection(state: SettingsBodyState) {
    var expandOthers by state.expandOthers
    val accordionMode = state.accordionMode
    var showBackupDialog by state.showBackupDialog
    val backupAccess = state.backupAccess
    val restoreLauncher = state.restoreLauncher
    var runtimeLogRetentionDays by state.runtimeLogRetentionDays
    val analyticsEnabled = state.analyticsEnabled
    val allowsLogExport = state.allowsLogExport
    val saveRuntimeLogBundle = state.saveRuntimeLogBundle
    var showRuntimeLogRetentionDialog by state.showRuntimeLogRetentionDialog
    var showClearLogConfirmDialog by state.showClearLogConfirmDialog
    val scope = state.scope
    val context = state.context
    val markPrefsSaved = state.markPrefsSaved
                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_others),
                        summary = stringResource(id = R.string.settings_group_others_summary),
                        expanded = expandOthers,
                        onExpandedChange = { expandOthers = !expandOthers },
                        accordionMode = accordionMode.value,
                    ) {
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_backup_title),
                            summary = stringResource(id = UiKitR.string.pref_backup_summary),
                        ) { showBackupDialog = true }
                        val navigateToCloudBackup = LocalCloudBackupNavigation.current
                        AppArrowItem(
                            title = stringResource(id = R.string.pref_cloud_backup_title),
                            summary = stringResource(id = R.string.pref_cloud_backup_summary),
                        ) {
                            navigateToCloudBackup?.invoke()
                        }
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_restore_title),
                            summary = stringResource(id = UiKitR.string.pref_restore_summary),
                        ) {
                            val intent = backupAccess.getImportRuleListSAFIntent(context)
                            restoreLauncher.launch(intent)
                        }
                        RuntimeLogDiagnosticsItems(
                            labels = RuntimeLogDiagnosticsLabels(
                                shareLogTitle = stringResource(id = UiKitR.string.pref_share_log_title),
                                shareLogSummary = stringResource(id = UiKitR.string.pref_share_log_summary),
                                retentionTitle = stringResource(id = UiKitR.string.pref_runtime_log_retention_days_title),
                                retentionSummary = pluralStringResource(
                                    id = R.plurals.pref_runtime_log_retention_days_summary,
                                    count = runtimeLogRetentionDays,
                                    runtimeLogRetentionDays,
                                ),
                                clearLogTitle = stringResource(id = R.string.runtime_log_clear_confirm_title),
                                clearLogSummary = stringResource(id = R.string.runtime_log_clear_summary),
                            ),
                            state = RuntimeLogDiagnosticsState(
                                analyticsEnabled = analyticsEnabled.value,
                            ),
                            callbacks = RuntimeLogDiagnosticsCallbacks(
                                onShareLog = if (allowsLogExport) {
                                    { saveRuntimeLogBundle() }
                                } else {
                                    {}
                                },
                                onRetentionClick = if (allowsLogExport) {
                                    { showRuntimeLogRetentionDialog = true }
                                } else {
                                    {}
                                },
                                onClearLogClick = if (allowsLogExport) {
                                    { showClearLogConfirmDialog = true }
                                } else {
                                    {}
                                },
                                onAnalyticsEnabledChange = if (!BuildConfig.DEBUG && !BuildConfig.IS_CI_BUILD) {
                                    { enabled ->
                                        analyticsEnabled.value = enabled
                                        scope.launch {
                                            AppPreferencesDataStore.setBoolean(
                                                context,
                                                PrefConst.KEY_ENABLE_ANALYTICS,
                                                enabled,
                                            )
                                            HookPreferenceMirror.publish(context)
                                            markPrefsSaved()
                                        }
                                        MagiskOtel.configure(
                                            MagiskOtel.Config(
                                                enabled = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD || enabled,
                                                serviceName = "xposedsmscode",
                                                serviceVersion = BuildConfig.VERSION_NAME,
                                                serviceCommit = BuildConfig.COMMIT_HASH,
                                                projectId = "83955172",
                                                projectName = "XposedSmsCode",
                                                environment = if (BuildConfig.DEBUG) "debug" else if (BuildConfig.IS_CI_BUILD) "ci" else "release",
                                                deviceAttributes =
                                                    mapOf(
                                                        "device.manufacturer" to Build.MANUFACTURER,
                                                        "device.model" to Build.MODEL,
                                                        "os.name" to "android",
                                                        "os.version" to Build.VERSION.RELEASE,
                                                        "os.api_level" to Build.VERSION.SDK_INT.toString(),
                                                    ),
                                            ),
                                        )
                                    }
                                } else null,
                            ),
                            layout = RuntimeLogDiagnosticsLayout(
                                shareEntryMode = RuntimeLogShareEntryMode.SEPARATE_ITEM,
                                itemOrder = if (allowsLogExport) {
                                    listOf(
                                        RuntimeLogDiagnosticsItem.SHARE_LOG,
                                        RuntimeLogDiagnosticsItem.RETENTION,
                                        RuntimeLogDiagnosticsItem.CLEAR_LOG,
                                        RuntimeLogDiagnosticsItem.ANALYTICS,
                                    )
                                } else {
                                    listOf(RuntimeLogDiagnosticsItem.ANALYTICS)
                                },
                            ),
                            enabled = allowsLogExport,
                        )

                        UpdateSettingsSection()
                        AppArrowItem(
                            title = stringResource(id = UiKitR.string.pref_privacy_policy_title),
                            summary = "",
                        ) {
                            BrowserUtils.openWebPage(
                                context,
                                Const.PRIVACY_POLICY_URL,
                                R.string.browser_install_or_enable_prompt,
                            )
                        }
                    }
}

