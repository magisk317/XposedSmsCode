package com.tianma.xsmscode.feature.backup

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.backup.exception.BackupInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionMissedException
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * SmsCode rule importer
 */
class RuleImporter(private val mJsonStream: InputStream?) : Closeable {

    constructor(file: File?) : this(FileInputStream(file))

    /**
     * perform import data from backup file.
     * @param context context
     * @param retain whether it retains current rules or not.
     */
    @Throws(BackupInvalidException::class)
    fun doImport(context: Context, retain: Boolean) {
        val jsonReader = JsonReader(InputStreamReader(mJsonStream))

        try {
            val jsonElement = JsonParser.parseReader(jsonReader)
            if (!jsonElement.isJsonObject) {
                throw BackupInvalidException()
            }
            val jsonObject = jsonElement.asJsonObject

            if (jsonObject.has(BackupConst.KEY_VERSION)) {
                val version = jsonObject[BackupConst.KEY_VERSION].asInt
                if (version == 1) {
                    doImportVersion1(context, jsonObject, retain)
                } else {
                    throw VersionInvalidException("Invalid backup version")
                }
            } else {
                throw VersionMissedException("Backup version property missed")
            }
        } catch (ex: JsonParseException) {
            // json syntax exception or json parse exception
            throw BackupInvalidException(ex)
        } catch (ex: VersionInvalidException) {
            throw ex
        } catch (ex: VersionMissedException) {
            throw ex
        } catch (ex: Exception) {
            throw BackupInvalidException(ex)
        }
    }

    @Throws(BackupInvalidException::class)
    private fun doImportVersion1(context: Context, jsonObject: JsonObject, retain: Boolean) {
        val ruleArray = jsonObject[BackupConst.KEY_RULES].asJsonArray ?: return
        val ruleList = readRuleList(ruleArray)
        if (ruleList.isNotEmpty()) {
            writeRuleListToDB(context, ruleList, retain)
        }
    }

    @Throws(BackupInvalidException::class)
    private fun readRuleList(ruleArray: com.google.gson.JsonArray): List<SmsCodeRule> {
        val ruleList = ArrayList<SmsCodeRule>()
        for (ruleJson in ruleArray) {
            ruleList.add(readRule(ruleJson.asJsonObject))
        }
        return ruleList
    }

    @Throws(BackupInvalidException::class)
    private fun readRule(ruleObject: JsonObject): SmsCodeRule {
        return try {
            val company = ruleObject[BackupConst.KEY_COMPANY].asString
            val codeKeyword = ruleObject[BackupConst.KEY_CODE_KEYWORD].asString
            val codeRegex = ruleObject[BackupConst.KEY_CODE_REGEX].asString

            SmsCodeRule(company = company, codeKeyword = codeKeyword, codeRegex = codeRegex)
        } catch (e: Exception) {
            throw BackupInvalidException(e)
        }
    }

    private fun writeRuleListToDB(context: Context, ruleList: List<SmsCodeRule>, retain: Boolean) {
        val dbManager = DBManager.get(context)
        if (!retain) {
            dbManager.removeAllSmsCodeRules()
        }
        dbManager.addSmsCodeRules(ruleList)
    }

    override fun close() {
        if (mJsonStream != null) {
            try {
                mJsonStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
