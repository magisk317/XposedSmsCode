package com.github.magisk317.smscode.ui.smscoderule

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.ui.shell.PageScaffoldExpressive

@Composable
internal fun SmsCodeRuleListScreenMaterial(
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldExpressive(
        title = stringResource(id = R.string.rule_list),
        onBack = onBack,
        actions = actions,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        content = body,
    )
}

@Composable
internal fun SmsCodeRuleEditorScreenMaterial(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldExpressive(
        title = title,
        onBack = onBack,
        actions = actions,
        snackbarHost = snackbarHost,
        content = body,
    )
}
