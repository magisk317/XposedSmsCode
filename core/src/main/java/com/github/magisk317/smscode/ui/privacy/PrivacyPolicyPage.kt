package com.github.magisk317.smscode.ui.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.foundation.stripMarkdown
import io.github.magisk317.uikit.surface.PrivacyPolicyScaffold

@Composable
fun PrivacyPolicyPage(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val policyText = remember {
        stripMarkdown(
            context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() },
        )
    }
    PrivacyPolicyScaffold(
        title = stringResource(id = R.string.pref_privacy_policy_title),
        policyText = policyText,
        onDismiss = onDismiss,
        extraBottomPadding = Const.BOTTOM_SPACE_HEIGHT.dp,
    )
}
