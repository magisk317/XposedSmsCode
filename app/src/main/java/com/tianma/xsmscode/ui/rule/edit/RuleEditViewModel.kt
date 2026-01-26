package com.tianma.xsmscode.ui.rule.edit

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.common.livedata.SingleLiveEvent
import androidx.core.os.BundleCompat
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.eventbus.Event
import com.tianma.xsmscode.data.eventbus.XEventBus
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {

    private val mCodeRuleLiveData = MutableLiveData<SmsCodeRule>()
    private val mTemplateSavedEvent = SingleLiveEvent<Boolean>()
    private val mValidationErrorEvent = SingleLiveEvent<ValidationResult>()
    private val mHideSoftInputEvent = SingleLiveEvent<Void>()
    private val mCodeRuleSavedEvent = SingleLiveEvent<Boolean>()

    private var mRuleEditType: Int = RuleEditFragment.EDIT_TYPE_CREATE
    private var mCodeRule: SmsCodeRule = SmsCodeRule()

    val codeRuleLiveData: LiveData<SmsCodeRule> = mCodeRuleLiveData
    val templateSavedEvent: LiveData<Boolean> = mTemplateSavedEvent
    val validationErrorEvent: LiveData<ValidationResult> = mValidationErrorEvent
    val hideSoftInputEvent: LiveData<Void> = mHideSoftInputEvent
    val codeRuleSavedEvent: LiveData<Boolean> = mCodeRuleSavedEvent

    override fun onCleared() {
        super.onCleared()
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        mRuleEditType = args.getInt(RuleEditFragment.KEY_RULE_EDIT_TYPE)
        val codeRule = BundleCompat.getParcelable(args, RuleEditFragment.KEY_CODE_RULE, SmsCodeRule::class.java)
        if (mRuleEditType == RuleEditFragment.EDIT_TYPE_UPDATE && codeRule != null) {
            mCodeRule = codeRule
            mCodeRuleLiveData.value = mCodeRule
        } else {
            loadTemplate()
        }
    }

    fun loadTemplate() {
        viewModelScope.launch {
            try {
                val codeRule = withContext(Dispatchers.IO) {
                    EntityStoreManager.loadEntityFromFile(
                        EntityType.CODE_RULE_TEMPLATE, SmsCodeRule::class.java
                    ) ?: SmsCodeRule()
                }
                mCodeRule = codeRule
                mCodeRuleLiveData.value = mCodeRule
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    fun saveAsTemplate(template: SmsCodeRule) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                EntityStoreManager.storeEntityToFile(EntityType.CODE_RULE_TEMPLATE, template)
            }
            mTemplateSavedEvent.value = success
        }
    }

    fun saveIfValid(codeRule: SmsCodeRule) {
        if (!checkValid(codeRule)) return
        mHideSoftInputEvent.call()

        mCodeRule.company = codeRule.company
        mCodeRule.codeKeyword = codeRule.codeKeyword
        mCodeRule.codeRegex = codeRule.codeRegex

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val dbManager = DBManager.get(getApplication())
                if (mRuleEditType == RuleEditFragment.EDIT_TYPE_CREATE) {
                    if (dbManager.isExistsSuspend(mCodeRule)) {
                        false
                    } else {
                        val id = dbManager.addSmsCodeRuleSuspend(mCodeRule)
                        mCodeRule.id = id
                        true
                    }
                } else {
                    dbManager.updateSmsCodeRuleSuspend(mCodeRule)
                    true
                }
            }
            XEventBus.post(Event.OnRuleCreateOrUpdate(mRuleEditType, mCodeRule))
            mCodeRuleSavedEvent.value = success
        }
    }

    private fun checkValid(codeRule: SmsCodeRule): Boolean {
        val companyValid = !codeRule.company.isNullOrEmpty()
        val keywordValid = codeRule.codeKeyword.isNotEmpty()
        val codeRegexValid = codeRule.codeRegex.isNotEmpty()

        mValidationErrorEvent.value = ValidationResult(companyValid, keywordValid, codeRegexValid)
        return companyValid && keywordValid && codeRegexValid
    }

    data class ValidationResult(
        val companyValid: Boolean,
        val keywordValid: Boolean,
        val codeRegexValid: Boolean
    )
}
