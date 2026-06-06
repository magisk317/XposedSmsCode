package com.github.magisk317.smscode.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.ui.nav.AppBlockRoute
import com.github.magisk317.smscode.ui.nav.AppConfigRoute
import com.github.magisk317.smscode.ui.nav.OverviewRoute
import com.github.magisk317.smscode.ui.nav.RecordsRoute
import com.github.magisk317.smscode.ui.nav.SettingsRoute
import com.github.magisk317.smscode.ui.nav.SmsCodeRuleEditorRoute
import com.github.magisk317.smscode.ui.nav.SmsCodeRulesRoute
import com.github.magisk317.smscode.ui.record.CodeRecordScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import io.github.magisk317.uikit.surface.AppBottomNavigationBar
import io.github.magisk317.uikit.surface.AppNavigationItemSpec
import io.github.magisk317.uikit.surface.AppNavigationRail
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class TabItem<T : Any>(val label: String, val icon: ImageVector, val route: T)

private const val TAB_DOUBLE_TAP_REFRESH_WINDOW_MS = 350L

@Composable
@Suppress("CyclomaticComplexMethod")
fun MainScreen(
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
) {
    val navController = rememberNavController()
    val appConfigViewModel: AppConfigViewModel = koinViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        TabItem(stringResource(R.string.tab_overview), Icons.Default.Home, OverviewRoute),
        TabItem(stringResource(R.string.tab_blacklist), Icons.Default.Widgets, AppBlockRoute),
        TabItem(stringResource(R.string.tab_records), Icons.Default.History, RecordsRoute),
        TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, SettingsRoute),
    )

    fun resolveTabIndex(destination: NavDestination?): Int {
        if (destination == null) return 0
        val hierarchy = destination.hierarchy
        return when {
            hierarchy.any { it.hasRoute(OverviewRoute::class) } -> 0
            hierarchy.any { it.hasRoute(AppBlockRoute::class) } ||
                hierarchy.any { it.hasRoute(AppConfigRoute::class) } -> 1
            hierarchy.any { it.hasRoute(RecordsRoute::class) } -> 2
            hierarchy.any { it.hasRoute(SettingsRoute::class) } ||
                hierarchy.any { it.hasRoute(SmsCodeRulesRoute::class) } ||
                hierarchy.any { it.hasRoute(SmsCodeRuleEditorRoute::class) } -> 3
            else -> 0
        }
    }

    fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
        if (destination == null) return true
        val hierarchy = destination.hierarchy
        return hierarchy.any { it.hasRoute(OverviewRoute::class) } ||
            hierarchy.any { it.hasRoute(AppBlockRoute::class) } ||
            hierarchy.any { it.hasRoute(RecordsRoute::class) } ||
            hierarchy.any { it.hasRoute(SettingsRoute::class) }
    }

    val selectedIndex = resolveTabIndex(currentDestination)

    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    var appBlockRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordsRefreshTrigger by remember { mutableIntStateOf(0) }
    var settingsRefreshTrigger by remember { mutableIntStateOf(0) }
    val tabLastTapAt = remember { mutableStateMapOf<String, Long>() }

    fun triggerRefreshForTab(route: Any) {
        when (route) {
            is AppBlockRoute -> appBlockRefreshTrigger++
            is RecordsRoute -> recordsRefreshTrigger++
            is AppConfigRoute -> appBlockRefreshTrigger++
            is SettingsRoute -> settingsRefreshTrigger++
            else -> Unit
        }
    }

    fun handleTabClick(tab: TabItem<*>, selected: Boolean) {
        val key = tab.route::class.qualifiedName ?: tab.label
        val now = SystemClock.elapsedRealtime()
        val last = tabLastTapAt[key] ?: 0L
        tabLastTapAt[key] = now

        if (selected) {
            if (now - last <= TAB_DOUBLE_TAP_REFRESH_WINDOW_MS) {
                triggerRefreshForTab(tab.route)
            }
            return
        }

        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigationItems = tabs.mapIndexed { index, tab ->
        AppNavigationItemSpec(
            label = tab.label,
            icon = tab.icon,
            selected = index == selectedIndex,
            onClick = { handleTabClick(tab, index == selectedIndex) },
        )
    }

    LaunchedEffect(initialTab) {
        when (initialTab) {
            is OverviewRoute -> navController.navigate(OverviewRoute)
            is AppBlockRoute -> navController.navigate(AppBlockRoute)
            is AppConfigRoute -> navController.navigate(AppConfigRoute)
            is RecordsRoute -> navController.navigate(RecordsRoute)
            is SettingsRoute -> navController.navigate(SettingsRoute)
            is SmsCodeRulesRoute -> navController.navigate(initialTab)
            is SmsCodeRuleEditorRoute -> navController.navigate(initialTab)
            else -> Unit
        }
        if (initialTab != null) {
            onInitialTabConsumed?.invoke()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isCompact) {
                AppNavigationRail(
                    items = navigationItems,
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    },
                    alwaysShowLabel = false,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = OverviewRoute,
                    enterTransition = {
                        val initialIndex = resolveTabIndex(initialState.destination)
                        val targetIndex = resolveTabIndex(targetState.destination)
                        val direction = if (targetIndex >= initialIndex) 1 else -1

                        slideInHorizontally(
                            animationSpec = tween(300),
                            initialOffsetX = { fullWidth -> direction * fullWidth },
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        val initialIndex = resolveTabIndex(initialState.destination)
                        val targetIndex = resolveTabIndex(targetState.destination)
                        val direction = if (targetIndex >= initialIndex) 1 else -1

                        slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { fullWidth -> -direction * fullWidth },
                        ) + fadeOut(animationSpec = tween(300))
                    },
                ) {
                    composable<OverviewRoute> {
                        OverviewScreen(hazeState = hazeState, hazeStyle = hazeStyle)
                    }
                    composable<AppBlockRoute> {
                        AppConfigScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                            onBack = null,
                            refreshTrigger = appBlockRefreshTrigger,
                            viewModel = appConfigViewModel,
                        )
                    }
                    composable<AppConfigRoute> {
                        AppConfigScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                            onBack = { navController.popBackStack() },
                            refreshTrigger = appBlockRefreshTrigger,
                            viewModel = appConfigViewModel,
                        )
                    }
                    composable<SmsCodeRulesRoute> {
                        com.github.magisk317.smscode.ui.smscoderule.SmsCodeRuleListScreen(
                            onBack = { navController.popBackStack() },
                            onAddClick = {
                                navController.navigate(SmsCodeRuleEditorRoute())
                            },
                            onEditClick = { id ->
                                navController.navigate(SmsCodeRuleEditorRoute(id = id))
                            },
                        )
                    }
                    composable<SmsCodeRuleEditorRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<SmsCodeRuleEditorRoute>()
                        com.github.magisk317.smscode.ui.smscoderule.SmsCodeRuleEditorScreen(
                            ruleId = route.id,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<RecordsRoute> {
                        CodeRecordScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                            onBack = null,
                            refreshTrigger = recordsRefreshTrigger,
                        )
                    }
                    composable<SettingsRoute> {
                        ComposeSettingsScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                            onExit = { /* In tab, ignore exit */ },
                            refreshTrigger = settingsRefreshTrigger,
                        )
                    }
                }
            }
        }

        if (isCompact && shouldShowCompactBottomBar(currentDestination)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .hazeEffect(hazeState) {
                    blurEffect { style = hazeStyle }
                        forceInvalidateOnPreDraw = true
                    }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
            ) {
                AppBottomNavigationBar(
                    items = navigationItems,
                    containerColor = Color.Transparent,
                    alwaysShowLabel = false,
                )
            }
        }
    }
}
