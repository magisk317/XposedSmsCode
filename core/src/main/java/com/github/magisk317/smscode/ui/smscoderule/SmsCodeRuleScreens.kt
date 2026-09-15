@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.smscoderule

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.utils.SmsCodeUtils as AppSmsCodeUtils
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.runtime.bridge.UiStorageAccess
import org.koin.compose.koinInject
import io.github.magisk317.smscode.rule.model.BuiltinSmsCodeRuleSpec
import io.github.magisk317.smscode.rule.model.BuiltinSmsCodeRules
import io.github.magisk317.smscode.rule.catalog.OfficialSmsCodeRule
import io.github.magisk317.smscode.runtime.common.rules.SmsCodeRuleCatalogSnapshot
import io.github.magisk317.uikit.surface.AppFloatingActionButton
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.surface.rememberSaveableTextFieldState
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.DetailSectionCard
import io.github.magisk317.uikit.surface.SectionHeading
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.preference.UrlSourceSettingsScreen
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BUILTIN_RULE_EDITOR_ID_ALPHANUMERIC = -101L
private const val BUILTIN_RULE_EDITOR_ID_DIGITS = -102L

private fun builtinRuleEditorId(ruleId: String): Long? = when (ruleId) {
    BuiltinSmsCodeRules.RULE_ID_ALPHANUMERIC -> BUILTIN_RULE_EDITOR_ID_ALPHANUMERIC
    BuiltinSmsCodeRules.RULE_ID_DIGITS -> BUILTIN_RULE_EDITOR_ID_DIGITS
    else -> null
}

