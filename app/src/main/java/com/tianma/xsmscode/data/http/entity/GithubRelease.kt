package com.tianma.xsmscode.data.http.entity

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    @SerializedName("tag_name")
    val tagName: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("body")
    val body: String? = null
)
