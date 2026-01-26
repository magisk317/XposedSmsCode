package com.tianma.xsmscode.ui.record

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.ActivityCodeRecordsBinding
import com.tianma.xsmscode.ui.app.base.BaseActivity

/**
 * Sms Code Records
 */
class CodeRecordActivity : BaseActivity() {

    private lateinit var binding: ActivityCodeRecordsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleInsets(binding.root)

        setupToolbar()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.code_records_main_content, CodeRecordFragment.newInstance())
            .commit()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }


    companion object {
        @JvmStatic
        fun startToMe(context: Context) {
            val intent = Intent(context, CodeRecordActivity::class.java)
            context.startActivity(intent)
        }
    }
}
