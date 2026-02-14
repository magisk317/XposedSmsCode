package com.tianma.xsmscode.ui.rule.edit

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    ruleEditType: Int,
    initialRule: SmsCodeRule? = null,
    initialRuleId: Long? = null,
    onBack: () -> Unit,
    viewModel: RuleEditViewModel = koinViewModel(),
) {
    val context = LocalContext.current

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        val args = Bundle().apply {
            putInt(Const.KEY_RULE_EDIT_TYPE, ruleEditType)
            if (initialRuleId != null) {
                putLong(Const.KEY_RULE_ID, initialRuleId)
            } else {
                putParcelable(Const.KEY_CODE_RULE, initialRule)
            }
        }
        viewModel.handleArguments(args)
    }

    val codeRule by viewModel.codeRuleFlow.collectAsStateWithLifecycle()

    var validationErrorState by remember { mutableStateOf<RuleEditViewModel.ValidationResult?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.eventsFlow, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.eventsFlow.collect { event ->
                when (event) {
                    is RuleEditEvent.HideSoftInput -> keyboardController?.hide()

                    is RuleEditEvent.ValidationError -> validationErrorState = event.result

                    is RuleEditEvent.CodeRuleSaved -> {
                        if (event.success) {
                            onBack()
                        } else {
                            snackbarHostState.showSnackbar(context.getString(R.string.rule_duplicated_prompt))
                        }
                    }

                    is RuleEditEvent.TemplateSaved -> {
                        val msg = if (event.success) R.string.save_template_succeed else R.string.save_template_failed
                        snackbarHostState.showSnackbar(context.getString(msg))
                    }
                }
            }
        }
    }

    // Local state for inputs to allow editing before saving
    var company by remember(codeRule) { mutableStateOf(codeRule.company ?: "") }
    var keyword by remember(codeRule) { mutableStateOf(codeRule.codeKeyword) }
    var regex by remember(codeRule) { mutableStateOf(codeRule.codeRegex) }

    // Quick Choose Dialog State
    var showQuickChoose by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val title = if (ruleEditType == Const.EDIT_TYPE_CREATE) {
                        stringResource(R.string.create_rule)
                    } else {
                        stringResource(R.string.edit_rule)
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val ruleToSave = SmsCodeRule(company, keyword, regex, id = codeRule.id)
                viewModel.saveIfValid(ruleToSave)
            }) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Const.PADDING_MEDIUM.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.PADDING_MEDIUM.dp),
        ) {
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text(stringResource(R.string.rule_company_hint)) },
                modifier = Modifier.fillMaxWidth(),
                isError = validationErrorState?.companyValid == false,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text(stringResource(R.string.rule_keyword_hint)) },
                placeholder = { Text(stringResource(R.string.rule_company_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                isError = validationErrorState?.keywordValid == false,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = regex,
                    onValueChange = { regex = it },
                    label = { Text(stringResource(R.string.rule_code_regex_hint)) },
                    prefix = { Text(stringResource(R.string.rule_regex_field_prefix), color = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.weight(1f),
                    isError = validationErrorState?.codeRegexValid == false,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val ruleToSave = SmsCodeRule(company, keyword, regex, id = codeRule.id)
                        viewModel.saveIfValid(ruleToSave)
                    }),
                )
                Spacer(modifier = Modifier.width(Const.PADDING_SMALL.dp))
                Button(onClick = { showQuickChoose = true }) {
                    Text(stringResource(R.string.quick_choose))
                }
            }
            Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + Const.BOTTOM_SPACE_HEIGHT.dp))
        }
    }

    QuickChooseDialog(
        onDismiss = { showQuickChoose = false },
        onConfirm = { generatedRegex ->
            regex = generatedRegex
            showQuickChoose = false
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickChooseDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val codeTypes = stringArrayResource(R.array.sms_code_type_list)
    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    var codeLength by remember { mutableStateOf("") }
    var lengthError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.quick_choose)) },
        text = {
            Column {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = codeTypes[selectedTypeIndex],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rule_code_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            true,
                        ).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        codeTypes.forEachIndexed { index, type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    selectedTypeIndex = index
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Const.PADDING_MEDIUM.dp))

                OutlinedTextField(
                    value = codeLength,
                    onValueChange = {
                        codeLength = it
                        lengthError = false
                    },
                    label = { Text(stringResource(R.string.rule_code_length)) },
                    isError = lengthError,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
                AnimatedVisibility(
                    visible = lengthError,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(stringResource(R.string.code_length_empty_prompt), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (codeLength.isEmpty()) {
                    lengthError = true
                    return@TextButton
                }
                val codeType = codeTypes[selectedTypeIndex]
                val format = "(?<!%s)%s{%s}(?!%s)"
                val codeRegex = String.format(format, codeType, codeType, codeLength, codeType)
                onConfirm(codeRegex)
            }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
