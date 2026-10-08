package io.github.magisk317.smscode.di

import io.github.magisk317.smscode.runtime.RuntimeBackupFacade
import io.github.magisk317.smscode.runtime.RuntimeCodeRecordFacade
import io.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import io.github.magisk317.smscode.runtime.AppPrefsFacade
import io.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.runtime.RuntimeStoreFacade
import io.github.magisk317.smscode.runtime.RuntimeUpdateFacade
import io.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import io.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import io.github.magisk317.smscode.runtime.bridge.UiPrefsAccess
import io.github.magisk317.smscode.runtime.bridge.UiStorageAccess
import io.github.magisk317.smscode.runtime.bridge.UiStoreAccess
import io.github.magisk317.smscode.runtime.bridge.UiUpdateAccess
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
}
