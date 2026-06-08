package com.github.magisk317.smscode.ui.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.ui.app.base.SystemBarsScrim
import com.github.magisk317.smscode.ui.app.base.rememberHazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.SummarySectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyPage(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val hazeStyle = rememberHazeStyle()
    val policyText = remember {
        context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
            .lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trimEnd()
                if (trimmed == "---") {
                    return@mapNotNull null
                }
                if (trimmed.startsWith("[") && trimmed.contains("](")) {
                    return@mapNotNull null
                }
                val noHeading = trimmed
                    .removePrefix("### ")
                    .removePrefix("## ")
                    .removePrefix("# ")
                val noBold = noHeading.replace("**", "")
                when {
                    noBold.startsWith("*   ") -> "- " + noBold.removePrefix("*   ")
                    noBold.startsWith("* ") -> "- " + noBold.removePrefix("* ")
                    else -> noBold
                }
            }
            .joinToString("\n")
            .trim()
    }
    val scrollState = rememberScrollState()

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        Const.BOTTOM_SPACE_HEIGHT.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = topPadding + 8.dp, bottom = bottomPadding + 16.dp),
        ) {
            SummarySectionCard {
                Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        text = policyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AppTopBar(
            title = stringResource(id = R.string.pref_privacy_policy_title),
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier.hazeEffect(hazeState) {
                    blurEffect { style = hazeStyle }
                forceInvalidateOnPreDraw = true
            },
        )

        SystemBarsScrim(hazeState = hazeState, hazeStyle = hazeStyle)
    }
}
