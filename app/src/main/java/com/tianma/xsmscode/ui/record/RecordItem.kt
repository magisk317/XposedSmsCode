package com.tianma.xsmscode.ui.record

import com.tianma.xsmscode.data.db.entity.SmsMsg

data class RecordItem(val smsMsg: SmsMsg) {
    var isSelected: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordItem) return false
        return smsMsg == other.smsMsg
    }

    override fun hashCode(): Int {
        return smsMsg.hashCode()
    }
}
