package com.github.magisk317.smscode.ui.faq

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.DetailSectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FaqScreen(refreshTrigger: Int = 0) {
    val questions = stringArrayResource(id = R.array.question_list)
    val answers = stringArrayResource(id = R.array.answer_list)
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("faq") }
    var actualLoading by remember { mutableStateOf(shouldShowInitialLoading) }
    var manualRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val showLoading = rememberMinDurationLoading(
        actualLoading = actualLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )

    suspend fun runManualRefresh() {
        val startedAt = SystemClock.elapsedRealtime()
        manualRefreshing = true
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        manualRefreshing = false
    }

    LaunchedEffect(Unit) {
        if (shouldShowInitialLoading) {
            actualLoading = false
        }
    }

    LaunchedEffect(showLoading, shouldShowInitialLoading) {
        if (shouldShowInitialLoading && !showLoading) {
            SessionLoadingRegistry.markShown("faq")
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            scope.launch { runManualRefresh() }
        }
    }

    val listState = rememberLazyListState()
    val showTopDivider by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = Modifier.fillMaxSize()) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                scope.launch { runManualRefresh() }
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    isRefreshing = manualRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (showLoading && !manualRefreshing) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PolygonMorphLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing,
                        bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(questions.toList()) { index, question ->
                        if (question != "empty" && index < answers.size && answers[index] != "empty") {
                            DetailSectionCard(
                                title = question,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                ) {
                                    Text(
                                        text = answers[index],
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            AppTopBar(
                title = stringResource(R.string.action_home_faq_title),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier,
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            )
        }
    }
}
