package com.github.magisk317.smscode.ui.record

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class CodeRecordUiState(val smsList: ImmutableList<SmsMsg> = persistentListOf(), val isLoading: Boolean = false)

class CodeRecordViewModel(
    application: Application,
    private val codeRecord: UiCodeRecordAccess,
) : AndroidViewModel(application) {

    private val _loading = MutableStateFlow(false)

    val uiState: StateFlow<CodeRecordUiState> = codeRecord
        .recordsFlow(application)
        .combine(_loading) { smsList, loading ->
            CodeRecordUiState(smsList.toImmutableList(), loading)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = CodeRecordUiState(isLoading = true),
        )

    fun loadData() {
        // Data is automatically loaded via codeRecord.recordsFlow() in uiState.
    }

    fun refreshData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                withContext(Dispatchers.IO) {
                    codeRecord.queryRecords(getApplication())
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun removeSmsMsg(smsMsgList: List<SmsMsg>) {
        viewModelScope.launch {
            try {
                codeRecord.removeRecords(getApplication(), smsMsgList)
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when remove SMS records", ignored)
            }
        }
    }

    fun restoreSmsMsgList(smsMsgList: List<SmsMsg>) {
        viewModelScope.launch {
            try {
                codeRecord.restoreRecords(getApplication(), smsMsgList)
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when restore SMS records", ignored)
            }
        }
    }

    fun exportRecords(context: Context, uri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val allRecords = uiState.value.smsList.toList()
                withContext(Dispatchers.IO) {
                    codeRecord.exportCodeRecords(context, uri, allRecords)
                }
                // We might want an event for success/failure
            } catch (ignored: Throwable) {
                XLog.e("Export records failed", ignored)
            } finally {
                _loading.value = false
            }
        }
    }
}
