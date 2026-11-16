package io.github.magisk317.smscode.runtime

import android.content.Context
import io.github.magisk317.smscode.feature.store.EntityStoreManager
import io.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.runtime.bridge.UiAppConfig
import io.github.magisk317.smscode.runtime.bridge.UiStoreAccess

object RuntimeStoreFacade : UiStoreAccess {
    override fun persistAppConfigs(context: Context, appConfigs: List<UiAppConfig>): Boolean {
        return EntityStoreManager.storeEntitiesToFile(
            context = context,
            entityType = EntityType.APP_CONFIG,
            entities = appConfigs,
            clazz = UiAppConfig::class.java,
        )
    }
}
