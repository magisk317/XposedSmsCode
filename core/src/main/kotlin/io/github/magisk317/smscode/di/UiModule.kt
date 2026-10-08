package io.github.magisk317.smscode.di

import io.github.magisk317.smscode.ui.home.AppConfigViewModel
import io.github.magisk317.smscode.ui.home.SettingsViewModel
import io.github.magisk317.smscode.ui.record.CodeRecordViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
}
