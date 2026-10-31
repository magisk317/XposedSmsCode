package io.github.magisk317.smscode.data.db.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "app_info")
@Parcelize
@Serializable
data class AppInfo @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    @SerialName("packageName")
    val packageName: String = "",
    @ColumnInfo(name = "label")
    @SerialName("label")
    val label: String? = null,
    @ColumnInfo(name = "blocked")
    @SerialName("blocked")
    @get:JvmName("isBlocked")
    val blocked: Boolean = false,
) : Parcelable
