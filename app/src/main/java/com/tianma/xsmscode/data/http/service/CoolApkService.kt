package com.tianma.xsmscode.data.http.service

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * retrofit service for coolapk.com
 */
interface CoolApkService {

    @GET("/apk/{packageName}")
    suspend fun getLatestRelease(@Path("packageName") packageName: String): String
}
