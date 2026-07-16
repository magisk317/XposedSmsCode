package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
import io.github.magisk317.uikit.surface.MainTabScaffold
import io.github.magisk317.uikit.surface.MainTabSpec
import io.github.magisk317.uikit.surface.tabEnterTransition
import io.github.magisk317.uikit.surface.tabExitTransition
import io.github.magisk317.uikit.surface.tabTransitionDirection
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController
import org.koin.compose.viewmodel.koinViewModel

private fun resolveTabIndex(destination: NavDestination?): Int {
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

private fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
    if (destination == null) return true
    val hierarchy = destination.hierarchy
    return hierarchy.any { it.hasRoute(OverviewRoute::class) } ||
        hierarchy.any { it.hasRoute(AppBlockRoute::class) } ||
        hierarchy.any { it.hasRoute(RecordsRoute::class) } ||
        hierarchy.any { it.hasRoute(SettingsRoute::class) }
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun MainScreen(
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    onBottomOverlayPaddingChanged: (Dp) -> Unit = {},
) {
    val navController = rememberNavController()
    val appConfigViewModel: AppConfigViewModel = koinViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        MainTabSpec(stringResource(R.string.tab_overview), Icons.Default.Home),
        MainTabSpec(stringResource(R.string.tab_blacklist), Icons.Default.Widgets),
        MainTabSpec(stringResource(R.string.tab_records), Icons.Default.History),
        MainTabSpec(stringResource(R.string.tab_settings), Icons.Default.Settings),
    )
    val tabRoutes = listOf(OverviewRoute, AppBlockRoute, RecordsRoute, SettingsRoute)

    val selectedIndex = resolveTabIndex(currentDestination)
    val isCompact = rememberIsCompactWidth()
    var appBlockRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordsRefreshTrigger by remember { mutableIntStateOf(0) }
    var settingsRefreshTrigger by remember { mutableIntStateOf(0) }

    val compactBottomBarRouteAvailable = shouldShowCompactBottomBar(currentDestination)
    val allowScrollChrome = currentDestination?.let { destination ->
        destination.hasRoute(AppBlockRoute::class) ||
            destination.hasRoute(RecordsRoute::class)
    } ?: false
    val chromeController = rememberMainChromeController(
        isCompact = isCompact,
        compactChromeRouteAvailable = compactBottomBarRouteAvailable,
        keepVisible = !allowScrollChrome,
        allowScrollHide = allowScrollChrome,
        resetKey = currentDestination?.route,
    )
    val pageScrollChromeState = chromeController.pageScrollChromeState

    fun triggerRefreshForIndex(index: Int) {
        when (index) {
            1 -> appBlockRefreshTrigger++
            2 -> recordsRefreshTrigger++
            3 -> settingsRefreshTrigger++
        }
    }

    fun navigateToTab(index: Int) {
        val route = tabRoutes.getOrNull(index) ?: return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
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

    MainTabScaffold(
        tabs = tabs,
        selectedIndex = selectedIndex,
        isCompact = isCompact,
        chromeController = chromeController,
        onTabSelected = { index -> navigateToTab(index) },
        onTabReselected = { index -> triggerRefreshForIndex(index) },
        railHeader = {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = null,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        },
        onContentBottomPaddingChanged = onBottomOverlayPaddingChanged,
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = OverviewRoute,
            enterTransition = {
                tabEnterTransition(
                    tabTransitionDirection(
                        initialIndex = resolveTabIndex(initialState.destination),
                        targetIndex = resolveTabIndex(targetState.destination),
                    ),
                )
            },
            exitTransition = {
                tabExitTransition(
                    tabTransitionDirection(
                        initialIndex = resolveTabIndex(initialState.destination),
                        targetIndex = resolveTabIndex(targetState.destination),
                    ),
                )
            },
            popEnterTransition = {
                tabEnterTransition(
                    tabTransitionDirection(
                        initialIndex = resolveTabIndex(initialState.destination),
                        targetIndex = resolveTabIndex(targetState.destination),
                        isPop = true,
                    ),
                )
            },
            popExitTransition = {
                tabExitTransition(
                    tabTransitionDirection(
                        initialIndex = resolveTabIndex(initialState.destination),
                        targetIndex = resolveTabIndex(targetState.destination),
                        isPop = true,
                    ),
                )
            },
            predictivePopEnterTransition = { _ ->
                tabEnterTransition(
                    tabTransitionDirection(
                        initialIndex = resolveTabIndex(initialState.destination),
                        targetIndex = resolveTabIndex(targetState.destination),
                        isPop = true,
                    ),
                )
            },
            predictivePopExitTransition = { _ ->
                tabExitTransition(
                    tabTransitionDirection(
                        initialIndex = resolveTabIndex(initialState.destination),
                        targetIndex = resolveTabIndex(targetState.destination),
                        isPop = true,
                    ),
                )
            },
        ) {
            composable<OverviewRoute> {
                OverviewScreen()
            }
            composable<AppBlockRoute> {
                AppConfigScreen(
                    onBack = null,
                    refreshTrigger = appBlockRefreshTrigger,
                    viewModel = appConfigViewModel,
                    scrollChromeState = pageScrollChromeState,
                )
            }
            composable<AppConfigRoute> {
                AppConfigScreen(
                    onBack = { navController.popBackStack() },
                    refreshTrigger = appBlockRefreshTrigger,
                    viewModel = appConfigViewModel,
                    scrollChromeState = pageScrollChromeState,
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
                    onBack = null,
                    refreshTrigger = recordsRefreshTrigger,
                    scrollChromeState = pageScrollChromeState,
                )
            }
            composable<SettingsRoute> {
                ComposeSettingsScreen(
                    onExit = { /* In tab, ignore exit */ },
                    refreshTrigger = settingsRefreshTrigger,
                )
            }
        }
    }
}
