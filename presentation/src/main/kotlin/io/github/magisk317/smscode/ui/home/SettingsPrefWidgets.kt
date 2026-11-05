@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.home


import org.koin.compose.koinInject
import androidx.compose.foundation.layout.PaddingValues
import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.smscode.presentation.BuildConfig
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleNames
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleExporter
import io.github.magisk317.smscode.runtime.RuntimeBackupImportStatus
import io.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import org.koin.compose.koinInject
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsEnvironment
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.common.utils.AppPreferences
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.permission.PermissionBridge
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.surface.AppPullToRefresh
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.GeneralSettingsSection
import io.github.magisk317.uikit.preference.NonNegativeIntegerInputDialog
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsCallbacks
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItem
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItems
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLabels
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLayout
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsState
import io.github.magisk317.uikit.preference.RuntimeLogShareEntryMode
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.surface.AppSlider
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.preference.SingleChoiceOptionDialog
import io.github.magisk317.uikit.preference.SingleChoicePositionDialog
import io.github.magisk317.uikit.preference.SingleChoiceValueDialog
import io.github.magisk317.uikit.preference.StatusSettingsSection
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.smscode.runtime.common.utils.BrowserUtils
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel


@Composable
internal fun ExpandableSettingsSection(
    title: String,
    summary: String = "",
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    accordionMode: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sectionExpanded = if (accordionMode) expanded else true

    io.github.magisk317.uikit.preference.SectionCard(
        title = title,
        summary = summary,
        accordionMode = accordionMode,
        sectionExpanded = sectionExpanded,
        onExpandedChange = onExpandedChange,
        content = content,
    )
}

internal const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "io.github.magisk317.smscode.service.AutoInputAccessibilityService"

internal fun isAutoInputAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedService = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    ).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    if (enabledServices.isBlank()) return false
    return enabledServices.split(':').any { candidate ->
        candidate.equals(expectedService, ignoreCase = true)
    }
}

@Composable
fun SwitchItem(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    modifier: Modifier = Modifier,
    stateOverride: MutableState<Boolean>? = null,
    enabled: Boolean = true,
    onItemClick: (() -> Unit)? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onSaved: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val checkedState = stateOverride ?: rememberPrefBoolean(key, defaultValue)
    val defaultSavedToast = context.getString(R.string.pref_sync_toast)

    fun toggle(checked: Boolean) {
        if (!enabled) return
        checkedState.value = checked
        scope.launch {
            AppPreferencesDataStore.setBoolean(context, key, checked)
            HookPreferenceMirror.publish(context)
            if (onSaved != null) {
                onSaved()
            } else {
                snackbarHostState.showSnackbar(defaultSavedToast)
            }
        }
        onToggle?.invoke(checked)
    }

    if (onItemClick != null) {
        io.github.magisk317.uikit.preference.ActionSwitchItem(
            title = title,
            summary = summary,
            checked = checkedState.value,
            enabled = enabled,
            modifier = modifier,
            onClick = onItemClick,
            onCheckedChange = { toggle(it) },
        )
    } else {
        io.github.magisk317.uikit.preference.StateSwitchItem(
            title = title,
            summary = summary,
            checked = checkedState.value,
            enabled = enabled,
            modifier = modifier,
            onCheckedChange = { toggle(it) },
        )
    }
}

internal val booleanPreferenceStateCache =
    io.github.magisk317.uikit.state.RetainedValueCache<String, Boolean>()

@Composable
fun rememberPrefBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val isActive = LocalSettingsPageRuntime.current.keepDataActive
    val flow = remember(context, key, defaultValue) {
        AppPreferencesDataStore.getBooleanFlow(context, key, defaultValue)
    }
    return io.github.magisk317.uikit.state.rememberRetainedFlowState(
        cache = booleanPreferenceStateCache,
        key = key,
        initialValue = defaultValue,
        isActive = isActive,
        flow = flow,
    )
}

@Composable
internal fun notificationRetentionEntryLabel(value: String): String {
    val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
    val values = stringArrayResource(id = R.array.notification_retention_time_list)
    val index = values.indexOf(value)
    if (index >= 0) {
        return entries[index]
    }
    return value.takeIf { it.isNotBlank() } ?: "0"
}

internal fun normalizeNumericInput(raw: String): String {
    val normalized = StringBuilder(raw.length)
    raw.forEach { ch ->
        when {
            ch.isWhitespace() || Character.getType(ch) == Character.FORMAT.toInt() -> Unit
            ch.digitToIntOrNull() != null -> normalized.append(ch.digitToInt())
            ch in setOf('-', '－', '﹣', '—', '–') && normalized.isEmpty() -> normalized.append('-')
            else -> normalized.append(ch)
        }
    }
    return normalized.toString()
}

internal fun parseNonNegativeLong(raw: String): Long? {
    return normalizeNumericInput(raw)
        .toLongOrNull()
        ?.takeIf { it >= 0L }
}

/**
 * Migrates legacy seconds-formatted delay values to milliseconds.
 * Stored values ≤ 60 are legacy seconds from before the unit change; convert to ms.
 */
@Composable
internal fun simSlotRemarkSummary(remark: String): String {
    return remark.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.pref_sim_slot_remark_empty)
}

@Composable
fun rememberPrefInt(key: String, defaultValue: Int): MutableIntState {
    val context = LocalContext.current
    val isActive = LocalSettingsPageRuntime.current.keepDataActive
    val state = remember { mutableIntStateOf(defaultValue) }
    LaunchedEffect(key, isActive) {
        if (!isActive) return@LaunchedEffect
        state.intValue = AppPreferencesDataStore.getInt(context, key, defaultValue)
    }
    return state
}

@Composable
fun rememberPrefFloat(key: String, defaultValue: Float): MutableFloatState {
    val context = LocalContext.current
    val isActive = LocalSettingsPageRuntime.current.keepDataActive
    val state = remember { mutableFloatStateOf(defaultValue) }
    LaunchedEffect(key, isActive) {
        if (!isActive) return@LaunchedEffect
        state.floatValue = AppPreferencesDataStore.getFloat(context, key, defaultValue)
    }
    return state
}

