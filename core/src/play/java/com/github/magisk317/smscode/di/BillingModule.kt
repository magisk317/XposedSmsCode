package com.github.magisk317.smscode.di

import com.github.magisk317.smscode.app.AppInitializer
import com.github.magisk317.smscode.billing.BillingInitializer
import com.github.magisk317.smscode.billing.BillingManager
import com.github.magisk317.smscode.billing.BillingProvider
import com.github.magisk317.smscode.billing.PlayBillingProvider
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    single { BillingManager(get()) }
    single<BillingProvider> { PlayBillingProvider(get()) }
    single { BillingInitializer(get()) } bind AppInitializer::class
}
