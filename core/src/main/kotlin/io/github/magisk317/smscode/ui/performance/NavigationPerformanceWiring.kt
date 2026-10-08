package io.github.magisk317.smscode.ui.performance

/** Pure decisions shared by the Compose shell and its unit tests. */
internal fun resolveNavigationInput(
    pendingTargetPage: Int?,
    pendingInput: NavigationInput?,
    observedTargetPage: Int,
    isProgrammaticNavigation: Boolean,
): NavigationInput = when {
    pendingTargetPage == observedTargetPage -> pendingInput ?: NavigationInput.DEEP_LINK
    isProgrammaticNavigation -> NavigationInput.CLICK
    else -> NavigationInput.SWIPE
}

internal fun shouldStartUserSwipeTransition(
    isScrollInProgress: Boolean,
    isProgrammaticNavigation: Boolean,
    targetPage: Int,
    settledPage: Int,
): Boolean = isScrollInProgress && !isProgrammaticNavigation && targetPage != settledPage

internal fun isPageReadyForTransition(
    token: NavigationTransitionToken,
    pageRoute: String,
): Boolean = token.target == pageRoute

internal fun shouldEmitTerminalTransition(
    result: NavigationTransitionResult,
    allowMissingFirstFrame: Boolean,
): Boolean {
    if (result.status == NavigationTransitionStatus.ACTIVE) return false
    return result.status != NavigationTransitionStatus.COMPLETED ||
        allowMissingFirstFrame ||
        NavigationMilestone.FIRST_FRAME !in result.missingMilestones
}