private fun builtinRuleByEditorId(ruleId: Long): BuiltinSmsCodeRuleSpec? = when (ruleId) {
    BUILTIN_RULE_EDITOR_ID_ALPHANUMERIC ->
        BuiltinSmsCodeRules.all.firstOrNull { it.id == BuiltinSmsCodeRules.RULE_ID_ALPHANUMERIC }

    BUILTIN_RULE_EDITOR_ID_DIGITS ->
        BuiltinSmsCodeRules.all.firstOrNull { it.id == BuiltinSmsCodeRules.RULE_ID_DIGITS }

    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsCodeRuleListScreen(
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onSourceSettingsClick: () -> Unit,
) {
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> SmsCodeRuleListScreenMiuix(
            onBack = onBack,
            onAddClick = onAddClick,
            onEditClick = onEditClick,
            onSourceSettingsClick = onSourceSettingsClick,
        )

        UiKitStyle.Expressive -> SmsCodeRuleListScreenMaterial(
            onBack = onBack,
            onAddClick = onAddClick,
            onEditClick = onEditClick,
            onSourceSettingsClick = onSourceSettingsClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmsCodeRuleListScreenShared(
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onSourceSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val storage = koinInject<UiStorageAccess>()
    val dbManager = remember(context) { storage.dbManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val removedLabel = stringResource(id = R.string.removed)
    val emptyPrompt = stringResource(id = R.string.rule_list_empty_prompt)
    val officialTitle = stringResource(id = R.string.official_code_rules_title)
    val officialEmptyPrompt = stringResource(id = R.string.official_code_rules_empty_prompt)
    val officialRefreshSuccess = stringResource(id = R.string.official_code_rules_refresh_success)
    val officialRefreshFailed = stringResource(id = R.string.official_code_rules_refresh_failed)
    val userTitle = stringResource(id = R.string.user_code_rules_title)
    val userSummary = stringResource(id = R.string.user_code_rules_summary)
    val rules by dbManager.queryAllSmsCodeRulesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var officialSnapshot by remember { mutableStateOf<SmsCodeRuleCatalogSnapshot?>(null) }
    var officialLoading by remember { mutableStateOf(false) }
    val officialRules = officialSnapshot?.rules.orEmpty()
    val officialSummary = pluralStringResource(
        id = R.plurals.official_code_rules_summary,
        count = officialRules.size,
        officialRules.size,
        officialSnapshot?.sourceKind?.name?.lowercase().orEmpty().ifBlank { "-" },
    )

    fun loadOfficialRules(refresh: Boolean) {
        if (officialLoading) return
        scope.launch {
            officialLoading = true
            val result = withContext(Dispatchers.IO) {
                if (refresh) {
                    AppSmsCodeUtils.refreshOfficialRules(context)
                } else {
                    null
                }
            }
            officialSnapshot = withContext(Dispatchers.IO) {
                result?.snapshot ?: AppSmsCodeUtils.loadOfficialRuleSnapshot(context)
            }
            officialLoading = false
            if (refresh) {
                snackbarHostState.showSnackbar(
                    if (result?.success == true) officialRefreshSuccess else officialRefreshFailed,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        loadOfficialRules(refresh = false)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(id = R.string.rule_list),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onSourceSettingsClick) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(id = R.string.action_rule_source_settings),
                        )
                    }
                    IconButton(
                        enabled = !officialLoading,
                        onClick = { loadOfficialRules(refresh = true) },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.action_refresh))
                    }
                },
            )
        },
        snackbarHost = {
            io.github.magisk317.uikit.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
                onClick = onAddClick,
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(id = R.string.create_rule),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 144.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RuleSectionHeader(
                    title = officialTitle,
                    summary = officialSummary,
                )
            }
            if (officialRules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = officialEmptyPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(officialRules, key = { index, rule -> "${rule.sourcePath}:${rule.id}:$index" }) { index, rule ->
                    OfficialSmsCodeRuleCard(
                        rule = rule,
                        ordinal = index + 1,
                    )
                }
            }
            item {
                RuleSectionHeader(
                    title = userTitle,
                    summary = userSummary,
                )
            }
            if (rules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(rules, key = { _, rule -> rule.id ?: 0L }) { index, rule ->
                    SmsCodeRuleCard(
                        rule = rule,
                        ordinal = index + 1,
                        onEdit = { rule.id?.let(onEditClick) },
                        onDelete = {
                            scope.launch {
                                dbManager.removeSmsCodeRuleSuspend(rule)
                                snackbarHostState.showSnackbar("$removedLabel: ${rule.codeKeyword}")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SmsCodeRuleSourceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    UrlSourceSettingsScreen(
        title = stringResource(id = R.string.rule_source_settings_title),
        fieldLabel = stringResource(id = R.string.rule_source_url_label),
        supportingText = stringResource(id = R.string.rule_source_url_summary),
        invalidUrlMessage = stringResource(id = R.string.rule_source_url_invalid),
        savedMessage = stringResource(id = R.string.rule_source_saved),
        saveFailedMessage = stringResource(id = R.string.rule_source_save_failed),
        saveContentDescription = stringResource(id = R.string.action_save),
        valueFlow = remember(context) { AppSmsCodeUtils.observeOfficialRuleSourceUrl(context) },
        onSaveValue = { value -> AppSmsCodeUtils.saveOfficialRuleSourceUrl(context, value) },
        onBack = onBack,
    )
}

@Composable
private fun RuleSectionHeader(
    title: String,
    summary: String,
) {
    SectionHeading(title = title, summary = summary)
}

@Composable
private fun OfficialSmsCodeRuleCard(
    rule: OfficialSmsCodeRule,
    ordinal: Int,
) {
    val officialBadge = stringResource(id = R.string.official_rule_badge_format, ordinal)
    DetailSectionCard(
        title = rule.company?.takeIf { it.isNotBlank() } ?: rule.setName,
        summary = officialBadge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = rule.codeKeyword,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = rule.codeRegex,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SmsCodeRuleCard(
    rule: SmsCodeRule,
    ordinal: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val userBadge = stringResource(id = R.string.user_rule_badge_format, ordinal)
    DetailSectionCard(
        title = rule.company?.takeIf { it.isNotBlank() } ?: rule.codeKeyword,
        summary = userBadge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!rule.company.isNullOrBlank()) {
                Text(
                    text = rule.codeKeyword,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = rule.codeRegex,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppTextButton(text = stringResource(id = R.string.edit), onClick = onEdit)
                AppTextButton(text = stringResource(id = R.string.remove), onClick = onDelete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsCodeRuleEditorScreen(
    ruleId: Long,
    onBack: () -> Unit,
) {
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> SmsCodeRuleEditorScreenMiuix(
            ruleId = ruleId,
            onBack = onBack,
        )

        UiKitStyle.Expressive -> SmsCodeRuleEditorScreenMaterial(
            ruleId = ruleId,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmsCodeRuleEditorScreenShared(
    ruleId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val storage = koinInject<UiStorageAccess>()
    val dbManager = remember(context) { storage.dbManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val builtinRule = remember(ruleId) { builtinRuleByEditorId(ruleId) }
    val isBuiltinRule = builtinRule != null
    val loadFailedText = stringResource(id = R.string.load_failed)
    val saveFailedText = stringResource(id = R.string.save_failed)
    val keywordEmptyText = stringResource(id = R.string.rule_keyword_empty_hint)
    val regexEmptyText = stringResource(id = R.string.rule_code_regex_empty_hint)
    val duplicateText = stringResource(id = R.string.rule_duplicated_prompt)
    val confirmLabel = stringResource(id = R.string.confirm)
    val copyLabel = stringResource(id = R.string.action_copy)
    val rulesSummary = stringResource(id = R.string.pref_code_rules_summary)
    val builtinSummary = stringResource(id = R.string.builtin_code_rules_summary)
    val builtinKeywordSetting = stringResource(id = R.string.builtin_rule_keyword_setting)
    val builtinTitle = when (builtinRule?.id) {
        BuiltinSmsCodeRules.RULE_ID_ALPHANUMERIC -> stringResource(id = R.string.builtin_rule_alphanumeric_title)
        BuiltinSmsCodeRules.RULE_ID_DIGITS -> stringResource(id = R.string.builtin_rule_digits_title)
        else -> stringResource(id = R.string.builtin_rule_badge)
    }
    val companyLabel = stringResource(id = R.string.rule_company_hint)
    val keywordLabel = stringResource(id = R.string.rule_keyword_hint)
    val regexLabel = stringResource(id = R.string.rule_code_regex_hint)
    val testGuidance = stringResource(
        id = R.string.rule_test_guidance,
        stringResource(id = R.string.pref_smscode_test_title),
    )
    val company = rememberSaveableTextFieldState()
    val keyword = rememberSaveableTextFieldState()
    val regex = rememberSaveableTextFieldState()
    var loading by remember { mutableStateOf(ruleId != 0L) }

    LaunchedEffect(ruleId) {
        if (builtinRule != null) {
            company.setTextAndPlaceCursorAtEnd("")
            keyword.setTextAndPlaceCursorAtEnd(builtinKeywordSetting)
            regex.setTextAndPlaceCursorAtEnd(builtinRule.codeRegex)
            loading = false
            return@LaunchedEffect
        }
        if (ruleId == 0L) {
            loading = false
            return@LaunchedEffect
        }
        val rule = dbManager.querySmsCodeRuleByIdSuspend(ruleId)
        if (rule == null) {
            snackbarHostState.showSnackbar(loadFailedText)
            onBack()
            return@LaunchedEffect
        }
        company.setTextAndPlaceCursorAtEnd(rule.company.orEmpty())
        keyword.setTextAndPlaceCursorAtEnd(rule.codeKeyword)
        regex.setTextAndPlaceCursorAtEnd(rule.codeRegex)
        loading = false
    }

    fun saveRule() {
        if (isBuiltinRule) return
        scope.launch {
            val normalizedCompany = company.text.toString().trim().ifBlank { null }
            val normalizedKeyword = keyword.text.toString().trim()
            val normalizedRegex = regex.text.toString().trim()
            when {
                normalizedKeyword.isEmpty() -> {
                    snackbarHostState.showSnackbar(keywordEmptyText)
                    return@launch
                }
                normalizedRegex.isEmpty() -> {
                    snackbarHostState.showSnackbar(regexEmptyText)
                    return@launch
                }
            }
            runCatching {
                Pattern.compile(normalizedRegex)
            }.onFailure {
                snackbarHostState.showSnackbar(it.message ?: saveFailedText)
                return@launch
            }
            val duplicated = dbManager.queryAllSmsCodeRulesSuspend().firstOrNull { existing ->
                existing.id != ruleId &&
                    existing.company.orEmpty().trim() == normalizedCompany.orEmpty() &&
                    existing.codeKeyword.trim() == normalizedKeyword &&
                    existing.codeRegex.trim() == normalizedRegex
            }
            if (duplicated != null) {
                snackbarHostState.showSnackbar(duplicateText)
                return@launch
            }
            val targetRule = SmsCodeRule(
                company = normalizedCompany,
                codeKeyword = normalizedKeyword,
                codeRegex = normalizedRegex,
                id = ruleId.takeIf { it > 0L },
            )
            if (ruleId > 0L) {
                dbManager.updateSmsCodeRuleSuspend(targetRule)
            } else {
                dbManager.addSmsCodeRuleSuspend(targetRule)
            }
            onBack()
        }
    }

    fun copyField(label: String, value: String) {
        if (value.isBlank()) return
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            snackbarHostState.showSnackbar(context.getString(R.string.prompt_field_copied, label))
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (isBuiltinRule) builtinTitle else {
                    stringResource(
                        id = if (ruleId == 0L) R.string.create_rule else R.string.edit_rule,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!isBuiltinRule) {
                        AppTextButton(
                            text = confirmLabel,
                            enabled = !loading,
                            onClick = ::saveRule,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            io.github.magisk317.uikit.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                state = company,
                modifier = Modifier.fillMaxWidth(),
                label = companyLabel,
                placeholder = { Text(stringResource(id = R.string.rule_company_placeholder)) },
                supportingText = { Text(if (isBuiltinRule) builtinSummary else rulesSummary) },
                readOnly = isBuiltinRule,
                enabled = !loading,
                trailingIcon = if (isBuiltinRule && company.text.isNotBlank()) {
                    {
                        IconButton(onClick = { copyField(companyLabel, company.text.toString()) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )
            AppTextField(
                state = keyword,
                modifier = Modifier.fillMaxWidth(),
                label = keywordLabel,
                readOnly = isBuiltinRule,
                enabled = !loading,
                trailingIcon = if (isBuiltinRule && keyword.text.isNotBlank()) {
                    {
                        IconButton(onClick = { copyField(keywordLabel, keyword.text.toString()) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )
            AppTextField(
                state = regex,
                modifier = Modifier.fillMaxWidth(),
                label = regexLabel,
                readOnly = isBuiltinRule,
                enabled = !loading,
                trailingIcon = if (isBuiltinRule && regex.text.isNotBlank()) {
                    {
                        IconButton(onClick = { copyField(regexLabel, regex.text.toString()) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                        }
                    }
                } else {
                    null
                },
                minLines = 3,
                singleLine = false,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = testGuidance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
