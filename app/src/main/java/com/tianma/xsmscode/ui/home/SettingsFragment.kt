package com.tianma.xsmscode.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import android.widget.FrameLayout
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.preference.ResetEditPreference
import com.tianma.xsmscode.common.preference.ResetEditPreferenceDialogFragCompat
import com.tianma.xsmscode.common.utils.ModuleUtils
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.common.utils.SnackbarHelper
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.ApkVersion
import com.tianma.xsmscode.ui.app.base.BasePreferenceFragment
import com.tianma.xsmscode.ui.block.AppBlockActivity
import com.tianma.xsmscode.ui.record.CodeRecordActivity
import com.tianma.xsmscode.ui.rule.CodeRulesActivity

/**
 * 首选项Fragment
 */
class SettingsFragment : BasePreferenceFragment(),
    Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener {

    private var mActivity: HomeActivity? = null
    private lateinit var mViewModel: SettingsViewModel

    override fun doOnCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        // general group
        if (!ModuleUtils.isModuleEnabled()) {
            val enablePref = findPreference<Preference>(PrefConst.KEY_ENABLE)
            enablePref?.setSummary(R.string.pref_enable_summary_alt)
        }

        findPreference<Preference>(PrefConst.KEY_HIDE_LAUNCHER_ICON)?.onPreferenceChangeListener = this
        // general group end

        // SMS code group
        val autoInputDelayPref = findPreference<EditTextPreference>(PrefConst.KEY_AUTO_INPUT_CODE_DELAY)
        autoInputDelayPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            editText.setSelection(editText.text.length)
        }
        showAutoInputDelaySummary(autoInputDelayPref, autoInputDelayPref?.text)
        autoInputDelayPref?.onPreferenceChangeListener = this

        findPreference<Preference>(PrefConst.KEY_APP_BLOCK_ENTRY)?.onPreferenceClickListener = this
        // SMS code group end

        // code rule group
        findPreference<Preference>(PrefConst.KEY_CODE_RULES)?.onPreferenceClickListener = this
        findPreference<Preference>(PrefConst.KEY_SMSCODE_TEST)?.onPreferenceClickListener = this
        // code rule group end

        // code records group
        val recordsEntryPref = findPreference<Preference>(PrefConst.KEY_ENTRY_CODE_RECORDS)
        recordsEntryPref?.onPreferenceClickListener = this
        initRecordEntryPreference(recordsEntryPref)
        // code records group end

        // others group
        findPreference<Preference>(PrefConst.KEY_VERBOSE_LOG_MODE)?.onPreferenceChangeListener = this
        // others group end

        // about group
        val versionPref = findPreference<Preference>(PrefConst.KEY_VERSION)
        versionPref?.onPreferenceClickListener = this
        showVersionInfo(versionPref)
        findPreference<Preference>(PrefConst.KEY_JOIN_QQ_GROUP)?.onPreferenceClickListener = this
        findPreference<Preference>(PrefConst.KEY_SOURCE_CODE)?.onPreferenceClickListener = this
        findPreference<Preference>(PrefConst.KEY_CHOOSE_THEME)?.onPreferenceClickListener = this
        findPreference<Preference>(PrefConst.KEY_DONATE_BY_ALIPAY)?.onPreferenceClickListener = this
        findPreference<Preference>(PrefConst.KEY_PRIVACY_POLICY)?.onPreferenceClickListener = this
        // about group end
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mActivity = requireActivity() as HomeActivity

        mViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        setupObservers()

        mViewModel.handleArguments(arguments)
    }

    private fun setupObservers() {
        mViewModel.showPrivacyPolicyEvent.observe(viewLifecycleOwner) { showPrivacyPolicy() }
        mViewModel.showAlipayPacketEvent.observe(viewLifecycleOwner) { showGetAlipayPacketDialog() }
        mViewModel.smsCodeTestResultEvent.observe(viewLifecycleOwner) { showSmsCodeTestResult(it) }
        mViewModel.checkUpdateErrorEvent.observe(viewLifecycleOwner) { showCheckError(it) }
        mViewModel.showUpdateDialogEvent.observe(viewLifecycleOwner) { showUpdateDialog(it) }
        mViewModel.appAlreadyNewestEvent.observe(viewLifecycleOwner) { showAppAlreadyNewest() }
    }

    override fun onPause() {
        super.onPause()
        val preferencesName = preferenceManager.sharedPreferencesName
        preferencesName?.let {
            mViewModel.setPreferenceWorldWritable(it)
        }
        mViewModel.setInternalFilesWritable()
    }

    private fun showAppAlreadyNewest() {
        SnackbarHelper.makeLong(listView, R.string.app_already_newest).show()
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = preference.key
        when (key) {
            PrefConst.KEY_CHOOSE_THEME -> {
                showThemeChooser()
            }
            PrefConst.KEY_CODE_RULES -> {
                CodeRulesActivity.startToMe(requireActivity())
            }
            PrefConst.KEY_SMSCODE_TEST -> {
                showSmsCodeTestDialog()
            }
            PrefConst.KEY_JOIN_QQ_GROUP -> {
                mViewModel.joinQQGroup()
            }
            PrefConst.KEY_SOURCE_CODE -> {
                mViewModel.showSourceProject()
            }
            PrefConst.KEY_DONATE_BY_ALIPAY -> {
                donateByAlipay()
            }
            PrefConst.KEY_ENTRY_CODE_RECORDS -> {
                CodeRecordActivity.startToMe(requireActivity())
            }
            PrefConst.KEY_APP_BLOCK_ENTRY -> {
                AppBlockActivity.startMe(requireActivity())
            }
            PrefConst.KEY_VERSION -> {
                mViewModel.checkUpdate()
            }
            PrefConst.KEY_PRIVACY_POLICY -> {
                showPrivacyPolicy()
            }
            else -> return false
        }
        return true
    }

    private fun showThemeChooser() {
        val activity = mActivity ?: return
        val currentMode = SPUtils.getThemeMode(activity)
        val items = arrayOf(
            getString(R.string.theme_follow_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pref_choose_theme_title)
            .setSingleChoiceItems(items, currentMode) { dialog, which ->
                SPUtils.setThemeMode(activity, which)
                applyTheme(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTheme(mode: Int) {
        val nightMode = when (mode) {
            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun showVersionInfo(preference: Preference?) {
        val summary = getString(R.string.pref_version_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        preference?.summary = summary
    }

    private fun donateByAlipay() {
        mActivity?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.dialog_donate_title)
                .setMessage(R.string.dialog_donate_content)
                .setPositiveButton(R.string.dialog_donate_alipay) { dialog, which -> showAlipayChoiceDialog() }
                .setNeutralButton(R.string.dialog_donate_wechat) { dialog, which -> showQRCodeDialog(R.drawable.wx, "wechat") }
                .setNegativeButton(R.string.dialog_donate_cancel, null)
                .show()
        }
    }

    private fun showAlipayChoiceDialog() {
        mActivity?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.dialog_donate_alipay_choice_title)
                .setMessage(R.string.dialog_donate_alipay_choice_content)
                .setPositiveButton(R.string.dialog_donate_alipay_qrcode) { dialog, which -> showQRCodeDialog(R.drawable.alipay, "alipay") }
                .setNeutralButton(R.string.dialog_donate_alipay_token) { dialog, which -> copyAlipayPocketToken() }
                .show()
        }
    }

    private fun showQRCodeDialog(resId: Int, type: String) {
        val activity = mActivity ?: return
        val imageView = android.widget.ImageView(activity).apply {
            setImageResource(resId)
            setPadding(60, 40, 60, 20)
            adjustViewBounds = true
            setOnLongClickListener {
                saveImageToGallery(resId, "${type}_qrcode")
                true
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(if (type == "alipay") R.string.dialog_donate_alipay else R.string.dialog_donate_wechat)
            .setMessage(R.string.long_press_save_hint)
            .setView(imageView)
            .setPositiveButton(R.string.confirm) { dialog, which -> 
                if (type == "alipay") {
                    PackageUtils.startAlipayActivity(activity)
                }
            }
            .show()
    }

    private fun saveImageToGallery(resId: Int, fileName: String) {
        val activity = mActivity ?: return
        val bitmap = android.graphics.BitmapFactory.decodeResource(resources, resId)
        val resolver = activity.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { 
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                SnackbarHelper.makeLong(listView, R.string.save_to_gallery_success).show()
                // Auto jump after 1.5s
                listView.postDelayed({
                    if (fileName.contains("alipay")) {
                        PackageUtils.startAlipayActivity(activity)
                    }
                }, 1500)
            } catch (e: Exception) {
                SnackbarHelper.makeLong(listView, R.string.save_to_gallery_failed).show()
            }
        } else {
            SnackbarHelper.makeLong(listView, R.string.save_to_gallery_failed).show()
        }
    }

    private fun copyAlipayPocketToken() {
        val context = context ?: return
        com.tianma.xsmscode.common.utils.Utils.copyToClipboard(context, com.tianma.xsmscode.common.constant.Const.ALIPAY_POCKET_TOKEN)
        SnackbarHelper.makeLong(listView, getString(R.string.alipay_red_packet_code_copied, com.tianma.xsmscode.common.constant.Const.ALIPAY_POCKET_TOKEN)).show()
        // Auto jump after 1.5s
        listView.postDelayed({
            PackageUtils.startAlipayActivity(context)
        }, 1500)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val key = preference.key
        when (key) {
            PrefConst.KEY_HIDE_LAUNCHER_ICON -> {
                mViewModel.hideOrShowLauncherIcon(newValue as Boolean)
            }
            PrefConst.KEY_VERBOSE_LOG_MODE -> {
                onVerboseLogModeSwitched(newValue as Boolean)
            }
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY -> {
                return onAutoInputDelayPrefChanged(preference, newValue)
            }
            else -> return false
        }
        return true
    }

    private fun onVerboseLogModeSwitched(on: Boolean) {
        XLog.setLogLevel(if (on) Log.VERBOSE else BuildConfig.LOG_LEVEL)
    }

    private fun showSmsCodeTestDialog() {
        mActivity?.let {
            val inputEditText = EditText(it).apply {
                setHint(R.string.sms_content_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            val frameLayout = FrameLayout(it).apply {
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(60, 20, 60, 20)
                layoutParams = params
                addView(inputEditText)
            }

            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.pref_smscode_test_title)
                .setView(frameLayout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    mViewModel.performSmsCodeTest(inputEditText.text.toString())
                }
                .show()
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        var handled = false
        if (preference is ResetEditPreference) {
            val dialogFragment = ResetEditPreferenceDialogFragCompat.newInstance(preference.key)
            parentFragmentManager.let {
                @Suppress("DEPRECATION")
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(it, "androidx.preference.PreferenceFragment.DIALOG")
                handled = true
            }
        }
        if (!handled) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun initRecordEntryPreference(preference: Preference?) {
        val summary = getString(R.string.pref_entry_code_records_summary, PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT)
        preference?.summary = summary
    }

    private fun showGetAlipayPacketDialog() {
        scrollToPreference(PrefConst.KEY_DONATE_BY_ALIPAY)
        donateByAlipay()
    }

    private fun showSmsCodeTestResult(code: String) {
        val text = if (TextUtils.isEmpty(code)) getString(R.string.cannot_parse_smscode)
        else getString(R.string.current_sms_code, code)
        SnackbarHelper.makeLong(listView, text).show()
    }

    private fun showCheckError(t: Throwable) {
        SnackbarHelper.makeShort(listView, R.string.check_update_failed).show()
    }

    private fun showUpdateDialog(latestVersion: ApkVersion) {
        mActivity?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.new_version_found)
                .setMessage(latestVersion.versionInfo ?: "")
                .setPositiveButton(R.string.update_from_coolapk) { _, _ -> mViewModel.updateFromCoolApk() }
                .setNegativeButton(R.string.update_from_github) { _, _ -> mViewModel.updateFromGithub() }
                .show()
        }
    }

    private fun showPrivacyPolicy() {
        mActivity?.let { activity ->
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.privacy_dialog_title)
                .setMessage(R.string.privacy_dialog_content)
                .setPositiveButton(R.string.privacy_dialog_confirm) { _, _ ->
                    SPUtils.setPrivacyPolicyAccepted(activity, true)
                }
                .setCancelable(false)
                .setNegativeButton(R.string.privacy_dialog_cancel) { _, _ ->
                    SPUtils.setPrivacyPolicyAccepted(activity, false)
                    activity.finish()
                }
                .show()
        }
    }

    private fun onAutoInputDelayPrefChanged(preference: Preference, newValue: Any): Boolean {
        if (newValue is String) {
            showAutoInputDelaySummary(preference, newValue)
            return true
        }
        return false
    }

    private fun showAutoInputDelaySummary(preference: Preference?, value: String?) {
        val context = context ?: return
        val summary = context.getString(R.string.pref_auto_input_code_delay_summary, value)
        preference?.summary = summary
    }

    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_DONATE_BY_ALIPAY = "donate_by_alipay"

        @JvmStatic
        fun newInstance() = newInstance(null)

        @JvmStatic
        fun newInstance(extraAction: String?): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()
            args.putString(EXTRA_ACTION, extraAction)
            fragment.arguments = args
            return fragment
        }
    }
}
