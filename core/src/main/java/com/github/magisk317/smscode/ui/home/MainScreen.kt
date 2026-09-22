package com.github.magisk317.smscode.ui.home

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.github.magisk317.smscode.ui.nav.MainPagerPageState
import com.github.magisk317.smscode.ui.nav.MainPagerActivationTracker
import com.github.magisk317.smscode.ui.nav.MainRoutePagerSynchronizer
import com.github.magisk317.smscode.ui.nav.MainTopLevelPage
import com.github.magisk317.smscode.ui.nav.OverviewRoute
import com.github.magisk317.smscode.ui.nav.ThemeSettingsRoute
import com.github.magisk317.smscode.ui.theme.ThemeSettingsPage
import com.github.magisk317.smscode.ui.nav.RecordSwipeGestureArbitrator
import com.github.magisk317.smscode.ui.nav.RecordsRoute
import com.github.magisk317.smscode.ui.nav.SettingsRoute
import com.github.magisk317.smscode.ui.nav.SmsCodeRuleEditorRoute
import com.github.magisk317.smscode.ui.nav.SmsCodeRuleSourceRoute
import com.github.magisk317.smscode.ui.nav.SmsCodeRulesRoute
import com.github.magisk317.smscode.ui.nav.canPublishSettledPage
import com.github.magisk317.smscode.ui.nav.mainTopLevelPageForRoute
import com.github.magisk317.smscode.ui.performance.NavigationInput
import com.github.magisk317.smscode.ui.performance.NavigationTransitionStatus
import com.github.magisk317.smscode.ui.performance.NavigationTransitionToken
import com.github.magisk317.smscode.ui.performance.TopLevelNavigationPerformanceRecorder
import com.github.magisk317.smscode.ui.performance.isPageReadyForTransition
import com.github.magisk317.smscode.ui.performance.resolveNavigationInput
import com.github.magisk317.smscode.ui.performance.shouldEmitTerminalTransition
import com.github.magisk317.smscode.ui.performance.shouldStartUserSwipeTransition
import com.github.magisk317.smscode.ui.record.CodeRecordScreen
import io.github.magisk317.uikit.pager.rememberMainPagerState
import io.github.magisk317.uikit.surface.MainTabScaffold
import io.github.magisk317.uikit.surface.MainTabSpec
import io.github.magisk317.uikit.surface.PagerTabScaffold
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController
import io.github.magisk317.uikit.surface.tabEnterTransition
import io.github.magisk317.uikit.surface.tabExitTransition
import io.github.magisk317.uikit.surface.tabPredictivePopEnterTransition
import io.github.magisk317.uikit.surface.tabPredictivePopExitTransition
import io.github.magisk317.uikit.surface.tabTransitionDirection
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import timber.log.Timber

private const val MAIN_PAGE_COUNT = 4
private const val RECORDS_PAGE_INDEX = 2
private const val NAVIGATION_EVENT_NAME = "ui.navigation"
private const val NAVIGATION_LOG_TAG = "NavigationPerf"
private const val NAVIGATION_REPORT_INTERVAL = 8
private const val MAX_EMITTED_PERFORMANCE_TOKENS = 256

private fun performanceRouteId(page: Int): String? = when (page) {
    MainTopLevelPage.OVERVIEW.index -> "overview"
    MainTopLevelPage.APP_BLOCK.index -> "app-block"
    MainTopLevelPage.RECORDS.index -> "records"
    MainTopLevelPage.SETTINGS.index -> "settings"
    else -> null
}

private fun resolveTopLevelPage(destination: NavDestination?): MainTopLevelPage? {
    if (destination == null) return null
    val hierarchy = destination.hierarchy
    return when {
        hierarchy.any { it.hasRoute(OverviewRoute::class) } -> MainTopLevelPage.OVERVIEW
        hierarchy.any { it.hasRoute(AppBlockRoute::class) } ||
            hierarchy.any { it.hasRoute(AppConfigRoute::class) } -> MainTopLevelPage.APP_BLOCK
        hierarchy.any { it.hasRoute(RecordsRoute::class) } -> MainTopLevelPage.RECORDS
        hierarchy.any { it.hasRoute(SettingsRoute::class) } ||
            hierarchy.any { it.hasRoute(SmsCodeRulesRoute::class) } ||
            hierarchy.any { it.hasRoute(SmsCodeRuleEditorRoute::class) } ||
            hierarchy.any { it.hasRoute(SmsCodeRuleSourceRoute::class) } -> MainTopLevelPage.SETTINGS
        else -> null
    }
}

