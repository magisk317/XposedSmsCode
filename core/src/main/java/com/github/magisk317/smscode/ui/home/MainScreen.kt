package com.github.magisk317.smscode.ui.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.navigation.NavBackStackEntry
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
import io.github.magisk317.uikit.surface.AnimatedSystemBarsScrim
import io.github.magisk317.uikit.surface.AppNavigationItemSpec
import io.github.magisk317.uikit.surface.AppNavigationRail
import io.github.magisk317.uikit.surface.AnimatedCompactBottomNavigationChrome
import io.github.magisk317.uikit.surface.TabItem
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController
import org.koin.compose.viewmodel.koinViewModel

private const val TAB_DOUBLE_TAP_REFRESH_WINDOW_MS = 350L
private const val TAB_NAV_TRANSITION_MS = 300

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

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabTransitionDirection(isPop: Boolean): Int {
    val initialIndex = resolveTabIndex(initialState.destination)
    val targetIndex = resolveTabIndex(targetState.destination)
    return when {
        targetIndex > initialIndex -> 1
        targetIndex < initialIndex -> -1
        isPop -> -1
        else -> 1
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnterTransition(
    isPop: Boolean = false,
): EnterTransition {
    val direction = tabTransitionDirection(isPop)
    return slideInHorizontally(
        animationSpec = tween(TAB_NAV_TRANSITION_MS),
        initialOffsetX = { fullWidth -> direction * fullWidth },
    ) + fadeIn(animationSpec = tween(TAB_NAV_TRANSITION_MS))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExitTransition(
    isPop: Boolean = false,
): ExitTransition {
    val direction = tabTransitionDirection(isPop)
    return slideOutHorizontally(
        animationSpec = tween(TAB_NAV_TRANSITION_MS),
        targetOffsetX = { fullWidth -> -direction * fullWidth },
    ) + fadeOut(animationSpec = tween(TAB_NAV_TRANSITION_MS))
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
        TabItem(stringResource(R.string.tab_overview), Icons.Default.Home, OverviewRoute),
        TabItem(stringResource(R.string.tab_blacklist), Icons.Default.Widgets, AppBlockRoute),
        TabItem(stringResource(R.string.tab_records), Icons.Default.History, RecordsRoute),
        TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, SettingsRoute),
    )

    val selectedIndex = resolveTabIndex(currentDestination)

    val isCompact = rememberIsCompactWidth()
    var compactBottomBarHeight by remember { mutableStateOf(0.dp) }
    var appBlockRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordsRefreshTrigger by remember { mutableIntStateOf(0) }
    var settingsRefreshTrigger by remember { mutableIntStateOf(0) }
    val tabLastTapAt = remember { mutableStateMapOf<String, Long>() }

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
    val scrollChromeState = chromeController.scrollChromeState
    val pageScrollChromeState = chromeController.pageScrollChromeState
    val compactBottomBarVisible = chromeController.compactBottomBarVisible

    val bottomOverlayPadding = if (compactBottomBarVisible) {
        compactBottomBarHeight
    } else {
        0.dp
    }

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

        scrollChromeState.animateToTop()
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

    LaunchedEffect(bottomOverlayPadding) {
        onBottomOverlayPaddingChanged(bottomOverlayPadding)
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
                    enterTransition = { tabEnterTransition() },
                    exitTransition = { tabExitTransition() },
                    popEnterTransition = { tabEnterTransition(isPop = true) },
                    popExitTransition = { tabExitTransition(isPop = true) },
                    predictivePopEnterTransition = { _ -> tabEnterTransition(isPop = true) },
                    predictivePopExitTransition = { _ -> tabExitTransition(isPop = true) },
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

        AnimatedCompactBottomNavigationChrome(
            visible = compactBottomBarVisible,
            items = navigationItems,
            onHeightChanged = { compactBottomBarHeight = it },
        )

        AnimatedSystemBarsScrim(
            visible = chromeController.mainChromeVisible,
        )
    }
}
