@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.magisk317.uikit.common.DismissibleSnackbarHost
import io.github.magisk317.uikit.preference.StateSwitchItem
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.surface.DetailSectionCard
import kotlinx.coroutines.launch

@Composable
fun LiteSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedSnackbarText = context.getString(R.string.pref_sync_toast)

    fun notifySaved() {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }

    val showToast by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_SHOW_TOAST,
        true,
    ).collectAsState(initial = true)
    val copyToClipboard by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_COPY_TO_CLIPBOARD,
        true,
    ).collectAsState(initial = true)
    val autoInput by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        true,
    ).collectAsState(initial = true)
    val autoEnter by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
        false,
    ).collectAsState(initial = false)
    val inputDelay by AppPreferencesDataStore.getStringFlow(
        context,
        PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
        PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
    ).collectAsState(initial = PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT)
    val inputInterval by AppPreferencesDataStore.getStringFlow(
        context,
        PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
        PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
    ).collectAsState(initial = PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DetailSectionCard(
                title = "验证码精简版设置",
                summary = "仅保留验证码解析与自动填充相关能力。",
            ) {
                StateSwitchItem(
                    title = "显示验证码提示",
                    summary = "",
                    checked = showToast,
                    onCheckedChange = {
                        scope.launch {
                            AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_SHOW_TOAST, it)
                            HookPreferenceMirror.publish(context)
                            notifySaved()
                        }
                    },
                )
                StateSwitchItem(
                    title = "复制验证码到剪贴板",
                    summary = "",
                    checked = copyToClipboard,
                    onCheckedChange = {
                        scope.launch {
                            AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, it)
                            HookPreferenceMirror.publish(context)
                            notifySaved()
                        }
                    },
                )
                StateSwitchItem(
                    title = "自动输入验证码",
                    summary = "",
                    checked = autoInput,
                    onCheckedChange = {
                        scope.launch {
                            AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, it)
                            HookPreferenceMirror.publish(context)
                            notifySaved()
                        }
                    },
                )
                StateSwitchItem(
                    title = "自动提交验证码",
                    summary = "",
                    checked = autoEnter,
                    onCheckedChange = {
                        scope.launch {
                            AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, it)
                            HookPreferenceMirror.publish(context)
                            notifySaved()
                        }
                    },
                )
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    AppTextField(
                        value = normalizeNumericInput(inputDelay),
                        onValueChange = { value ->
                            val normalized = normalizeNumericInput(value)
                            if (normalized.isEmpty() || parseNonNegativeLongLite(normalized) != null) {
                                scope.launch {
                                    AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, normalized)
                                    HookPreferenceMirror.publish(context)
                                }
                            }
                        },
                        label = "自动输入延迟(毫秒)",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    AppTextField(
                        value = normalizeNumericInput(inputInterval),
                        onValueChange = { value ->
                            val normalized = normalizeNumericInput(value)
                            if (normalized.isEmpty() || parseNonNegativeLongLite(normalized) != null) {
                                scope.launch {
                                    AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL, normalized)
                                    HookPreferenceMirror.publish(context)
                                }
                            }
                        },
                        label = "自动输入间隔(毫秒)",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }

        DismissibleSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

private fun normalizeNumericInput(raw: String): String {
    val normalized = StringBuilder(raw.length)
    raw.forEach { ch ->
        when {
            ch.isWhitespace() || Character.getType(ch) == Character.FORMAT.toInt() -> Unit
            ch.digitToIntOrNull() != null -> normalized.append(ch.digitToInt())
            ch in setOf('-', '－', '﹣', '—', '–') && normalized.isEmpty() -> normalized.append('-')
            else -> normalized.append(ch)
        }
    }
    return normalized.toString()
}

private fun parseNonNegativeLongLite(raw: String): Long? {
    return normalizeNumericInput(raw)
        .toLongOrNull()
        ?.takeIf { it >= 0L }
}
