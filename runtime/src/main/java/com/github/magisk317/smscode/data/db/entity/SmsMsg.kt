package com.github.magisk317.smscode.data.db.entity

import android.content.Intent
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.magisk317.smscode.common.utils.XLog
import io.github.magisk317.smscode.domain.utils.SmsMessageUtils
import io.github.magisk317.smscode.runtime.common.record.SmsMsgRecord
import io.github.magisk317.smscode.runtime.common.sim.SmsRoutingIntentExtras
import io.github.magisk317.smscode.runtime.contract.sim.SmsRoutingMetadata
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Entity(
    tableName = "sms_msg",
    indices = [
        androidx.room.Index(value = ["sender", "body", "date", "msg_type"], unique = true),
        androidx.room.Index(
            value = ["package_name", "msg_type", "notify_channel_id", "date"],
            name = "index_sms_msg_pkg_type_channel_date",
        ),
    ],
)
@Parcelize
@Serializable
data class SmsMsg(
    @PrimaryKey(autoGenerate = true)
    @SerialName("id")
    override val id: Long? = null,

    @ColumnInfo(name = "sender")
    @SerialName("sender")
    override val sender: String? = null,

    @ColumnInfo(name = "body")
    @SerialName("body")
    override val body: String? = null,

    @ColumnInfo(name = "date")
    @SerialName("date")
    override val date: Long = 0,

    @ColumnInfo(name = "processed_time", defaultValue = "0")
    @SerialName("processedTime")
    override val processedTime: Long = 0L,

    @ColumnInfo(name = "company")
    @SerialName("company")
    override val company: String? = null,

    @ColumnInfo(name = "sms_code")
    @SerialName("code")
    override val smsCode: String? = null,

    @ColumnInfo(name = "package_name")
    @SerialName("packageName")
    override val packageName: String? = null,

    @ColumnInfo(name = "notify_channel_id", defaultValue = "''")
    @SerialName("notifyChannelId")
    override val notifyChannelId: String = "",

    @ColumnInfo(name = "sim_slot", defaultValue = "-1")
    @SerialName("simSlot")
    override val simSlot: Int = SmsRoutingMetadata.UNKNOWN_SIM_SLOT,

    @ColumnInfo(name = "sub_id", defaultValue = "0")
    @SerialName("subId")
    override val subId: Int = SmsRoutingMetadata.UNKNOWN_SUB_ID,

    @ColumnInfo(name = "forward_status")
    @SerialName("forwardStatus")
    override var forwardStatus: Int = FORWARD_STATUS_NONE,

    @ColumnInfo(name = "forward_target")
    @SerialName("forwardTarget")
    override var forwardTarget: String? = null,

    @ColumnInfo(name = "forward_message")
    @SerialName("forwardMessage")
    override var forwardMessage: String? = null,

    @ColumnInfo(name = "forward_time")
    @SerialName("forwardTime")
    override var forwardTime: Long = 0L,

    @ColumnInfo(name = "msg_type", defaultValue = "0")
    @SerialName("msgType")
    override val msgType: Int = MSG_TYPE_SMS,

    @ColumnInfo(name = "call_type", defaultValue = "0")
    @SerialName("callType")
    override val callType: Int = 0,

) : Parcelable, SmsMsgRecord {

    companion object {
        const val FORWARD_STATUS_NONE = 0
        const val FORWARD_STATUS_SUCCESS = 1
        const val FORWARD_STATUS_FAILED = 2
        const val FORWARD_STATUS_PARTIAL = 3
        const val FORWARD_STATUS_BLOCKED = 4

        const val MSG_TYPE_SMS = 0
        const val MSG_TYPE_APP_NOTIFY = 1
        const val MSG_TYPE_CALL_NOTIFY = 2

        @JvmStatic
        fun fromIntent(intent: Intent): SmsMsg {
            val smsMessageParts = SmsMessageUtils.fromIntent(intent)
            if (smsMessageParts.isEmpty()) return SmsMsg()

            var sender = smsMessageParts[0].displayOriginatingAddress
            var body = SmsMessageUtils.getMessageBody(smsMessageParts)
            val date = smsMessageParts[0].timestampMillis

            sender = Normalizer.normalize(sender, Normalizer.Form.NFC)
            body = Normalizer.normalize(body, Normalizer.Form.NFC)
            val routing = SmsRoutingIntentExtras.readFrom(intent)
            XLog.i(
                "Diag SMS routing from intent: simSlot=%d subId=%d extras=%s",
                routing.simSlot ?: SmsRoutingMetadata.UNKNOWN_SIM_SLOT,
                routing.subId ?: SmsRoutingMetadata.UNKNOWN_SUB_ID,
                intent.extras != null,
            )

            return SmsMsg(
                sender = sender,
                body = body,
                date = date,
                simSlot = routing.simSlot ?: SmsRoutingMetadata.UNKNOWN_SIM_SLOT,
                subId = routing.subId ?: SmsRoutingMetadata.UNKNOWN_SUB_ID,
                msgType = MSG_TYPE_SMS,
            )
        }
    }
}
