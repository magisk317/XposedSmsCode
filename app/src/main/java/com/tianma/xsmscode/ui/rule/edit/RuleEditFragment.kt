package com.tianma.xsmscode.ui.rule.edit

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.Fragment
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.FragmentRuleEditBinding
import com.google.android.material.textfield.TextInputEditText
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.data.db.entity.SmsCodeRule

class RuleEditFragment : Fragment() {

    private var binding: FragmentRuleEditBinding? = null
    private var mActivity: Activity? = null
    private var mCodeTypeIndex = 0
    private lateinit var mViewModel: RuleEditViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRuleEditBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mActivity = activity
        mViewModel = ViewModelProvider(this).get(RuleEditViewModel::class.java)
        setupObservers()

        binding?.ruleCodeRegexQuickChoose?.setOnClickListener { showQuickChooseDialog() }

        binding?.ruleCodeRegexEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mViewModel.saveIfValid(getCurrentCodeRule())
                true
            } else {
                false
            }
        }

        mViewModel.handleArguments(arguments)
        
        setupMenu()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_edit_rule, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_rules_tick -> {
                        mViewModel.saveIfValid(getCurrentCodeRule())
                        true
                    }
                    R.id.action_save_as_template -> {
                        mViewModel.saveAsTemplate(getCurrentCodeRule())
                        true
                    }
                    R.id.action_rule_help -> {
                        showCodeRuleHelp()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupObservers() {
        mViewModel.codeRuleLiveData.observe(viewLifecycleOwner) { displayCodeRule(it) }
        mViewModel.templateSavedEvent.observe(viewLifecycleOwner) { onTemplateSaved(it) }
        mViewModel.validationErrorEvent.observe(viewLifecycleOwner) { result ->
            showErrorInfo(result.companyValid, result.keywordValid, result.codeRegexValid)
        }
        mViewModel.hideSoftInputEvent.observe(viewLifecycleOwner) { hideSoftInput() }
        mViewModel.codeRuleSavedEvent.observe(viewLifecycleOwner) { onCodeRuleSaved(it) }
    }

    private fun showQuickChooseDialog() {
        val codeTypes = resources.getStringArray(R.array.sms_code_type_list)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_code_regex_quick_chcoose, null)
        val spinner = dialogView.findViewById<AppCompatSpinner>(R.id.rule_code_type_spinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mCodeTypeIndex = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val codeLenEditText = dialogView.findViewById<TextInputEditText>(R.id.code_rule_length_edit_text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quick_choose)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                val codeType = codeTypes[mCodeTypeIndex]
                val codeLenText = codeLenEditText.text.toString()

                if (TextUtils.isEmpty(codeLenText)) {
                    codeLenEditText.error = getString(R.string.code_length_empty_prompt)
                    return@setPositiveButton
                }
                val format = "(?<!%s)%s{%s}(?!%s)"
                val codeRegex = String.format(format, codeType, codeType, codeLenText, codeType)
                binding?.ruleCodeRegexEditText?.let { setText(it, codeRegex) }
                dialog.dismiss()
            }
            .show()
    }

    private fun setText(editText: EditText, text: CharSequence?) {
        if (!text.isNullOrEmpty()) {
            editText.setText(text)
            editText.setSelection(text.length)
        }
    }

    private fun setError(editText: EditText, @StringRes textId: Int) {
        setError(editText, getString(textId))
    }

    private fun setError(editText: EditText, error: String?) {
        editText.error = error
    }


    private fun getCurrentCodeRule(): SmsCodeRule {
        val company = binding?.ruleCompanyEditText?.text?.toString()
        val keyword = binding?.ruleKeywordEditText?.text?.toString() ?: ""
        val codeRegex = binding?.ruleCodeRegexEditText?.text?.toString() ?: ""
        return SmsCodeRule(company, keyword, codeRegex)
    }

    private fun showCodeRuleHelp() {
        val ruleHelpUrl = Utils.getProjectDocUrl(Const.PROJECT_DOC_BASE_URL, Const.DOC_SMS_CODE_RULE_HELP)
        Utils.showWebPage(requireContext(), ruleHelpUrl)
    }

    private fun displayCodeRule(codeRule: SmsCodeRule?) {
        codeRule?.let {
            binding?.let { b ->
                setText(b.ruleCompanyEditText, it.company)
                setText(b.ruleKeywordEditText, it.codeKeyword)
                setText(b.ruleCodeRegexEditText, it.codeRegex)
            }
        }
    }

    private fun onTemplateSaved(success: Boolean) {
        val msg = if (success) R.string.save_template_succeed else R.string.save_template_failed
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun showErrorInfo(companyValid: Boolean, keywordValid: Boolean, codeRegexValid: Boolean) {
        binding?.let { b ->
            if (!companyValid) setError(b.ruleCompanyEditText, R.string.rule_company_empty_hint)
            if (!keywordValid) setError(b.ruleKeywordEditText, R.string.rule_keyword_empty_hint)
            if (!codeRegexValid) setError(b.ruleCodeRegexEditText, R.string.rule_code_regex_empty_hint)
        }
    }

    private fun hideSoftInput() {
        val imeManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imeManager?.let {
            if (it.isActive) {
                binding?.ruleCodeRegexEditText?.let { view ->
                    it.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                }
            }
        }
    }

    private fun onCodeRuleSaved(success: Boolean) {
        if (success) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        } else {
            Toast.makeText(requireContext(), R.string.rule_duplicated_prompt, Toast.LENGTH_LONG).show()
        }
    }

    @IntDef(EDIT_TYPE_CREATE, EDIT_TYPE_UPDATE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class RuleEditType

    companion object {
        const val EDIT_TYPE_CREATE = 1
        const val EDIT_TYPE_UPDATE = 2
        const val KEY_RULE_EDIT_TYPE = "rule_edit_type"
        const val KEY_CODE_RULE = "code_rule"

        @JvmStatic
        fun newInstance(ruleEditType: Int, codeRule: SmsCodeRule?): RuleEditFragment {
            val args = Bundle()
            args.putInt(KEY_RULE_EDIT_TYPE, ruleEditType)
            args.putParcelable(KEY_CODE_RULE, codeRule)
            val fragment = RuleEditFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
