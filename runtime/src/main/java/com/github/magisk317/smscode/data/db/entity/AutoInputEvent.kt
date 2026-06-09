package com.github.magisk317.smscode.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "auto_input_event",
    indices = [
        Index(value = ["attempt_at"], name = "index_auto_input_attempt_at"),
        Index(value = ["record_id"], name = "index_auto_input_record"),
    ],
)
data class AutoInputEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "record_id")
    val recordId: Long? = null,

    @ColumnInfo(name = "package_name")
    val packageName: String? = null,

    @ColumnInfo(name = "code_length")
    val codeLength: Int = 0,

    @ColumnInfo(name = "attempt_at")
    val attemptAt: Long,

    @ColumnInfo(name = "success")
    val success: Boolean? = null,

    @ColumnInfo(name = "fail_reason")
    val failReason: String? = null,
)
