package com.github.magisk317.smscode.di

import com.github.magisk317.smscode.runtime.RuntimeBackupFacade
import com.github.magisk317.smscode.runtime.RuntimeCodeRecordFacade
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import com.github.magisk317.smscode.runtime.AppPrefsFacade
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import com.github.magisk317.smscode.runtime.RuntimeStoreFacade
import com.github.magisk317.smscode.runtime.RuntimeUpdateFacade
import com.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import com.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import com.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import com.github.magisk317.smscode.runtime.bridge.UiPrefsAccess
import com.github.magisk317.smscode.runtime.bridge.UiStorageAccess
import com.github.magisk317.smscode.runtime.bridge.UiStoreAccess
import com.github.magisk317.smscode.runtime.bridge.UiUpdateAccess
import com.github.magisk317.smscode.ui.home.AppConfigViewModel
import com.github.magisk317.smscode.ui.home.SettingsViewModel
import com.github.magisk317.smscode.ui.record.CodeRecordViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<UiStorageAccess> { RuntimeStorageFacade }
    single<UiStoreAccess> { RuntimeStoreFacade }
    single<UiBackupAccess> { RuntimeBackupFacade }
    single<UiCodeRecordAccess> { RuntimeCodeRecordFacade }
    single<UiNotificationAccess> { RuntimeNotificationFacade }
    single<UiPrefsAccess> { AppPrefsFacade }
    single<UiUpdateAccess> { RuntimeUpdateFacade }

    // Database
    single { get<UiStorageAccess>().appDatabase(get()) }

    // ViewModels
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
}
