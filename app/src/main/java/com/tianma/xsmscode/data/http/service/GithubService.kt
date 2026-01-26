package com.tianma.xsmscode.data.http.service

import com.tianma.xsmscode.data.http.entity.GithubRelease
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit Service for GitHub
 */
interface GithubService {

    @GET("/repos/{username}/{repoName}/releases/latest")
    suspend fun getLatestRelease(
        @Path("username") username: String,
        @Path("repoName") repoName: String
    ): GithubRelease
}
