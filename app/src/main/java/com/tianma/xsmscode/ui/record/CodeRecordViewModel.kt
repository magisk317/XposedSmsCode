package com.tianma.xsmscode.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsMsg
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class CodeRecordViewModel(application: Application) : AndroidViewModel(application) {

    private val mSmsMsgListLiveData = MutableLiveData<List<SmsMsg>>()
    private val mLoadingLiveData = MutableLiveData<Boolean>()

    val smsMsgListLiveData: LiveData<List<SmsMsg>> = mSmsMsgListLiveData
    val loadingLiveData: LiveData<Boolean> = mLoadingLiveData

    fun loadData() {
        viewModelScope.launch {
            mLoadingLiveData.value = true
            try {
                DBManager.get(getApplication())
                    .queryAllSmsMsgFlow()
                    .collect { smsMsgList ->
                        mSmsMsgListLiveData.value = smsMsgList
                        mLoadingLiveData.value = false
                    }
            } catch (t: Throwable) {
                XLog.e("", t)
                mLoadingLiveData.value = false
            }
        }
    }

    fun removeSmsMsg(smsMsgList: List<SmsMsg>) {
        viewModelScope.launch {
            try {
                DBManager.get(getApplication())
                    .removeSmsMsgListSuspend(smsMsgList)
            } catch (t: Throwable) {
                XLog.e("Error occurs when remove SMS records", t)
            }
        }
    }
}
