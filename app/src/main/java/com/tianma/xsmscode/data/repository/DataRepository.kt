package com.tianma.xsmscode.data.repository

import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.data.db.entity.ApkVersion
import com.tianma.xsmscode.data.http.ApiConst
import com.tianma.xsmscode.data.http.service.CoolApkService
import com.tianma.xsmscode.data.http.service.GithubService
import com.tianma.xsmscode.data.http.service.ServiceGenerator
import java.util.Locale

object DataRepository {

    private fun isInChina(): Boolean {
        val locale = Locale.getDefault()
        val language = locale.language
        return "zh".equals(language, ignoreCase = true)
    }

    suspend fun getLatestVersion(): ApkVersion {
        val isInChina = isInChina()

        val coolApkService = ServiceGenerator.getInstance()
            .createService(ApiConst.COOLAPK_BASE_URL, CoolApkService::class.java)
        
        val githubService = ServiceGenerator.getInstance()
            .createService(ApiConst.GITHUB_BASE_URL, GithubService::class.java)

        return if (isInChina) {
            try {
                getFromCoolApk(coolApkService)
            } catch (e: Exception) {
                getFromGithub(githubService, isInChina)
            }
        } else {
            try {
                getFromGithub(githubService, isInChina)
            } catch (e: Exception) {
                getFromCoolApk(coolApkService)
            }
        }
    }

    private suspend fun getFromCoolApk(service: CoolApkService): ApkVersion {
        val html = service.getLatestRelease(BuildConfig.APPLICATION_ID)
        return ApkVersionHelper.parseFromCoolApk(html)
    }

    private suspend fun getFromGithub(service: GithubService, isInChina: Boolean): ApkVersion {
        val release = service.getLatestRelease(ApiConst.GITHUB_USERNAME, ApiConst.GITHUB_REPO_NAME)
        val regex = "<br/>|<br>"
        val body = release.body ?: ""
        val arr = body.split(regex.toRegex()).toTypedArray()
        val versionInfo: String = if (arr.size >= 2) {
            if (isInChina) arr[1].trim() else arr[0].trim()
        } else {
            body.replace(regex.toRegex(), "")
        }
        return ApkVersion(release.name ?: "", versionInfo)
    }
}
