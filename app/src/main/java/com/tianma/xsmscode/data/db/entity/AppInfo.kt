package com.tianma.xsmscode.data.db.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Entity(tableName = "app_info")
@Parcelize
data class AppInfo @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    @SerializedName("packageName")
    @Expose
    var packageName: String = "",

    @ColumnInfo(name = "label")
    @SerializedName("label")
    @Expose
    var label: String? = null,

    @ColumnInfo(name = "blocked")
    @SerializedName("blocked")
    @Expose
    @get:JvmName("isBlocked")
    var blocked: Boolean = false
) : Parcelable
