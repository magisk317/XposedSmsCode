package com.tianma.xsmscode.feature.store

import com.tianma.xsmscode.common.utils.JsonUtils
import com.tianma.xsmscode.common.utils.StorageUtils
import com.tianma.xsmscode.common.utils.XLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.ArrayList

/**
 * Put and get blocked app info in files.
 */
object EntityStoreManager {

    private val CODE_RULE_TEMPLATE_FILE_NAME = "code_rule_template"
    private val CODE_RULES_FILE_NAME = "code_rules"
    private val BLOCKED_APPS_FILE_NAME = "blocked_apps"
    private val PREV_CODE_RECORD = "prev_code_record"

    private fun getStoreFile(entityType: EntityType): File {
        val filename = when (entityType) {
            EntityType.BLOCKED_APP -> BLOCKED_APPS_FILE_NAME
            EntityType.CODE_RULES -> CODE_RULES_FILE_NAME
            EntityType.CODE_RULE_TEMPLATE -> CODE_RULE_TEMPLATE_FILE_NAME
            EntityType.PREV_SMS_MSG -> PREV_CODE_RECORD
        }
        return File(StorageUtils.getFilesDir(), filename)
    }

    @JvmStatic
    fun <T> storeEntitiesToFile(entityType: EntityType, entities: List<T>): Boolean {
        var osw: OutputStreamWriter? = null
        try {
            val storeFile = getStoreFile(entityType)
            osw = OutputStreamWriter(FileOutputStream(storeFile), StandardCharsets.UTF_8)

            JsonUtils.toJson(entities, osw, true)

            // set file world writable
            StorageUtils.setFileWorldWritable(storeFile, 0)
            return true
        } catch (e: Exception) {
            XLog.e("store entities to file failed", e)
        } finally {
            if (osw != null) {
                try {
                    osw.close()
                } catch (ioException: IOException) {
                    // ignore
                }
            }
        }
        return false
    }

    @JvmStatic
    fun <T> storeEntityToFile(entityType: EntityType, entity: T): Boolean {
        val entities = ArrayList<T>()
        entities.add(entity)
        return storeEntitiesToFile(entityType, entities)
    }

    @JvmStatic
    fun <T> loadEntitiesFromFile(entityType: EntityType, entityClass: Class<T>): List<T> {
        val storeFile = getStoreFile(entityType)
        if (!storeFile.exists()) {
            return ArrayList()
        }
        var isr: InputStreamReader? = null
        try {
            isr = InputStreamReader(
                FileInputStream(storeFile), StandardCharsets.UTF_8
            )

            return JsonUtils.listFromJson(isr, entityClass, true)
        } catch (e: Exception) {
            XLog.e("load entities from file failed", e)
        } finally {
            if (isr != null) {
                try {
                    isr.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return ArrayList()
    }

    @JvmStatic
    fun <T> loadEntityFromFile(entityType: EntityType, entityClass: Class<T>): T? {
        val entities = loadEntitiesFromFile(entityType, entityClass)
        return if (entities.isNotEmpty()) {
            entities[0]
        } else null
    }
}
