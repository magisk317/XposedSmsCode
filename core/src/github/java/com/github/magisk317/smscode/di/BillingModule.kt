package com.github.magisk317.smscode.di

import com.github.magisk317.smscode.billing.BillingProvider
import com.github.magisk317.smscode.billing.NoOpBillingProvider
import org.koin.dsl.module

val billingModule = module {
    single<BillingProvider> { NoOpBillingProvider() }
}
