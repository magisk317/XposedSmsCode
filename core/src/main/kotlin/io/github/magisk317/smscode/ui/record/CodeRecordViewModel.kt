package io.github.magisk317.smscode.ui.record

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.smscode.common.utils.XLog
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class CodeRecordUiState(val smsList: ImmutableList<SmsMsg> = persistentListOf(), val isLoading: Boolean = false)

internal class RecordRequestGeneration {
    private var current = 0L

    fun next(): Long = ++current

    fun invalidate() {
        current += 1
    }

    fun isCurrent(generation: Long): Boolean = generation == current
}

class CodeRecordViewModel(
    application: Application,
    private val codeRecord: UiCodeRecordAccess,
) : AndroidViewModel(application) {

    private val _loading = MutableStateFlow(false)
    private val _records = MutableStateFlow<ImmutableList<SmsMsg>>(persistentListOf())
    private val _hasRecordSnapshot = MutableStateFlow(false)
    val hasRecordSnapshot: StateFlow<Boolean> = _hasRecordSnapshot.asStateFlow()
    private val _isActive = MutableStateFlow(false)
    private var recordsJob: Job? = null
    private var refreshJob: Job? = null
    private var activationGeneration = 0L
    private val refreshGeneration = RecordRequestGeneration()

    val uiState: StateFlow<CodeRecordUiState> = combine(
        _records,
        _loading,
        _hasRecordSnapshot,
        _isActive,
    ) { smsList, loading, hasSnapshot, isActive ->
        CodeRecordUiState(smsList, loading || (isActive && !hasSnapshot))
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CodeRecordUiState(isLoading = true),
        )

    fun loadData() {
        setActive(true)
    }

    fun setActive(active: Boolean) {
        if (_isActive.value == active) return
        _isActive.value = active
        val generation = ++activationGeneration
        recordsJob?.cancel()
        recordsJob = null
        if (!active) {
            refreshGeneration.invalidate()
            refreshJob?.cancel()
            refreshJob = null
            _loading.value = false
            return
        }
        recordsJob = viewModelScope.launch {
            try {
                codeRecord.recordsFlow(getApplication()).collect { smsList ->
                    if (generation != activationGeneration || !_isActive.value) return@collect
                    _records.value = smsList.toImmutableList()
                    _hasRecordSnapshot.value = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                XLog.e("Code record stream failed", t)
            }
        }
    }

    fun refreshData() {
        if (!_isActive.value) return
        val activation = activationGeneration
        val refresh = refreshGeneration.next()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _loading.value = true
            try {
                val records = withContext(Dispatchers.IO) {
                    codeRecord.queryRecords(getApplication())
                }
                if (activation == activationGeneration &&
                    refreshGeneration.isCurrent(refresh) &&
                    _isActive.value
                ) {
                    _records.value = records.toImmutableList()
                    _hasRecordSnapshot.value = true
                }
            } finally {
                if (activation == activationGeneration && refreshGeneration.isCurrent(refresh)) {
                    _loading.value = false
                    refreshJob = null
                }
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
