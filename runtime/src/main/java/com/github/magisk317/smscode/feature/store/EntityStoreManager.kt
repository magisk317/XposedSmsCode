package com.github.magisk317.smscode.feature.store

import android.content.Context
import io.github.magisk317.smscode.runtime.common.store.JsonEntityFileStore
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.smscode.xposed.utils.XLog
import java.io.File

/**
 * Put and get blocked app info in files.
 */
object EntityStoreManager {

    private const val CODE_RULE_TEMPLATE_FILE_NAME = "code_rule_template"
    private const val CODE_RULES_FILE_NAME = "code_rules"
    private const val BLOCKED_APPS_FILE_NAME = "blocked_apps"
    private const val APP_CONFIGS_FILE_NAME = "app_configs"
    private const val PREV_CODE_RECORD = "prev_code_record"

    private val delegate = JsonEntityFileStore<EntityType>(
        fileResolver = { context, entityType ->
            File(StorageUtils.getFilesDir(context), fileName(entityType))
        },
        prepareFileForCommit = { file -> StorageUtils.setFileWorldWritable(file, 0) },
        logger = JsonEntityFileStore.Logger { message, throwable ->
            if (throwable == null) XLog.e(message) else XLog.e(message, throwable)
        },
    )

    fun getStoreFile(context: Context, entityType: EntityType): File = delegate.getStoreFile(context, entityType)

    @JvmStatic
    fun <T : Any> storeEntitiesToFile(
        context: Context,
        entityType: EntityType,
        entities: List<T>,
        clazz: Class<T>,
    ): Boolean = delegate.storeEntities(context, entityType, entities, clazz)

    @JvmStatic
    fun <T : Any> storeEntityToFile(context: Context, entityType: EntityType, entity: T, clazz: Class<T>): Boolean =
        delegate.storeEntity(context, entityType, entity, clazz)

    @JvmStatic
    fun <T : Any> loadEntitiesFromFile(context: Context, entityType: EntityType, entityClass: Class<T>): List<T> =
        delegate.loadEntities(context, entityType, entityClass)

    @JvmStatic
    fun <T : Any> loadEntitiesFromFile(storeFile: File, entityClass: Class<T>): List<T> =
        delegate.loadEntities(storeFile, entityClass)

    @JvmStatic
    fun <T : Any> loadEntityFromFile(context: Context, entityType: EntityType, entityClass: Class<T>): T? =
        delegate.loadEntity(context, entityType, entityClass)

    private fun fileName(entityType: EntityType): String = when (entityType) {
        EntityType.BLOCKED_APP -> BLOCKED_APPS_FILE_NAME
        EntityType.APP_CONFIG -> APP_CONFIGS_FILE_NAME
        EntityType.CODE_RULES -> CODE_RULES_FILE_NAME
        EntityType.CODE_RULE_TEMPLATE -> CODE_RULE_TEMPLATE_FILE_NAME
        EntityType.PREV_SMS_MSG -> PREV_CODE_RECORD
    }
}