private fun resolveTabIndex(destination: NavDestination?): Int =
    resolveTopLevelPage(destination)?.index ?: MainTopLevelPage.OVERVIEW.index

private fun isTopLevelDestination(destination: NavDestination?): Boolean = destination?.let {
    it.hasRoute(OverviewRoute::class) ||
        it.hasRoute(AppBlockRoute::class) ||
        it.hasRoute(RecordsRoute::class) ||
        it.hasRoute(SettingsRoute::class)
} ?: false

private fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
    if (destination == null) return true
    val hierarchy = destination.hierarchy
    return hierarchy.any { it.hasRoute(OverviewRoute::class) } ||
        hierarchy.any { it.hasRoute(AppBlockRoute::class) } ||
        hierarchy.any { it.hasRoute(RecordsRoute::class) } ||
        hierarchy.any { it.hasRoute(SettingsRoute::class) }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun MainScreen(
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    onBottomOverlayPaddingChanged: (Dp) -> Unit = {},
) {
    val navController = rememberNavController()
    val appConfigViewModel: AppConfigViewModel = koinViewModel()
    val themeViewModel: SettingsViewModel = koinViewModel()
    val chromeThemeState by themeViewModel.themeState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRouteKey = currentDestination?.route

    val tabs = listOf(
        MainTabSpec(stringResource(R.string.tab_overview), Icons.Default.Home),
        MainTabSpec(stringResource(R.string.tab_blacklist), Icons.Default.Widgets),
        MainTabSpec(stringResource(R.string.tab_records), Icons.Default.History),
        MainTabSpec(stringResource(R.string.tab_settings), Icons.Default.Settings),
    )
    val tabRoutes = listOf(OverviewRoute, AppBlockRoute, RecordsRoute, SettingsRoute)

    val isCompact = rememberIsCompactWidth()
    var appBlockRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordsRefreshTrigger by remember { mutableIntStateOf(0) }
    var settingsRefreshTrigger by remember { mutableIntStateOf(0) }

    fun triggerRefreshForIndex(index: Int) {
        when (index) {
            MainTopLevelPage.APP_BLOCK.index -> appBlockRefreshTrigger++
            MainTopLevelPage.RECORDS.index -> recordsRefreshTrigger++
            MainTopLevelPage.SETTINGS.index -> settingsRefreshTrigger++
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

    val initialPagerPage = mainTopLevelPageForRoute(initialTab)?.index
        ?: MainTopLevelPage.OVERVIEW.index
    val pagerState = rememberMainPagerState(
        pageCount = { MAIN_PAGE_COUNT },
        initialPage = initialPagerPage,
    )
    val routePagerSynchronizer = remember { MainRoutePagerSynchronizer() }
    val pageActivationTracker = remember { MainPagerActivationTracker(MAIN_PAGE_COUNT) }
    val recordSwipeGestureArbitrator = remember { RecordSwipeGestureArbitrator() }
    val onRecordSwipeGestureActiveChanged = remember(recordSwipeGestureArbitrator) {
        recordSwipeGestureArbitrator::onRecordSwipeGestureActiveChanged
    }
    val performanceRecorder = remember { TopLevelNavigationPerformanceRecorder() }
    val emittedPerformanceTokens = remember { LinkedHashSet<String>() }
    val emittedPerformanceCount = remember { intArrayOf(0) }
    val performanceReportScope = rememberCoroutineScope()
    var activePerformanceToken by remember { mutableStateOf<NavigationTransitionToken?>(null) }
    var pendingPerformanceInput by remember { mutableStateOf<NavigationInput?>(null) }
    var pendingPerformanceTargetPage by remember { mutableStateOf<Int?>(null) }
    var reconciledRouteKey by remember { mutableStateOf<String?>(null) }
    val isTopLevelRoute = isTopLevelDestination(currentDestination)
    val routePage = resolveTopLevelPage(currentDestination)

    fun emitTerminalPerformance(
        token: NavigationTransitionToken,
        allowMissingFirstFrame: Boolean = false,
    ) {
        val result = performanceRecorder.result(token) ?: return
        if (!shouldEmitTerminalTransition(result, allowMissingFirstFrame)) return
        if (emittedPerformanceTokens.size >= MAX_EMITTED_PERFORMANCE_TOKENS) {
            emittedPerformanceTokens.iterator().let { iterator ->
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        if (!emittedPerformanceTokens.add(token.id)) return
        // Dispatch telemetry off the main thread to avoid adding to per-frame cost.
        performanceReportScope.launch(Dispatchers.Default) {
            Timber.tag(NAVIGATION_LOG_TAG).i(result.toLogLine())
            MagiskOtel.event(
                name = NAVIGATION_EVENT_NAME,
                attributes = result.toTelemetryAttributes(),
                statusOk = result.status == NavigationTransitionStatus.COMPLETED,
            )
        }
        emittedPerformanceCount[0]++
        if (emittedPerformanceCount[0] % NAVIGATION_REPORT_INTERVAL == 0) {
            performanceReportScope.launch(Dispatchers.Default) {
                Timber.tag(NAVIGATION_LOG_TAG).i(performanceRecorder.report().toLogLine())
            }
        }
    }

    fun startPerformanceTransition(sourcePage: Int, targetPage: Int, input: NavigationInput) {
        if (sourcePage == targetPage) return
        val sourceRoute = performanceRouteId(sourcePage) ?: return
        val targetRoute = performanceRouteId(targetPage) ?: return
        val previousToken = activePerformanceToken
        val previousResult = previousToken?.let(performanceRecorder::result)
        if (previousResult != null &&
            previousResult.status != NavigationTransitionStatus.CANCELLED &&
            previousToken.target == targetRoute
        ) {
            return
        }
        activePerformanceToken = performanceRecorder.start(sourceRoute, targetRoute, input)
        previousToken?.let { emitTerminalPerformance(it, allowMissingFirstFrame = true) }
    }

    fun recordPageDataReady(page: Int, cacheHit: Boolean) {
        val pageRoute = performanceRouteId(page) ?: return
        activePerformanceToken?.let { token ->
            if (!isPageReadyForTransition(token, pageRoute)) return@let
            if (cacheHit) performanceRecorder.cacheHit(token)
            if (performanceRecorder.dataReady(token)) {
                emitTerminalPerformance(token)
            }
        }
    }

    LaunchedEffect(initialTab) {
        mainTopLevelPageForRoute(initialTab)?.let { targetPage ->
            startPerformanceTransition(
                sourcePage = pagerState.pagerState.settledPage,
                targetPage = targetPage.index,
                input = NavigationInput.DEEP_LINK,
            )
        }
        when (initialTab) {
            is OverviewRoute -> navController.navigate(OverviewRoute)
            is AppBlockRoute -> navController.navigate(AppBlockRoute)
            is AppConfigRoute -> navController.navigate(AppConfigRoute)
            is RecordsRoute -> navController.navigate(RecordsRoute)
            is SettingsRoute -> navController.navigate(SettingsRoute)
            is SmsCodeRulesRoute -> navController.navigate(initialTab)
            is SmsCodeRuleEditorRoute -> navController.navigate(initialTab)
            is SmsCodeRuleSourceRoute -> navController.navigate(SmsCodeRuleSourceRoute)
            else -> Unit
        }
        if (initialTab != null) {
            onInitialTabConsumed?.invoke()
        }
    }

    val pagerScrollInProgress = pagerState.pagerState.isScrollInProgress
    val pagerTargetPage = pagerState.pagerState.targetPage
    val pagerSettledPage = pagerState.pagerState.settledPage
    LaunchedEffect(
        pagerScrollInProgress,
        pagerTargetPage,
        pagerSettledPage,
        pagerState.isNavigating,
    ) {
        if (shouldStartUserSwipeTransition(
                isScrollInProgress = pagerScrollInProgress,
                isProgrammaticNavigation = pagerState.isNavigating,
                targetPage = pagerTargetPage,
                settledPage = pagerSettledPage,
            )
        ) {
            startPerformanceTransition(
                sourcePage = pagerSettledPage,
                targetPage = pagerTargetPage,
                input = NavigationInput.SWIPE,
            )
        }
    }
    LaunchedEffect(pagerState.pagerState.currentPage) {
        pagerState.syncPage()
    }
    LaunchedEffect(activePerformanceToken) {
        val token = activePerformanceToken ?: return@LaunchedEffect
        withFrameNanos { }
        if (performanceRecorder.firstFrame(token)) {
            emitTerminalPerformance(token)
        }
    }
    LaunchedEffect(pagerState.pagerState.settledPage, activePerformanceToken) {
        val token = activePerformanceToken ?: return@LaunchedEffect
        val settledRoute = performanceRouteId(pagerState.pagerState.settledPage)
        if (settledRoute == token.target && performanceRecorder.settled(token)) {
            emitTerminalPerformance(token)
        }
    }
    val currentPerformanceToken by rememberUpdatedState(activePerformanceToken)
    DisposableEffect(performanceRecorder) {
        onDispose {
            currentPerformanceToken?.let { token ->
                if (performanceRecorder.cancel(token)) {
                    emitTerminalPerformance(token)
                } else {
                    emitTerminalPerformance(token, allowMissingFirstFrame = true)
                }
            }
        }
    }
    // A route-key generation prevents an old settled page from overwriting a newly arrived route
    // before route -> pager reconciliation has observed it.
    LaunchedEffect(
        pagerState.pagerState.settledPage,
        pagerState.selectedPage,
        pagerState.isNavigating,
        isTopLevelRoute,
        reconciledRouteKey,
        currentRouteKey,
    ) {
        val settledPage = pagerState.pagerState.settledPage
        if (!isTopLevelRoute ||
            !canPublishSettledPage(
                reconciledRouteKey = reconciledRouteKey,
                currentRouteKey = currentRouteKey,
                isNavigating = pagerState.isNavigating,
                settledPage = settledPage,
                selectedPage = pagerState.selectedPage,
            )
        ) {
            return@LaunchedEffect
        }
        if (routePage?.index == settledPage) return@LaunchedEffect
        navigateToTab(settledPage)
    }
    LaunchedEffect(currentRouteKey, pagerState.isNavigating) {
        val targetPage = routePagerSynchronizer.targetPageFor(
            routePage = routePage,
            currentPage = pagerState.pagerState.currentPage,
            isNavigating = pagerState.isNavigating,
        )
        if (targetPage != null) {
            if (pendingPerformanceTargetPage != targetPage) {
                pendingPerformanceInput = NavigationInput.DEEP_LINK
                pendingPerformanceTargetPage = targetPage
            }
            reconciledRouteKey = null
            pagerState.animateToPage(targetPage)
            return@LaunchedEffect
        }
        val isReconciled = routePagerSynchronizer.isReconciled(
            routePage = routePage,
            currentPage = pagerState.pagerState.currentPage,
            isNavigating = pagerState.isNavigating,
        )
        reconciledRouteKey = currentRouteKey.takeIf { isReconciled }
        if (isReconciled && pendingPerformanceTargetPage == pagerState.pagerState.currentPage) {
            pendingPerformanceInput = null
            pendingPerformanceTargetPage = null
        }
    }
    LaunchedEffect(pagerState.pagerState.settledPage, isTopLevelRoute) {
        if (!isTopLevelRoute || pagerState.pagerState.settledPage != RECORDS_PAGE_INDEX) {
            recordSwipeGestureArbitrator.release()
        }
    }

    val compactBottomBarRouteAvailable = shouldShowCompactBottomBar(currentDestination)
    val allowScrollChrome = currentDestination?.let { destination ->
        destination.hasRoute(AppBlockRoute::class) ||
            destination.hasRoute(AppConfigRoute::class) ||
            destination.hasRoute(RecordsRoute::class)
    } ?: false
    val chromeController = rememberMainChromeController(
        isCompact = isCompact,
        compactChromeRouteAvailable = compactBottomBarRouteAvailable,
        keepVisible = !allowScrollChrome,
        allowScrollHide = allowScrollChrome,
        resetKey = if (isTopLevelRoute) "main-tabs" else currentRouteKey,
    )
    val pageScrollChromeState = chromeController.pageScrollChromeState
    val currentBottomPaddingCallback by rememberUpdatedState(onBottomOverlayPaddingChanged)
    val lastReportedBottomPadding = remember { arrayOfNulls<Dp>(1) }

    Box(modifier = Modifier.fillMaxSize()) {
        PagerTabScaffold(
            tabs = tabs,
            pagerState = pagerState,
            isCompact = isCompact,
            showSystemBarsScrim = false,
            chromeController = chromeController,
            floatingBottomBar = chromeThemeState.floatingBottomBar,
            bottomBarBlur = chromeThemeState.bottomBarBlur,
            bottomBarBackdrop = chromeThemeState.bottomBarBackdrop,
            onTabReselected = ::triggerRefreshForIndex,
            pagerVisible = isTopLevelRoute,
                onChromeTransition = { transition ->
                    if (transition.targetPage != transition.settledPage) {
                        val hasPendingInput = pendingPerformanceTargetPage == transition.targetPage
                        val input = resolveNavigationInput(
                            pendingTargetPage = pendingPerformanceTargetPage,
                            pendingInput = pendingPerformanceInput,
                            observedTargetPage = transition.targetPage,
                            isProgrammaticNavigation = pagerState.isNavigating,
                        )
                        startPerformanceTransition(
                            sourcePage = transition.settledPage,
                            targetPage = transition.targetPage,
                            input = input,
                        )
                        if (hasPendingInput) {
                            pendingPerformanceInput = null
                            pendingPerformanceTargetPage = null
                        }
                    } else {
                        val token = activePerformanceToken
                        val settledRoute = performanceRouteId(transition.settledPage)
                        if (token != null && token.target != settledRoute && performanceRecorder.cancel(token)) {
                            emitTerminalPerformance(token)
                            activePerformanceToken = null
                        }
                    }
                },
                // Only pre-compose one neighbor on each side. Pages further away wait
                // until the pager scroll brings them into range, which cuts the per-frame
                // measure/draw surface from 4 pages to 2 during tab transitions.
                beyondViewportPageCount = 1,
                userScrollEnabled = recordSwipeGestureArbitrator.pagerUserScrollEnabled,
                reserveCompactBottomBarSpace = true,
                retainPageContentAfterFirstFrame = true,
                railHeader = {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                },
            ) { page, contentPadding ->
                val bottomPadding = contentPadding.calculateBottomPadding()
                LaunchedEffect(bottomPadding) {
                    if (lastReportedBottomPadding[0] != bottomPadding) {
                        lastReportedBottomPadding[0] = bottomPadding
                        currentBottomPaddingCallback(bottomPadding)
                    }
                }

                val pageState = MainPagerPageState(
                    page = page,
                    settledPage = pagerState.pagerState.settledPage,
                    hasActivated = pageActivationTracker.observe(
                        page = page,
                        isActive = page == pagerState.pagerState.settledPage,
                    ),
                )
                val activePageScrollChromeState = if (pageState.ownsSharedChrome) {
                    pageScrollChromeState
                } else {
                    null
                }
                when (page) {
                    MainTopLevelPage.OVERVIEW.index -> OverviewScreen(
                        isActive = pageState.isActive,
                        keepDataActive = pageState.isDataActive,
                        onPageDataReady = { cacheHit -> recordPageDataReady(page, cacheHit) },
                    )
                    MainTopLevelPage.APP_BLOCK.index -> AppConfigScreen(
                        onBack = null,
                        refreshTrigger = appBlockRefreshTrigger,
                        viewModel = appConfigViewModel,
                        scrollChromeState = activePageScrollChromeState,
                        isActive = pageState.isActive,
                        keepDataActive = pageState.isDataActive,
                        onPageDataReady = { cacheHit -> recordPageDataReady(page, cacheHit) },
                    )
                    MainTopLevelPage.RECORDS.index -> CodeRecordScreen(
                        onBack = null,
                        refreshTrigger = recordsRefreshTrigger,
                        scrollChromeState = activePageScrollChromeState,
                        isActive = pageState.isActive,
                        keepDataActive = pageState.isDataActive,
                        onPageDataReady = { cacheHit -> recordPageDataReady(page, cacheHit) },
                        onRecordSwipeGestureActiveChanged = onRecordSwipeGestureActiveChanged,
                    )
                    MainTopLevelPage.SETTINGS.index -> androidx.compose.runtime.CompositionLocalProvider(
                        LocalThemeSettingsNavigation provides { navController.navigate(ThemeSettingsRoute) },
                    ) {
                        ComposeSettingsScreen(
                            onExit = { /* In tab, ignore exit */ },
                            refreshTrigger = settingsRefreshTrigger,
                            isActive = pageState.isActive,
                            keepDataActive = pageState.isDataActive,
                            onPageDataReady = { cacheHit -> recordPageDataReady(page, cacheHit) },
                        )
                    }
                }
            }
        if (!isTopLevelRoute) {
            MainTabScaffold(
                tabs = tabs,
                selectedIndex = resolveTabIndex(currentDestination),
                isCompact = isCompact,
                showSystemBarsScrim = false,
                chromeController = chromeController,
                floatingBottomBar = chromeThemeState.floatingBottomBar,
                bottomBarBlur = chromeThemeState.bottomBarBlur,
                bottomBarBackdrop = chromeThemeState.bottomBarBackdrop,
                onTabSelected = { index ->
                    pendingPerformanceInput = NavigationInput.CLICK
                    pendingPerformanceTargetPage = index
                    navigateToTab(index)
                },
                onTabReselected = ::triggerRefreshForIndex,
                railHeader = {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                },
                onContentBottomPaddingChanged = { bottomPadding ->
                    if (lastReportedBottomPadding[0] != bottomPadding) {
                        lastReportedBottomPadding[0] = bottomPadding
                        currentBottomPaddingCallback(bottomPadding)
                    }
                },
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
                        tabPredictivePopEnterTransition()
                    },
                    predictivePopExitTransition = { swipeEdge ->
                        tabPredictivePopExitTransition(swipeEdge)
                    },
                ) {
                    composable<OverviewRoute> {
                        OverviewScreen(
                            isActive = currentDestination?.hasRoute(OverviewRoute::class) == true,
                            onPageDataReady = { cacheHit ->
                                recordPageDataReady(MainTopLevelPage.OVERVIEW.index, cacheHit)
                            },
                        )
                    }
                    composable<AppBlockRoute> {
                        AppConfigScreen(
                            onBack = null,
                            refreshTrigger = appBlockRefreshTrigger,
                            viewModel = appConfigViewModel,
                            scrollChromeState = pageScrollChromeState,
                            isActive = currentDestination?.hasRoute(AppBlockRoute::class) == true,
                            onPageDataReady = { cacheHit ->
                                recordPageDataReady(MainTopLevelPage.APP_BLOCK.index, cacheHit)
                            },
                        )
                    }
                    composable<AppConfigRoute> {
                        AppConfigScreen(
                            onBack = { navController.popBackStack() },
                            refreshTrigger = appBlockRefreshTrigger,
                            viewModel = appConfigViewModel,
                            scrollChromeState = pageScrollChromeState,
                            isActive = currentDestination?.hasRoute(AppConfigRoute::class) == true,
                            onPageDataReady = { cacheHit ->
                                recordPageDataReady(MainTopLevelPage.APP_BLOCK.index, cacheHit)
                            },
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
                            onSourceSettingsClick = {
                                navController.navigate(SmsCodeRuleSourceRoute)
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
                    composable<SmsCodeRuleSourceRoute> {
                        com.github.magisk317.smscode.ui.smscoderule.SmsCodeRuleSourceSettingsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<RecordsRoute> {
                        CodeRecordScreen(
                            onBack = null,
                            refreshTrigger = recordsRefreshTrigger,
                            scrollChromeState = pageScrollChromeState,
                            isActive = currentDestination?.hasRoute(RecordsRoute::class) == true,
                            onPageDataReady = { cacheHit ->
                                recordPageDataReady(MainTopLevelPage.RECORDS.index, cacheHit)
                            },
                            onRecordSwipeGestureActiveChanged = onRecordSwipeGestureActiveChanged,
                        )
                    }
                    composable<SettingsRoute> {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalThemeSettingsNavigation provides { navController.navigate(ThemeSettingsRoute) },
                        ) {
                            ComposeSettingsScreen(
                                onExit = { /* In tab, ignore exit */ },
                                refreshTrigger = settingsRefreshTrigger,
                                isActive = currentDestination?.hasRoute(SettingsRoute::class) == true,
                                onPageDataReady = { cacheHit ->
                                    recordPageDataReady(MainTopLevelPage.SETTINGS.index, cacheHit)
                                },
                            )
                        }
                    }
                    composable<ThemeSettingsRoute> {
                        ThemeSettingsPage(
                            // The activity-scoped instance MainScreen and MainActivity
                            // already observe; a plain koinViewModel() here would resolve to
                            // the NavBackStackEntry scope and write to an orphan instance.
                            viewModel = themeViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
