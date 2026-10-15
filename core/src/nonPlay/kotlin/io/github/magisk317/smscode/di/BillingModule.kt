package io.github.magisk317.smscode.di

import io.github.magisk317.smscode.billing.BillingProvider
import io.github.magisk317.smscode.billing.NoOpBillingProvider
import org.koin.dsl.module

val billingModule = module {
    single<BillingProvider> { NoOpBillingProvider() }
}
