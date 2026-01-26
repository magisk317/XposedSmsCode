package com.tianma.xsmscode.data.eventbus

import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.backup.ExportResult
import com.tianma.xsmscode.ui.rule.edit.RuleEditFragment
import java.io.File

object Event {
    /**
     * Start to edit codeRule event
     */
    data class StartRuleEditEvent(
        @RuleEditFragment.RuleEditType var type: Int,
        var codeRule: SmsCodeRule?
    )

    /**
     * Rule create or update event
     */
    data class OnRuleCreateOrUpdate(
        @RuleEditFragment.RuleEditType var type: Int,
        var codeRule: SmsCodeRule
    )

    /**
     * Save template rule event
     */
    data class TemplateSaveEvent(var success: Boolean)

    /**
     * Load template rule event
     */
    data class TemplateLoadEvent(var template: SmsCodeRule)

    class ExportEvent(var result: ExportResult, var file: File)
}
