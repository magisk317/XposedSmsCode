package com.github.magisk317.smscode.ui.smscoderule

import androidx.compose.runtime.Composable

@Composable
internal fun SmsCodeRuleListScreenMiuix(
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onSourceSettingsClick: () -> Unit,
) {
    SmsCodeRuleListScreenShared(
        onBack = onBack,
        onAddClick = onAddClick,
        onEditClick = onEditClick,
        onSourceSettingsClick = onSourceSettingsClick,
    )
}

@Composable
internal fun SmsCodeRuleEditorScreenMiuix(
    ruleId: Long,
    onBack: () -> Unit,
) {
    SmsCodeRuleEditorScreenShared(
        ruleId = ruleId,
        onBack = onBack,
    )
}
