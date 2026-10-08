package io.github.magisk317.smscode.runtime

import android.content.Context
import io.github.magisk317.smscode.db.entity.AppInfo
import io.github.magisk317.smscode.feature.store.EntityStoreManager
import io.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.runtime.bridge.UiStoreAccess

object RuntimeStoreFacade : UiStoreAccess {
    override fun persistAppConfigs(context: Context, appConfigs: List<AppInfo>): Boolean {
        return EntityStoreManager.storeEntitiesToFile(
            context = context,
            entityType = EntityType.APP_CONFIG,
            entities = appConfigs,
            clazz = AppInfo::class.java,
        )
    }
}
