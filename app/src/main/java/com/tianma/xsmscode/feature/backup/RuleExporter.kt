package com.tianma.xsmscode.feature.backup

import com.google.gson.stream.JsonWriter
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * SmsCode rules exporter
 */
class RuleExporter(out: OutputStream?) : Closeable {
    private val mJsonWriter: JsonWriter

    init {
        val osw = OutputStreamWriter(out, StandardCharsets.UTF_8)
        mJsonWriter = JsonWriter(osw)
        mJsonWriter.setIndent("\t") // pretty print
    }

    constructor(file: File?) : this(FileOutputStream(file))

    @Throws(IOException::class)
    fun doExport(ruleList: List<SmsCodeRule>) {
        begin()
        exportRuleList(ruleList)
        end()
    }

    @Throws(IOException::class)
    private fun begin() {
        mJsonWriter.beginObject()
        mJsonWriter.name(BackupConst.KEY_VERSION)
            .value(BackupConst.BACKUP_VERSION.toLong())
    }

    @Throws(IOException::class)
    private fun exportRuleList(ruleList: List<SmsCodeRule>) {
        mJsonWriter.name(BackupConst.KEY_RULES)
            .beginArray()
        for (rule in ruleList) {
            exportRule(rule)
        }
        mJsonWriter.endArray()
    }

    @Throws(IOException::class)
    private fun exportRule(rule: SmsCodeRule) {
        mJsonWriter.beginObject()
        mJsonWriter.name(BackupConst.KEY_COMPANY).value(rule.company)
        mJsonWriter.name(BackupConst.KEY_CODE_KEYWORD).value(rule.codeKeyword)
        mJsonWriter.name(BackupConst.KEY_CODE_REGEX).value(rule.codeRegex)
        mJsonWriter.endObject()
    }

    @Throws(IOException::class)
    private fun end() {
        mJsonWriter.endObject()
    }

    override fun close() {
        try {
            mJsonWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
