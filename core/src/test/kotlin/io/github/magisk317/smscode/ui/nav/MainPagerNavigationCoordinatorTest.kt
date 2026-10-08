package io.github.magisk317.smscode.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainPagerNavigationCoordinatorTest {
    @Test
    fun `top-level and deep routes resolve to their owning pager page`() {
        assertEquals(MainTopLevelPage.OVERVIEW, mainTopLevelPageForRoute(OverviewRoute))
        assertEquals(MainTopLevelPage.APP_BLOCK, mainTopLevelPageForRoute(AppBlockRoute))
        assertEquals(MainTopLevelPage.APP_BLOCK, mainTopLevelPageForRoute(AppConfigRoute))
        assertEquals(MainTopLevelPage.RECORDS, mainTopLevelPageForRoute(RecordsRoute))
        assertEquals(MainTopLevelPage.SETTINGS, mainTopLevelPageForRoute(SettingsRoute))
        assertEquals(MainTopLevelPage.SETTINGS, mainTopLevelPageForRoute(SmsCodeRulesRoute()))
        assertEquals(MainTopLevelPage.SETTINGS, mainTopLevelPageForRoute(SmsCodeRuleEditorRoute(7)))
        assertEquals(MainTopLevelPage.SETTINGS, mainTopLevelPageForRoute(SmsCodeRuleSourceRoute))
        assertNull(mainTopLevelPageForRoute(Any()))
    }

    @Test
    fun `route restoration emits one pager command without a feedback loop`() {
        val synchronizer = MainRoutePagerSynchronizer()

        assertEquals(
            MainTopLevelPage.SETTINGS.index,
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
        assertNull(
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.SETTINGS.index,
                isNavigating = false,
            ),
        )
        assertTrue(
            synchronizer.isReconciled(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.SETTINGS.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `route command waits for an existing pager animation to settle`() {
        val synchronizer = MainRoutePagerSynchronizer()

        assertNull(
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.RECORDS,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = true,
            ),
        )
        assertEquals(
            MainTopLevelPage.RECORDS.index,
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.RECORDS,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `external route arriving during pager motion blocks the old settle and is retried`() {
        val synchronizer = MainRoutePagerSynchronizer()
        assertNull(
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = true,
            ),
        )
        assertFalse(
            synchronizer.isReconciled(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.RECORDS.index,
                isNavigating = true,
            ),
        )

        assertEquals(
            MainTopLevelPage.SETTINGS.index,
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.RECORDS.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `pager initiated animation does not claim route reconciliation early`() {
        val synchronizer = MainRoutePagerSynchronizer()
        assertNull(
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.OVERVIEW,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )

        assertNull(
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.OVERVIEW,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = true,
            ),
        )
        assertFalse(
            synchronizer.isReconciled(
                routePage = MainTopLevelPage.OVERVIEW,
                currentPage = MainTopLevelPage.RECORDS.index,
                isNavigating = true,
            ),
        )
    }

    @Test
    fun `same route generation is not reconciled until pager reaches its page`() {
        val synchronizer = MainRoutePagerSynchronizer()

        assertEquals(
            MainTopLevelPage.SETTINGS.index,
            synchronizer.targetPageFor(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
        assertFalse(
            synchronizer.isReconciled(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
        assertTrue(
            synchronizer.isReconciled(
                routePage = MainTopLevelPage.SETTINGS,
                currentPage = MainTopLevelPage.SETTINGS.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `deep route return invalidates the previous reverse publication generation`() {
        val appConfigRouteKey = "AppConfigRoute"
        val rulesRouteKey = "SmsCodeRulesRoute"
        val overviewRouteKey = "OverviewRoute"

        assertTrue(canPublishSettledPage(appConfigRouteKey, appConfigRouteKey, false, 1, 1))
        assertFalse(canPublishSettledPage(appConfigRouteKey, overviewRouteKey, false, 1, 1))
        assertTrue(canPublishSettledPage(rulesRouteKey, rulesRouteKey, false, 3, 3))
        assertFalse(canPublishSettledPage(rulesRouteKey, overviewRouteKey, false, 3, 3))
        assertTrue(canPublishSettledPage(overviewRouteKey, overviewRouteKey, false, 0, 0))
    }

    @Test
    fun `latest pager intent blocks stale settle publication`() {
        val routeKey = "OverviewRoute"

        assertFalse(canPublishSettledPage(routeKey, routeKey, true, 1, 2))
        assertFalse(canPublishSettledPage(routeKey, routeKey, false, 1, 2))
        assertTrue(canPublishSettledPage(routeKey, routeKey, false, 2, 2))
    }

    @Test
    fun `interaction follows settled page while activated data stays warm`() {
        val settled = MainPagerPageState(page = 2, settledPage = 2, hasActivated = true)
        val current = MainPagerPageState(page = 1, settledPage = 2, hasActivated = true)
        val adjacent = MainPagerPageState(page = 0, settledPage = 2)

        assertTrue(settled.isActive)
        assertTrue(settled.isDataActive)
        assertTrue(settled.ownsSharedChrome)
        assertFalse(current.isActive)
        assertTrue(current.isDataActive)
        assertFalse(current.ownsSharedChrome)
        assertFalse(adjacent.isActive)
        assertFalse(adjacent.isDataActive)
        assertFalse(adjacent.ownsSharedChrome)
    }

    @Test
    fun `activation tracker latches pages after first settled visit`() {
        val tracker = MainPagerActivationTracker(pageCount = 4)

        assertFalse(tracker.observe(page = 2, isActive = false))
        assertTrue(tracker.observe(page = 2, isActive = true))
        assertTrue(tracker.observe(page = 2, isActive = false))
        assertFalse(tracker.observe(page = -1, isActive = true))
        assertFalse(tracker.observe(page = 4, isActive = true))
    }

    @Test
    fun `records gesture lock disables user swipes until released`() {
        val arbitrator = RecordSwipeGestureArbitrator()

        assertTrue(arbitrator.pagerUserScrollEnabled)
        arbitrator.onRecordSwipeGestureActiveChanged(true)
        assertTrue(arbitrator.isRecordSwipeGestureActive)
        assertFalse(arbitrator.pagerUserScrollEnabled)

        arbitrator.release()
        assertFalse(arbitrator.isRecordSwipeGestureActive)
        assertTrue(arbitrator.pagerUserScrollEnabled)
    }
}
