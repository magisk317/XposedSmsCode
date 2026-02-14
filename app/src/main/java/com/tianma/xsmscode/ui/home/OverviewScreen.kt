package com.tianma.xsmscode.ui.home

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.utils.ModuleUtils
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.Utils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(hazeState: HazeState, hazeStyle: HazeStyle) {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = if (activityOwner != null) {
        koinViewModel<SettingsViewModel>(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showAlipayChoiceDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val isEnabled = ModuleUtils.isModuleEnabled()

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val frameworkInfoState by produceState<Pair<String, String>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getLsposedModuleInfo()
        }
    }
    val frameworkType = frameworkInfoState?.first ?: stringResource(id = R.string.unknown)
    val frameworkVersion = frameworkInfoState?.second ?: run {
        val lsposedVersion = PackageUtils.getPackageVersion(context, Const.LSPOSED_MANAGER_PACKAGE_NAME)
        when {
            lsposedVersion != null && lsposedVersion.first.isNotBlank() ->
                "${lsposedVersion.first} (${lsposedVersion.second})"

            PackageUtils.isPackageInstalled(context, Const.LSPOSED_MANAGER_PACKAGE_NAME) ->
                stringResource(id = R.string.unknown)

            else -> stringResource(id = R.string.not_installed)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp + 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatusCard(
                    isEnabled = isEnabled,
                    onClick = if (isEnabled) {
                        null
                    } else {
                        {
                            val intent = Intent().apply {
                                setClassName(
                                    "org.lsposed.manager",
                                    "org.lsposed.manager.ui.activity.MainActivity",
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (ignored: Exception) {
                                // Ignore if LSPosed manager is not installed.
                            }
                        }
                    },
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        InfoItem(Icons.AutoMirrored.Filled.Label, stringResource(id = R.string.version_name), BuildConfig.VERSION_NAME)
                        InfoItem(Icons.Default.Numbers, stringResource(id = R.string.version_code), BuildConfig.VERSION_CODE.toString())
                        val rootHint = stringResource(id = R.string.root_permission_hint)
                        InfoItem(
                            Icons.Default.Extension,
                            stringResource(id = R.string.framework_type),
                            frameworkType,
                            onClick = { Toast.makeText(context, rootHint, Toast.LENGTH_SHORT).show() },
                        )
                        InfoItem(
                            Icons.Default.Verified,
                            stringResource(id = R.string.framework_version),
                            frameworkVersion,
                            onClick = { Toast.makeText(context, rootHint, Toast.LENGTH_SHORT).show() },
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        InfoItem(Icons.Default.Android, stringResource(id = R.string.android_version), Build.VERSION.RELEASE)
                        InfoItem(Icons.Default.Terminal, stringResource(id = R.string.android_codename), Build.VERSION.CODENAME)
                        InfoItem(Icons.Default.Code, stringResource(id = R.string.api_level), Build.VERSION.SDK_INT.toString())
                        InfoItem(Icons.Default.Business, stringResource(id = R.string.manufacturer), Build.MANUFACTURER)
                        InfoItem(Icons.Default.Smartphone, stringResource(id = R.string.model), Build.MODEL)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        InfoItem(
                            icon = Icons.Default.Info,
                            label = stringResource(id = R.string.check_update_title),
                            value = stringResource(id = R.string.check_update_summary),
                            onClick = {
                                settingsViewModel.requestPreferredUpdate()
                            },
                        )
                        InfoItem(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            label = stringResource(id = R.string.pref_join_qq_group_title),
                            value = stringResource(id = R.string.pref_join_qq_group_summary),
                            onClick = { PackageUtils.joinQQGroup(context) },
                        )
                        InfoItem(
                            icon = Icons.AutoMirrored.Filled.Send,
                            label = stringResource(id = R.string.pref_join_telegram_group_title),
                            value = stringResource(id = R.string.pref_join_telegram_group_summary),
                            onClick = { Utils.showWebPage(context, Const.TELEGRAM_GROUP_URL) },
                        )
                        InfoItem(
                            icon = Icons.Default.Code,
                            label = stringResource(id = R.string.pref_source_code_title),
                            value = stringResource(id = R.string.pref_source_code_summary),
                            onClick = { Utils.showWebPage(context, Const.PROJECT_SOURCE_CODE_URL) },
                        )
                        InfoItem(
                            icon = Icons.Default.Favorite,
                            label = stringResource(id = R.string.pref_donate_by_alipay_title),
                            value = stringResource(id = R.string.dialog_donate_summary),
                            onClick = { showDonateDialog = true },
                        )
                    }
                }
            }
        }

        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                showAlipayChoiceDialog = true
            },
            onWechat = {
                showDonateDialog = false
                showQRCodeDialog = Pair(R.drawable.wx, "wechat")
            },
        )
    }

    if (showAlipayChoiceDialog) {
        AlipayChoiceDialog(
            onDismiss = { showAlipayChoiceDialog = false },
            onQRCode = {
                showAlipayChoiceDialog = false
                showQRCodeDialog = Pair(R.drawable.alipay, "alipay")
            },
            onToken = {
                showAlipayChoiceDialog = false
                PackageUtils.copyAlipayPocketToken(context)
                PackageUtils.startAlipayActivity(context)
            },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { showQRCodeDialog = null },
            onSave = { Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode") },
        )
    }
}

@Composable
fun StatusCard(isEnabled: Boolean, onClick: (() -> Unit)? = null) {
    val containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        onClick = { onClick?.invoke() },
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Column {
                Text(
                    text = if (isEnabled) stringResource(id = R.string.status_working) else stringResource(id = R.string.status_not_active),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (!isEnabled) {
                    Text(
                        text = stringResource(id = R.string.status_tip),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun InfoItem(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
