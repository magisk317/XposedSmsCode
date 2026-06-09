package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.xp.hook.code.AutoCancelReceiver
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade as NotificationUtils
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.verification.CodeNotificationReceiverHandler
import kotlinx.coroutines.runBlocking

class CodeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        CodeNotificationReceiverHandler.handleBroadcast(
            receiver = this,
            context = context,
            intent = intent,
            config = receiverConfig(context.applicationContext),
        )
    }

    private fun receiverConfig(context: Context): CodeNotificationReceiverHandler.Config {
        return CodeNotificationReceiverHandler.Config(
            source = "CodeNotificationReceiver",
            expectedAction = CodeNotificationBroadcastContract.ACTION_SHOW_CODE_NOTIFICATION,
            expectedTokenProvider = { receiverContext ->
                runBlocking {
                    AppPreferencesDataStore.getString(receiverContext, PrefConst.KEY_IPC_TOKEN, "")
                }
            },
            sentFromUidProvider = {
                getSentFromUid()
            },
            channelName = context.getString(R.string.channel_name_smscode_notification),
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
                groupKey = NotificationConst.GROUP_KEY_SMSCODE_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            fallbackTitleProvider = { appContext ->
                appContext.getString(R.string.app_name)
            },
            contentTextProvider = { appContext, code ->
                appContext.getString(R.string.code_notification_content, code)
            },
            createNotificationChannel = NotificationUtils::createNotificationChannel,
            createCopyIntent = CopyCodeReceiver::createIntent,
            createAutoCancelIntent = AutoCancelReceiver::createIntent,
            inspectDelivery = { appContext, channelId ->
                val diagnostics = NotificationUtils.inspectDelivery(appContext, channelId)
                CodeNotificationReceiverHandler.DeliveryDiagnostics(
                    canPost = diagnostics.canPost,
                    summary = diagnostics.summary(),
                )
            },
        )
    }
}
