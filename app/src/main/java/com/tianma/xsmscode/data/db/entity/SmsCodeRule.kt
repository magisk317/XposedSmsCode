package com.tianma.xsmscode.data.db.entity

import android.os.Parcelable
import androidx.room.*
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.tianma.xsmscode.feature.backup.BackupConst
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "sms_code_rule",
    indices = [
        Index(value = ["company", "code_keyword", "code_regex"], unique = true)
    ]
)
@Parcelize
data class SmsCodeRule @JvmOverloads constructor(
    @ColumnInfo(name = "company")
    @SerializedName(BackupConst.KEY_COMPANY)
    @Expose
    var company: String? = null,

    @ColumnInfo(name = "code_keyword")
    @SerializedName(BackupConst.KEY_CODE_KEYWORD)
    @Expose
    var codeKeyword: String = "",

    @ColumnInfo(name = "code_regex")
    @SerializedName(BackupConst.KEY_CODE_REGEX)
    @Expose
    var codeRegex: String = "",

    @PrimaryKey(autoGenerate = true)
    var id: Long? = null
) : Parcelable {

    fun copyFrom(newRule: SmsCodeRule) {
        this.id = newRule.id
        this.company = newRule.company
        this.codeKeyword = newRule.codeKeyword
        this.codeRegex = newRule.codeRegex
    }
}
