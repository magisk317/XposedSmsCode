package com.github.magisk317.smscode.receiver

import android.content.Context
import android.os.Build
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade as NotificationUtils
import com.github.magisk317.smscode.xp.hook.code.AutoCancelReceiver
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.verification.CodeNotificationReceiverHandler
import kotlinx.coroutines.runBlocking

object CodeNotificationReceiverConfig {
    fun create(
        context: Context,
        source: String,
        sentFromUidProvider: () -> Int,
    ): CodeNotificationReceiverHandler.Config {
        val appContext = context.applicationContext ?: context
        return CodeNotificationReceiverHandler.Config(
            source = source,
            expectedAction = CodeNotificationBroadcastContract.ACTION_SHOW_CODE_NOTIFICATION,
            expectedTokenProvider = { receiverContext ->
                runBlocking {
                    AppPreferencesDataStore.getString(receiverContext, PrefConst.KEY_IPC_TOKEN, "")
                }
            },
            sentFromUidProvider = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    sentFromUidProvider()
                } else {
                    -1
                }
            },
            channelName = appContext.getString(R.string.channel_name_smscode_notification),
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
                groupKey = NotificationConst.GROUP_KEY_SMSCODE_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            fallbackTitleProvider = { receiverContext ->
                receiverContext.getString(R.string.app_name)
            },
            contentTextProvider = { receiverContext, code ->
                receiverContext.getString(R.string.code_notification_content, code)
            },
            createNotificationChannel = NotificationUtils::createNotificationChannel,
            createCopyIntent = CopyCodeReceiver::createIntent,
            createAutoCancelIntent = AutoCancelReceiver::createIntent,
            inspectDelivery = { receiverContext, channelId ->
                val diagnostics = NotificationUtils.inspectDelivery(receiverContext, channelId)
                CodeNotificationReceiverHandler.DeliveryDiagnostics(
                    canPost = diagnostics.canPost,
                    summary = diagnostics.summary(),
                )
            },
        )
    }
}
