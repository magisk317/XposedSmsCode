package com.tianma.xsmscode.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.utils.StorageUtils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.backup.exception.BackupInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionMissedException
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val BACKUP_DIRECTORY = "SmsCode"
    private const val BACKUP_FILE_EXTENSION = ".scebak"
    private const val BACKUP_FILE_NAME_PREFIX = "SmsCode-"

    private const val BACKUP_MIME_TYPE = "application/json"
    private const val BACKUP_FILE_AUTHORITY = BuildConfig.APPLICATION_ID + ".files"

    @JvmStatic
    fun getBackupDir(): File? {
        val sdcard = StorageUtils.getPublicDocumentsDir() ?: return null
        return File(sdcard, BACKUP_DIRECTORY)
    }

    @JvmStatic
    fun getBackupFileExtension(): String {
        return BACKUP_FILE_EXTENSION
    }

    @JvmStatic
    fun getDefaultBackupFilename(): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val backupDir = getBackupDir()
        val basename = BACKUP_FILE_NAME_PREFIX + dateStr
        var filename = basename + BACKUP_FILE_EXTENSION
        var i = 2
        while (File(backupDir, filename).exists()) {
            filename = "$basename-$i$BACKUP_FILE_EXTENSION"
            i++
        }
        return filename
    }

    @JvmStatic
    fun getBackupFiles(): Array<File>? {
        val backupDir = getBackupDir() ?: return null
        if (!backupDir.exists()) return null
        val files = backupDir.listFiles { _, name -> name.endsWith(BACKUP_FILE_EXTENSION) }

        if (files != null) {
            Arrays.sort(files) { f1: File, f2: File ->
                val s1 = f1.name
                val s2 = f2.name
                val extLength = BACKUP_FILE_EXTENSION.length
                val n1 = s1.substring(0, s1.length - extLength)
                val n2 = s2.substring(0, s2.length - extLength)
                n1.compareTo(n2)
            }
        }
        return files
    }

    @JvmStatic
    fun exportRuleList(file: File, ruleList: List<SmsCodeRule>): ExportResult {
        val parentFile = file.parentFile
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs()
        }

        try {
            RuleExporter(file).use { exporter ->
                exporter.doExport(ruleList)
                return ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            XLog.e("Export SmsCode rules failed", e)
            return ExportResult.FAILED
        }
    }

    @JvmStatic
    fun exportRuleList(context: Context, uri: Uri, ruleList: List<SmsCodeRule>): ExportResult {
        try {
            RuleExporter(context.contentResolver.openOutputStream(uri)).use { exporter ->
                exporter.doExport(ruleList)
                return ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            XLog.e("Export SmsCode rules failed", e)
            return ExportResult.FAILED
        }
    }

    /**
     * 获取导出规则列表的 SAF (Storage Access Framework) 的 Intent
     */
    @JvmStatic
    fun getExportRuleListSAFIntent(): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = BACKUP_MIME_TYPE
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename())

        return intent
    }

    @JvmStatic
    fun importRuleList(context: Context, uri: Uri, retain: Boolean): ImportResult {
        var ruleImporter: RuleImporter? = null
        try {
            ruleImporter = RuleImporter(context.contentResolver.openInputStream(uri))
            ruleImporter.doImport(context, retain)
            return ImportResult.SUCCESS
        } catch (e: IOException) {
            XLog.e("Error occurs in importRuleList", e)
            return ImportResult.READ_FAILED
        } catch (e: VersionMissedException) {
            XLog.e("Error occurs in importRuleList", e)
            return ImportResult.VERSION_MISSED
        } catch (e: VersionInvalidException) {
            XLog.e("Error occurs in importRuleList", e)
            return ImportResult.VERSION_UNKNOWN
        } catch (e: BackupInvalidException) {
            XLog.e("Error occurs in importRuleList", e)
            return ImportResult.BACKUP_INVALID
        } finally {
            if (ruleImporter != null) {
                ruleImporter.close()
            }
        }
    }

    /**
     * 获取导入规则列表的 SAF (Storage Access Framework) 的 Intent
     */
    @JvmStatic
    fun getImportRuleListSAFIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = BACKUP_MIME_TYPE
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename())

        return intent
    }

    @JvmStatic
    fun shareBackupFile(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_SEND)

        val uri = FileProvider.getUriForFile(context, BACKUP_FILE_AUTHORITY, file)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = BACKUP_MIME_TYPE
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(Intent.createChooser(intent, null))
    }
}
