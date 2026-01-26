package com.tianma.xsmscode.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import androidx.core.content.ContextCompat
import com.tianma.xsmscode.common.utils.ClipboardUtils

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver private constructor() : BroadcastReceiver() {

    private var mPluginContext: Context? = null

    override fun onReceive(phoneContext: Context, intent: Intent) {
        val action = intent.action
        if (ACTION_COPY_CODE == action) {
            val smsCode = intent.getStringExtra(EXTRA_KEY_CODE)
            // copy to clipboard
            smsCode?.let {
                ClipboardUtils.copyToClipboard(phoneContext, it)
                // show toast
                val pluginContext = createSmsCodeAppContext(phoneContext)
                showToast(pluginContext, phoneContext, it)
            }
        }
    }

    private fun createSmsCodeAppContext(phoneContext: Context): Context? {
        if (mPluginContext == null) {
            try {
                mPluginContext = phoneContext.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY
                )
            } catch (e: Exception) {
                // ignore
            }
        }
        return mPluginContext
    }

    private fun showToast(pluginContext: Context?, phoneContext: Context?, smsCode: String) {
        pluginContext?.let {
            val text = it.getString(R.string.prompt_sms_code_copied, smsCode)
            phoneContext?.let { pc ->
                Toast.makeText(pc, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val ACTION_COPY_CODE = "${BuildConfig.APPLICATION_ID}.ACTION_COPY_CODE"
        private const val EXTRA_KEY_CODE = "extra_key_code"

        private val instance: CopyCodeReceiver by lazy { CopyCodeReceiver() }

        @JvmStatic
        fun createIntent(smsCode: String?): Intent {
            val intent = Intent(ACTION_COPY_CODE)
            intent.putExtra(EXTRA_KEY_CODE, smsCode)
            return intent
        }

        @JvmStatic
        fun registerMe(context: Context) {
            val filter = IntentFilter()
            filter.addAction(ACTION_COPY_CODE)
            ContextCompat.registerReceiver(context, instance, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }
}
