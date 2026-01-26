package com.tianma.xsmscode.ui.home

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.ActivityHomeBinding
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.ModuleUtils
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.ui.app.base.BaseActivity
import com.tianma.xsmscode.ui.faq.FaqFragment
import com.tianma.xsmscode.ui.home.SettingsFragment

/**
 * 主界面
 */
class HomeActivity : BaseActivity() {
    private lateinit var binding: ActivityHomeBinding

    private var mCurrentFragment: Fragment? = null
    private var mFragmentManager: FragmentManager? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleInsets(binding.root)

        setupBackPressHandler()
        setupMenu()

        getExternalFilesDir("")

        shareXposedPreferences()

        handleIntent(intent)

        // setup toolbar
        setupToolbar()

        // check module activation status
        checkModuleActivationStatus()

        // check notification permission for Android 13+
        checkNotificationPermission()
    }

    private fun setupMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
                val faqItem = menu.findItem(R.id.action_home_faq)
                faqItem.isVisible = mCurrentFragment !is FaqFragment
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_home_faq -> {
                        onFAQSelected()
                        true
                    }
                    else -> false
                }
            }
        }, this, Lifecycle.State.RESUMED)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar.root)
        refreshActionBar(getString(R.string.app_name))
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        var settingsFragment: SettingsFragment? = null
        if (Intent.ACTION_VIEW == action) {
            val extraAction = intent.getStringExtra(SettingsFragment.EXTRA_ACTION)
            if (SettingsFragment.ACTION_DONATE_BY_ALIPAY == extraAction) {
                settingsFragment = SettingsFragment.newInstance(extraAction)
            }
        }

        if (settingsFragment == null) {
            settingsFragment = SettingsFragment.newInstance()
        }

        mFragmentManager = supportFragmentManager
        mFragmentManager?.beginTransaction()
            ?.replace(R.id.home_content, settingsFragment)
            ?.commit()
        mCurrentFragment = settingsFragment
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fm = mFragmentManager ?: return
                if (fm.backStackEntryCount == 0) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    fm.popBackStackImmediate()
                    mCurrentFragment = fm.findFragmentById(R.id.home_content)
                    refreshActionBar(getString(R.string.app_name))
                    invalidateOptionsMenu()
                }
            }
        })
    }

    private fun refreshActionBar(title: String) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.title = title
            actionBar.setHomeButtonEnabled(true)
            if (mCurrentFragment is SettingsFragment) {
                actionBar.setDisplayHomeAsUpEnabled(false)
            } else {
                actionBar.setDisplayHomeAsUpEnabled(true)
            }
        }
    }


    private fun onFAQSelected() {
        val faqFragment = FaqFragment.newInstance()
        mFragmentManager?.beginTransaction()
            ?.replace(R.id.home_content, faqFragment, TAG_FAQ)
            ?.addToBackStack(TAG_FAQ)
            ?.commit()
        mCurrentFragment = faqFragment
        refreshActionBar(getString(R.string.action_home_faq_title))
        invalidateOptionsMenu()
    }


    private fun checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    private fun checkModuleActivationStatus() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (isFinishing) {
                return@postDelayed
            }

            val format = "%s(%s)"
            val appName = getString(R.string.app_name)
            val appTitle = if (ModuleUtils.isModuleEnabled()) {
                String.format(format, appName, getString(R.string.module_status_active))
            } else {
                String.format(format, appName, getString(R.string.module_status_inactive))
            }
            binding.toolbar.root.title = appTitle
        }, 1000L)
    }

    @SuppressLint("WorldReadableFiles")
    private fun shareXposedPreferences() {
        try {
            @Suppress("DEPRECATION")
            getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_WORLD_READABLE)
        } catch (exception: SecurityException) {
            // 如果模块没有被 EdXposed 或者 LSPosed 激活，就会走到这里来
            // ignore
        }
    }

    companion object {
        private const val TAG_NESTED = "tag_nested"
        private const val TAG_FAQ = "tag_faq"
    }
}
