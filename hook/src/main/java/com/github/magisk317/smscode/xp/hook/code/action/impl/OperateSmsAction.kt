package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import androidx.annotation.IntDef
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.common.utils.HookPrefsReader
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage
import io.github.magisk317.smscode.runtime.verification.OperateSmsActionHelper

class OperateSmsAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    constructor(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        @SmsOp forcedOp: Int,
    ) : this(pluginContext, phoneContext, smsMsg) {
        this.forcedOp = forcedOp
    }

    @IntDef(FORCE_DELETE, OP_MARK_AS_READ)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class SmsOp

    @SmsOp
    private var forcedOp: Int? = null

    override fun action(): Bundle? {
        OperateSmsActionHelper(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsMsg = mSmsMsg.toVerificationMessage(),
            deleteSmsEnabledReader = HookPrefsReader::deleteSmsEnabled,
            markAsReadEnabledReader = HookPrefsReader::markAsReadEnabled,
        ).execute(
            OperateSmsActionHelper.resolveForcedOperation(
                forcedOperation = forcedOp,
                deleteValue = OP_DELETE,
                markAsReadValue = OP_MARK_AS_READ,
            ),
        )
        return null
    }

    companion object {
        const val FORCE_DELETE = 0
        private const val OP_DELETE = FORCE_DELETE
        private const val OP_MARK_AS_READ = 1
    }
}
