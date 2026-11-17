@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.home



import androidx.compose.foundation.layout.PaddingValues

import android.annotation.SuppressLint
import android.app.Activity
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.surface.AppPullToRefresh
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.surface.SectionColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@SuppressLint("InlinedApi")
@Composable
internal fun ComposeSettingsScreenBody(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
    listPadding: PaddingValues,
    scrollModifier: Modifier,
) {
    val state = rememberSettingsBodyState(viewModel = viewModel, onExit = onExit)
    val context = state.context
    val scope = state.scope
    val lifecycleOwner = state.lifecycleOwner
    val activityOwner = state.activityOwner
    val settingsViewModel = state.settingsViewModel
    val notificationAccess = state.notificationAccess
    val supportsAccessibilityAutoInput = state.supportsAccessibilityAutoInput
    val snackbarHostState = state.snackbarHostState
    var autoInputAccessibilityEnabled by state.autoInputAccessibilityEnabled
    var settingsDataLoaded by state.settingsDataLoaded
    var manualRefreshing by state.manualRefreshing
    val showCodeNotificationEnabled = state.showCodeNotificationEnabled
    var restoreUri by state.restoreUri
    var showRestoreDialog by state.showRestoreDialog

    val pageRuntime = LocalSettingsPageRuntime.current
    val isActive = pageRuntime.isActive
    val keepDataActive = pageRuntime.keepDataActive
    val currentOnPageDataReady by rememberUpdatedState(pageRuntime.onPageDataReady)

    val themeState by produceState(
        initialValue = SettingsViewModel.ThemeState(mode = 0),
        settingsViewModel,
        keepDataActive,
    ) {
        if (!keepDataActive) return@produceState
        settingsViewModel.themeState.collect { value = it }
    }
    val themeMode = themeState.mode

    var isActivated by remember { mutableStateOf(false) }

    val refreshTriggerConsumer = remember { PageRefreshTriggerConsumer() }
    LaunchedEffect(isActive, refreshTrigger) {
        val action = refreshTriggerConsumer.consume(isActive, refreshTrigger)
            ?: return@LaunchedEffect
        when (action) {
            PageRefreshAction.FORCE_REFRESH -> state.runManualRefresh()
            PageRefreshAction.INITIAL_LOAD -> {
                if (!settingsDataLoaded) state.reloadSettingsData()
            }
            PageRefreshAction.NO_OP -> Unit
        }
    }

    LaunchedEffect(lifecycleOwner, keepDataActive) {
        if (!keepDataActive) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            isActivated = RuntimeStateStore.isModuleActivated(context)
            autoInputAccessibilityEnabled =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
            // Reconcile the notification switch with the real system delivery state:
            // if the permission (or the master switch) was revoked outside the app,
            // turn the preference back off.
            if (showCodeNotificationEnabled.value &&
                !(NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                    notificationAccess.hasPostNotificationsPermission(context))
            ) {
                showCodeNotificationEnabled.value = false
                AppPreferencesDataStore.setBoolean(
                    context,
                    PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                    false,
                )
                HookPreferenceMirror.publish(context)
            }
            delay(1000L)
            isActivated = RuntimeStateStore.isModuleActivated(context)
            autoInputAccessibilityEnabled =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
        }
    }

    LaunchedEffect(settingsViewModel, lifecycleOwner, keepDataActive) {
        if (!keepDataActive) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            settingsViewModel.eventsFlow.collect { event ->
                handleSettingsEvent(
                    event = event,
                    context = context,
                    activity = activityOwner ?: (context as? Activity),
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    onShowPrivacyPolicy = {},
                    onShowRestoreConfirm = { uri ->
                        restoreUri = uri
                        showRestoreDialog = true
                    },
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("settings") }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isActive && shouldShowInitialLoading && !settingsDataLoaded,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )

    LaunchedEffect(isActive, settingsDataLoaded, showLoading, shouldShowInitialLoading) {
        if (isActive && shouldShowInitialLoading && settingsDataLoaded && !showLoading) {
            SessionLoadingRegistry.markShown("settings")
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val cacheHit = settingsDataLoaded
        if (!cacheHit) {
            snapshotFlow { settingsDataLoaded }.first { it }
        }
        currentOnPageDataReady(cacheHit)
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(modifier = Modifier.fillMaxSize()) {
        val isCompact = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width.toDp() < 600.dp
        }
        val bottomPadding =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                if (isCompact) Const.BOTTOM_SPACE_HEIGHT.dp else 0.dp

        AppPullToRefresh(
            isRefreshing = manualRefreshing,
            onRefresh = {
                scope.launch { state.runManualRefresh() }
            },
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = listPadding.calculateTopPadding(),
            ),
        ) {
            if (showLoading && !manualRefreshing) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PolygonMorphLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = listPadding.calculateTopPadding() + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                SectionColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .then(scrollModifier),
                    contentPadding = PaddingValues(
                        top = listPadding.calculateTopPadding(),
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
                ) {

                    SettingsEntitlementSection(state)
                    SettingsGeneralSection(state)
                    SettingsSmsCodeSection(state)
                    SettingsAutoInputSection(state)
                    SettingsNotificationSection(state)
                    SettingsExperimentalSection(state)
                    SettingsOthersSection(state)

                    Spacer(modifier = Modifier.height(Const.SPACING_SMALL.dp))
                }
            }
        }

        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .padding(bottom = if (isCompact) Const.BOTTOM_SPACE_HEIGHT.dp else 0.dp)
                .navigationBarsPadding(),
        )
    }

    SettingsDialogsHost(state)
    }
}

