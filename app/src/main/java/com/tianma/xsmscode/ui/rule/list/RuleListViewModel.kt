package com.tianma.xsmscode.ui.rule.list

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.common.livedata.SingleLiveEvent
import androidx.core.os.BundleCompat
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.backup.BackupManager
import com.tianma.xsmscode.feature.backup.ExportResult
import com.tianma.xsmscode.feature.backup.ImportResult
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RuleListViewModel(application: Application) : AndroidViewModel(application) {

    private val mRulesLiveData = MutableLiveData<List<SmsCodeRule>>()

    private val mImportDirectEvent = SingleLiveEvent<Uri>()
    private val mImportDialogConfirmEvent = SingleLiveEvent<Uri>()
    private val mShowProgressEvent = SingleLiveEvent<String>()
    private val mCancelProgressEvent = SingleLiveEvent<Void>()

    private val mExportResultBelowQEvent = SingleLiveEvent<Pair<Boolean, File>>()
    private val mExportResultAboveQEvent = SingleLiveEvent<Boolean>()
    private val mImportResultEvent = SingleLiveEvent<ImportResult>()

    val rulesLiveData: LiveData<List<SmsCodeRule>> = mRulesLiveData
    val importDirectEvent: LiveData<Uri> = mImportDirectEvent
    val importDialogConfirmEvent: LiveData<Uri> = mImportDialogConfirmEvent
    val showProgressEvent: LiveData<String> = mShowProgressEvent
    val cancelProgressEvent: LiveData<Void> = mCancelProgressEvent
    val exportResultBelowQEvent: LiveData<Pair<Boolean, File>> = mExportResultBelowQEvent
    val exportResultAboveQEvent: LiveData<Boolean> = mExportResultAboveQEvent
    val importResultEvent: LiveData<ImportResult> = mImportResultEvent

    override fun onCleared() {
        super.onCleared()
    }

    fun loadAllRules() {
        viewModelScope.launch {
            try {
                val rules = withContext(Dispatchers.IO) {
                    DBManager.get(getApplication()).queryAllSmsCodeRulesSuspend()
                }
                mRulesLiveData.value = rules
            } catch (t: Throwable) {
                XLog.e("Load all rules failed", t)
            }
        }
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        val importUri = BundleCompat.getParcelable(args, RuleListFragment.EXTRA_IMPORT_URI, Uri::class.java)
        if (importUri != null) {
            args.remove(RuleListFragment.EXTRA_IMPORT_URI)
            if (ContentResolver.SCHEME_FILE == importUri.scheme) {
                mImportDirectEvent.value = importUri
            } else {
                mImportDialogConfirmEvent.value = importUri
            }
        }
    }

    fun removeRule(codeRule: SmsCodeRule) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DBManager.get(getApplication()).removeSmsCodeRuleSuspend(codeRule)
                }
            } catch (t: Throwable) {
                XLog.e("Remove $codeRule failed", t)
            }
        }
    }

    fun exportRulesBelowQ(rules: List<SmsCodeRule>, file: File, progressMsg: String) {
        viewModelScope.launch {
            mShowProgressEvent.value = progressMsg
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupManager.exportRuleList(file, rules)
                }
                mExportResultBelowQEvent.value = Pair(result == ExportResult.SUCCESS, file)
                mCancelProgressEvent.call()
            } catch (t: Throwable) {
                XLog.e("Export failed", t)
                mCancelProgressEvent.call()
            }
        }
    }

    fun exportRulesAboveQ(rules: List<SmsCodeRule>, context: Context, uri: Uri, progressMsg: String) {
        viewModelScope.launch {
            mShowProgressEvent.value = progressMsg
            try {
                val result = withContext(Dispatchers.IO) {
                     BackupManager.exportRuleList(context, uri, rules)
                }
                mExportResultAboveQEvent.value = result == ExportResult.SUCCESS
                mCancelProgressEvent.call()
            } catch (t: Throwable) {
                 XLog.e("Export failed", t)
                 mCancelProgressEvent.call()
            }
        }
    }

    fun importRules(uri: Uri, retain: Boolean, progressMsg: String) {
        viewModelScope.launch {
            mShowProgressEvent.value = progressMsg
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupManager.importRuleList(getApplication(), uri, retain)
                }
                mImportResultEvent.value = result
                mCancelProgressEvent.call()
            } catch (t: Throwable) {
                XLog.e("Import rules failed", t)
                // Assuming BackupManager already catches exceptions and returns result, but safety check
                mCancelProgressEvent.call()
            }
        }
    }

    fun saveRulesToFile(rules: List<SmsCodeRule>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                EntityStoreManager.storeEntitiesToFile(EntityType.CODE_RULES, rules)
            }
        }
    }
}
