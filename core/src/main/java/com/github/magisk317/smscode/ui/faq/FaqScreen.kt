package com.github.magisk317.smscode.ui.faq

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.surface.FaqScaffold

@Composable
fun FaqScreen(refreshTrigger: Int = 0) {
    FaqScaffold(
        title = stringResource(R.string.action_home_faq_title),
        questions = stringArrayResource(id = R.array.question_list).toList(),
        answers = stringArrayResource(id = R.array.answer_list).toList(),
        refreshTrigger = refreshTrigger,
    )
}
