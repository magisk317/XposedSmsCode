package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.ObservedInboxScanner as SharedObservedInboxScanner
import io.github.magisk317.smscode.verification.SmsInboxSeenTracker

internal class ObservedInboxScanner(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val smsIdTracker: SmsInboxSeenTracker,
    private val smsCodeParser: suspend (Context, String) -> String = { context, body ->
        SmsCodeUtils.parseSmsCodeIfExists(context, body)
    },
    private val inboxRowLoader: ((Long, Long?) -> List<InboxRow>)? = null,
) {
    data class InboxRow(
        val smsId: Long,
        val sender: String,
        val body: String,
        val date: Long,
        val read: Boolean,
        val simSlot: Int = -1,
        val subId: Int = 0,
    )

    private val delegate = createDelegate()

    fun scan(triggerUri: String, recentSmsWindowMs: Long): List<ObservedInboxScanRecord> {
        return delegate.scan(triggerUri, recentSmsWindowMs)
    }

    fun scanRouting(triggerUri: String, recentSmsWindowMs: Long): List<ObservedInboxScanRecord> {
        return delegate.scanRouting(triggerUri, recentSmsWindowMs)
    }

    private fun createDelegate(): SharedObservedInboxScanner {
        val mappedLoader: ((Long, Long?) -> List<SharedObservedInboxScanner.InboxRow>)? =
            inboxRowLoader?.let { loader ->
                { cutoff: Long, triggeredSmsId: Long? ->
                    loader(cutoff, triggeredSmsId).map { row ->
                        SharedObservedInboxScanner.InboxRow(
                            smsId = row.smsId,
                            sender = row.sender,
                            body = row.body,
                            date = row.date,
                            read = row.read,
                            simSlot = row.simSlot,
                            subId = row.subId,
                        )
                    }
                }
            }
        return if (mappedLoader == null) {
            SharedObservedInboxScanner(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsIdTracker = smsIdTracker,
                smsCodeParser = smsCodeParser,
            )
        } else {
            SharedObservedInboxScanner(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsIdTracker = smsIdTracker,
                smsCodeParser = smsCodeParser,
                inboxRowLoader = mappedLoader,
            )
        }
    }
}
