package com.github.magisk317.smscode.xp.hook.code

import android.os.Parcelable
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.SmsMessage
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class VerificationSmsMsg(
    val raw: SmsMsg,
) : SmsMessage, Parcelable {
    override val sender: String?
        get() = raw.sender
    override val body: String?
        get() = raw.body
    override val date: Long
        get() = raw.date
    override val company: String?
        get() = raw.company
    override val smsCode: String?
        get() = raw.smsCode
    override val packageName: String?
        get() = raw.packageName
    override val simSlot: Int
        get() = raw.simSlot
    override val subId: Int
        get() = raw.subId
}

internal fun SmsMsg.toVerificationMessage(): VerificationSmsMsg = VerificationSmsMsg(this)
