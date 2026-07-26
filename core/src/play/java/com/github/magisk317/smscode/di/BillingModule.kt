package com.github.magisk317.smscode.di

import io.github.magisk317.uikit.billing.BillingInitializer
import io.github.magisk317.uikit.billing.BillingManager
import io.github.magisk317.uikit.shell.AppInitializer
import com.github.magisk317.smscode.billing.BillingProvider
import com.github.magisk317.smscode.billing.PlayBillingProvider
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    single { BillingManager(get()) }
    single<BillingProvider> { PlayBillingProvider(get()) }
    single { BillingInitializer(get()) } bind AppInitializer::class
}
