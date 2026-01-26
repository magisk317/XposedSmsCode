package com.tianma.xsmscode.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import java.io.File

/**
 * Utils for storage.
 */
object StorageUtils {

    @JvmStatic
    fun isSDCardMounted(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    /**
     * 获取日志路径
     */
    @JvmStatic
    fun getLogDir(context: Context): File? {
        return if (isSDCardMounted()) {
            context.getExternalFilesDir("log")
        } else {
            File(context.filesDir, "log")
        }
    }

    /**
     * 获取Crash日志路径
     */
    @JvmStatic
    fun getCrashLogDir(context: Context): File? {
        return if (isSDCardMounted()) {
            context.getExternalFilesDir("crash")
        } else {
            File(context.filesDir, "crash")
        }
    }

    /**
     * Get sdcard directory
     */
    @JvmStatic
    fun getSDCardDir(): File {
        return Environment.getExternalStorageDirectory()
    }

    /**
     * get sdcard public documents directory
     */
    @JvmStatic
    fun getPublicDocumentsDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    }

    @JvmStatic
    fun getSharedPreferencesFile(context: Context, preferencesName: String): File {
        val dataDir = ContextCompat.getDataDir(context)
        val prefsDir = File(dataDir, "shared_prefs")
        return File(prefsDir, "$preferencesName.xml")
    }

    /**
     * Get internal data dir. /data/data/<package_name>/
     */
    @JvmStatic
    fun getInternalDataDir(): File {
        return File(Environment.getDataDirectory(), "data/${BuildConfig.APPLICATION_ID}")
    }

    /**
     * Get internal files dir. /data/data/<package_name>/files/
     */
    @JvmStatic
    fun getInternalFilesDir(): File {
        return File(getInternalDataDir(), "files")
    }

    /**
     * Get external files dir. /sdcard/Android/data/<package_name>/files/
     */
    @JvmStatic
    fun getExternalFilesDir(): File {
        return File(Environment.getExternalStorageDirectory(), "Android/data/${BuildConfig.APPLICATION_ID}/files/")
    }

    /**
     * Get files dir
     */
    @JvmStatic
    fun getFilesDir(): File {
        return if (isSDCardMounted()) {
            val externalFilesDir = getExternalFilesDir()
            if (!externalFilesDir.exists()) {
                externalFilesDir.mkdirs()
            }
            externalFilesDir
        } else {
            getInternalFilesDir()
        }
    }

    /**
     * Set file world writable
     */
    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    @JvmStatic
    fun setFileWorldWritable(file: File, parentDepth: Int) {
        var currentFile: File? = file
        if (currentFile == null || !currentFile.exists()) {
            return
        }
        val actualDepth = parentDepth + 1
        for (i in 0 until actualDepth) {
            currentFile?.setExecutable(true, false)
            currentFile?.setWritable(true, false)
            currentFile?.setReadable(true, false)
            currentFile = currentFile?.parentFile
            if (currentFile == null) {
                break
            }
        }
    }

    /**
     * Set file world readable
     */
    @SuppressLint("SetWorldReadable")
    @JvmStatic
    fun setFileWorldReadable(file: File, parentDepth: Int) {
        var currentFile: File? = file
        if (currentFile == null || !currentFile.exists()) {
            return
        }
        for (i in 0 until parentDepth) {
            currentFile?.setReadable(true, false)
            currentFile?.setExecutable(true, false)
            currentFile = currentFile?.parentFile
            if (currentFile == null) {
                break
            }
        }
    }
}
