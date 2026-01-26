package com.tianma.xsmscode.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.ActivityAppBlockBinding
import com.tianma.xsmscode.ui.app.base.BaseActivity

/**
 * Activity for choosing apps where auto-input is banned.
 */
class AppBlockActivity : BaseActivity() {

    private lateinit var binding: ActivityAppBlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleInsets(binding.root)

        setupToolbar()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.app_block_main_content, AppBlockFragment.newInstance())
            .commit()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar.root)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
    }

    companion object {
        @JvmStatic
        fun startMe(context: Context) {
            context.startActivity(Intent(context, AppBlockActivity::class.java))
        }
    }
}
