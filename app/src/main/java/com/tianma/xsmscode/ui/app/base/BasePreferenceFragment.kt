package com.tianma.xsmscode.ui.app.base

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.tianma.xsmscode.common.constant.PrefConst

/**
 * Base Preferences Fragment
 */
abstract class BasePreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = PrefConst.PREF_NAME
        doOnCreatePreferences(savedInstanceState, rootKey)
    }

    protected abstract fun doOnCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)

    override fun <T : Preference> findPreference(key: CharSequence): T? {
        return super.findPreference(key)
    }
}
