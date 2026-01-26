package com.tianma.xsmscode.ui.block

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.common.livedata.SingleLiveEvent
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.Comparator

class AppBlockViewModel(application: Application) : AndroidViewModel(application) {

    private val _appsLiveData = MutableLiveData<List<AppInfo>>()
    val appsLiveData: LiveData<List<AppInfo>> = _appsLiveData

    private val _loadingLiveData = MutableLiveData<Boolean>()
    val loadingLiveData: LiveData<Boolean> = _loadingLiveData

    private val _errorEvent = SingleLiveEvent<Throwable>()
    val errorEvent: LiveData<Throwable> = _errorEvent

    private val _saveSuccessEvent = SingleLiveEvent<Void>()
    val saveSuccessEvent: LiveData<Void> = _saveSuccessEvent

    private val _saveFailedEvent = SingleLiveEvent<Void>()
    val saveFailedEvent: LiveData<Void> = _saveFailedEvent

    private var originalBlockedApps: List<AppInfo> = ArrayList()
    private var apps: List<AppInfo> = ArrayList()
    private var isLoadSucceed = false

    private var filter = ""
    private var sortType = SortType.LABEL_ASC

    fun refreshData() {
        if (isLoadSucceed) {
            if (_appsLiveData.value == null && apps.isNotEmpty()) {
                _appsLiveData.value = ArrayList(apps)
            }
            return
        }

        viewModelScope.launch {
            _loadingLiveData.value = true
            try {
                val appList = withContext(Dispatchers.IO) {
                    val pm = getApplication<Application>().packageManager
                    originalBlockedApps = DBManager.get(getApplication()).queryAllBlockedAppsSuspend()
                    
                    val installedApps = pm.getInstalledApplications(0)
                    installedApps.asSequence()
                        .map { AppInfoHelper.getAppInfo(pm, it) }
                        .onEach { appInfo ->
                            if (originalBlockedApps.contains(appInfo)) {
                                appInfo.blocked = true
                            }
                        }
                        .sortedWith(mComparator)
                        .toList()
                }
                
                apps = appList
                _loadingLiveData.value = false
                _appsLiveData.value = ArrayList(appList)
                isLoadSucceed = true
            } catch (t: Throwable) {
                XLog.e("", t)
                _loadingLiveData.value = false
                _errorEvent.value = t
                isLoadSucceed = false
            }
        }
    }

    fun doFilter(newFilter: String) {
        sortWithFilter(sortType, newFilter)
    }

    fun doSort(newSortType: SortType) {
        sortWithFilter(newSortType, filter)
    }

    private fun sortWithFilter(newSortType: SortType, newFilter: String) {
        val lowerCaseFilter = newFilter.lowercase()
        if (sortType == newSortType && filter == lowerCaseFilter) {
            return
        }
        sortType = newSortType
        filter = lowerCaseFilter

        viewModelScope.launch {
            val filteredList = withContext(Dispatchers.Default) {
                apps.asSequence()
                    .filter { appInfo ->
                        val lowerLabel = appInfo.label?.lowercase() ?: ""
                        val lowerPkg = appInfo.packageName?.lowercase() ?: ""
                        lowerLabel.contains(filter) || lowerPkg.contains(filter)
                    }
                    .sortedWith(mComparator)
                    .toList()
            }
            _appsLiveData.value = ArrayList(filteredList)
        }
    }

    fun doItemClicked(item: AppInfo) {
        for (appInfo in apps) {
            if (appInfo.packageName == item.packageName) {
                appInfo.blocked = !appInfo.blocked
                break
            }
        }
    }

    fun saveData() {
        if (apps.isEmpty()) {
            _saveSuccessEvent.call()
            return
        }

        val blockedApps = apps.filter { it.blocked }

        if (blockedApps == originalBlockedApps) {
            _saveSuccessEvent.call()
            return
        }

        viewModelScope.launch {
            try {
                val savedList = withContext(Dispatchers.IO) {
                    val dbManager = DBManager.get(getApplication())
                    dbManager.deleteAllSuspend(AppInfo::class.java)
                    dbManager.insertOrReplaceInTxSuspend(AppInfo::class.java, blockedApps)
                    
                    EntityStoreManager.storeEntitiesToFile(
                        EntityType.BLOCKED_APP, blockedApps
                    )
                    blockedApps
                }
                originalBlockedApps = savedList
                _saveSuccessEvent.call()
            } catch (t: Throwable) {
                _saveFailedEvent.call()
            }
        }
    }

    private val mComparator = Comparator<AppInfo> { o1, o2 ->
        val result = o2.blocked.compareTo(o1.blocked)
        if (result != 0) return@Comparator result

        when (sortType) {
            SortType.LABEL_ASC -> compareString(o1.label, o2.label)
            SortType.PACKAGE_ASC -> compareString(o1.packageName, o2.packageName)
            SortType.LABEL_DESC -> compareString(o2.label, o1.label)
            SortType.PACKAGE_DESC -> compareString(o2.packageName, o1.packageName)
        }
    }

    private fun compareString(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1
        return s1.compareTo(s2, ignoreCase = true)
    }
}
