package com.github.magisk317.smscode.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.magisk317.smscode.ui.home.MainScreen
import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
object OverviewRoute

@Serializable
object SettingsRoute

@Serializable
object ThemeSettingsRoute

@Serializable
object RecordsRoute

@Serializable
object AppBlockRoute

@Serializable
object AppConfigRoute

@Serializable
data class SmsCodeRulesRoute(
    val fromShortcut: Boolean = false,
)

@Serializable
data class SmsCodeRuleEditorRoute(
    val id: Long = 0,
)

@Serializable
object SmsCodeRuleSourceRoute

@Composable
fun SmsCodeNavHost(
    navController: NavHostController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    onBottomOverlayPaddingChanged: (Dp) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = modifier,
    ) {
        composable<MainRoute> {
            MainScreen(
                initialTab = initialTab,
                onInitialTabConsumed = onInitialTabConsumed,
                onBottomOverlayPaddingChanged = onBottomOverlayPaddingChanged,
            )
        }
    }
}
