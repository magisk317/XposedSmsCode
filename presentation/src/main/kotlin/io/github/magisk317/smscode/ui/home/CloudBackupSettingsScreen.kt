package io.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.backup.CloudBackupPanel
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import io.github.magisk317.uikit.surface.PageScaffoldExpressive
import io.github.magisk317.uikit.surface.PageScaffoldMiuix
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

/**
 * Navigation handle the settings screen uses to open this page; the value is
 * provided by the settings tab of [MainScreen], mirroring
 * [LocalThemeSettingsNavigation].
 */
internal val LocalCloudBackupNavigation = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Cloud backup settings page: hosts the ui-kit [CloudBackupPanel] and adds the
 * host-owned restore confirmation. All provider logic lives in
 * [CloudBackupViewModel]; this composable only maps user gestures to it.
 */
@Composable
fun CloudBackupSettingsScreen(
    viewModel: CloudBackupViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { AppSnackbarHostState() }
    var pendingRestoreId by remember { mutableStateOf<String?>(null) }

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, _ ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            Box(modifier = Modifier.fillMaxSize()) {
                CloudBackupPanel(
                    state = state,
                    actions = viewModel.actions.copy(
                        onRestoreBackup = { backupId -> pendingRestoreId = backupId },
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = listPadding.calculateTopPadding()),
                )
                AppSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                )
            }
        }
    }

    val pageTitle = stringResource(R.string.pref_cloud_backup_title)
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> PageScaffoldMiuix(title = pageTitle, onBack = onBack, content = body)
        UiKitStyle.Expressive -> PageScaffoldExpressive(title = pageTitle, onBack = onBack, content = body)
    }

    pendingRestoreId?.let { backupId ->
        AppAlertDialog(
            onDismissRequest = { pendingRestoreId = null },
            title = {
                AppText(
                    text = stringResource(R.string.cloud_backup_restore_confirm_title),
                    role = AppTextRole.Title,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                AppText(
                    text = stringResource(R.string.cloud_backup_restore_confirm_message),
                    role = AppTextRole.Body,
                )
            },
            confirmButton = {},
            dismissButton = {
                DialogActionRow(
                    actions = listOf(
                        DialogAction(
                            label = stringResource(UiKitR.string.uikit_action_cancel),
                            onClick = { pendingRestoreId = null },
                            style = DialogActionStyle.Secondary,
                        ),
                        DialogAction(
                            label = stringResource(R.string.cloud_backup_restore_confirm_action),
                            onClick = {
                                pendingRestoreId = null
                                viewModel.restoreBackup(backupId)
                            },
                            style = DialogActionStyle.Danger,
                        ),
                    ),
                )
            },
        )
    }
}
