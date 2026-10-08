package io.github.magisk317.smscode.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class MainTopLevelPage(val index: Int) {
    OVERVIEW(0),
    APP_BLOCK(1),
    RECORDS(2),
    SETTINGS(3),
}

/** Resolves top-level and deep routes to the pager page that owns their navigation branch. */
internal fun mainTopLevelPageForRoute(route: Any?): MainTopLevelPage? = when (route) {
    is OverviewRoute -> MainTopLevelPage.OVERVIEW
    is AppBlockRoute,
    is AppConfigRoute -> MainTopLevelPage.APP_BLOCK
    is RecordsRoute -> MainTopLevelPage.RECORDS
    is SettingsRoute,
    is SmsCodeRulesRoute,
    is SmsCodeRuleEditorRoute,
    is SmsCodeRuleSourceRoute -> MainTopLevelPage.SETTINGS
    else -> null
}

/** Prevents a settle from an older route generation from overwriting a newly arrived route. */
internal fun canPublishSettledPage(
    reconciledRouteKey: String?,
    currentRouteKey: String?,
    isNavigating: Boolean,
    settledPage: Int,
    selectedPage: Int,
): Boolean = !isNavigating &&
    settledPage == selectedPage &&
    currentRouteKey != null &&
    reconciledRouteKey == currentRouteKey

/**
 * Reconciles route state into pager state without allowing the reverse pager-to-route bridge to
 * pull an in-flight route restoration back to the pager's previous page.
 */
internal class MainRoutePagerSynchronizer {
    private var lastEffectivePage: MainTopLevelPage? = null
    private var pendingRoutePage: MainTopLevelPage? = null

    fun targetPageFor(
        routePage: MainTopLevelPage?,
        currentPage: Int,
        isNavigating: Boolean,
    ): Int? {
        routePage ?: return null
        if (routePage != lastEffectivePage) {
            lastEffectivePage = routePage
            pendingRoutePage = routePage.takeUnless { it.index == currentPage }
        }
        val pendingPage = pendingRoutePage ?: return null
        if (isNavigating) return null
        if (pendingPage.index == currentPage) {
            pendingRoutePage = null
            return null
        }
        return pendingPage.index
    }

    fun isReconciled(
        routePage: MainTopLevelPage?,
        currentPage: Int,
        isNavigating: Boolean,
    ): Boolean {
        if (isNavigating) return false
        val pendingPage = pendingRoutePage ?: return routePage?.index == currentPage
        val reconciled = routePage == pendingPage && pendingPage.index == currentPage
        if (reconciled) pendingRoutePage = null
        return reconciled
    }
}

/** Pager-derived lifecycle state for one retained top-level page. */
internal data class MainPagerPageState(
    val page: Int,
    val settledPage: Int,
    val hasActivated: Boolean = false,
) {
    /** User interaction and one-shot refresh work belong exclusively to the settled page. */
    val isActive: Boolean = page == settledPage

    /** Data subscriptions stay warm after the page has settled once. */
    val isDataActive: Boolean = isActive || hasActivated

    /**
     * Chrome ownership follows the settled page, not the animating page. During a tab
     * transition this stays stable at the old value until the animation completes, which
     * eliminates per-frame recomposition of all pages' scroll-chrome state.
     */
    val ownsSharedChrome: Boolean = page == settledPage
}

/** Latches page activation without making pager motion itself observable or stateful. */
internal class MainPagerActivationTracker(private val pageCount: Int) {
    private val activatedPages = BooleanArray(pageCount.coerceAtLeast(0))

    fun observe(page: Int, isActive: Boolean): Boolean {
        if (page !in activatedPages.indices) return false
        if (isActive) activatedPages[page] = true
        return activatedPages[page]
    }
}

/**
 * Arbitrates horizontal gestures started by the records page. The page reports gesture ownership
 * through [onRecordSwipeGestureActiveChanged]; while held, the parent pager stops accepting user
 * drags. Programmatic tab navigation remains available.
 */
@Stable
internal class RecordSwipeGestureArbitrator {
    var isRecordSwipeGestureActive by mutableStateOf(false)
        private set

    val pagerUserScrollEnabled: Boolean
        get() = !isRecordSwipeGestureActive

    fun onRecordSwipeGestureActiveChanged(active: Boolean) {
        isRecordSwipeGestureActive = active
    }

    fun release() {
        isRecordSwipeGestureActive = false
    }
}
