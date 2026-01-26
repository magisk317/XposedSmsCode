package com.tianma.xsmscode.ui.rule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.FragmentManager
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.ActivityCodeRulesBinding
import com.tianma.xsmscode.data.eventbus.Event
import com.tianma.xsmscode.data.eventbus.XEventBus
import com.tianma.xsmscode.ui.app.base.BaseActivity
import com.tianma.xsmscode.ui.rule.edit.RuleEditFragment
import com.tianma.xsmscode.ui.rule.list.RuleListFragment
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * User custom smscode codeRule list
 */
class CodeRulesActivity : BaseActivity() {

    private lateinit var binding: ActivityCodeRulesBinding
    private var mFragmentManager: FragmentManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleInsets(binding.root)

        setupToolbar()
        handleIntent(intent)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar.root)
        refreshActionBar(getString(R.string.rule_list))
    }

    private fun refreshActionBar(title: String) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.title = title
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        var ruleListFragment: RuleListFragment? = null

        if (Intent.ACTION_VIEW == action) {
            val uri = intent.data
            if (uri != null) {
                // Import rules by back file URI
                ruleListFragment = RuleListFragment.newInstance(uri)
            }
        }

        if (ruleListFragment == null) {
            ruleListFragment = RuleListFragment.newInstance()
        }

        mFragmentManager = supportFragmentManager
        mFragmentManager?.addOnBackStackChangedListener {
            val count = mFragmentManager?.backStackEntryCount ?: 0
            if (count > 0) {
                 // Nothing to do for now, title is set when adding to backstack
            } else {
                refreshActionBar(getString(R.string.rule_list))
            }
        }
        mFragmentManager?.beginTransaction()
            ?.replace(R.id.code_rules_main_content, ruleListFragment, TAG_RULE_LIST)
            ?.commit()
    }

    override fun onStart() {
        super.onStart()
        XEventBus.register(this)
    }

    override fun onStop() {
        super.onStop()
        XEventBus.unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onStartRuleEdit(event: Event.StartRuleEditEvent) {
        val ruleEditFragment = RuleEditFragment.newInstance(event.type, event.codeRule)
        mFragmentManager?.beginTransaction()
            ?.replace(R.id.code_rules_main_content, ruleEditFragment, TAG_RULE_EDIT)
            ?.addToBackStack(TAG_RULE_EDIT)
            ?.commit()
        if (event.type == RuleEditFragment.EDIT_TYPE_CREATE) {
            refreshActionBar(getString(R.string.create_rule))
        } else {
            refreshActionBar(getString(R.string.edit_rule))
        }
    }


    companion object {
        private const val TAG_RULE_EDIT = "tag_rule_edit"
        private const val TAG_RULE_LIST = "tag_rule_list"

        @JvmStatic
        fun startToMe(context: Context) {
            val intent = Intent(context, CodeRulesActivity::class.java)
            context.startActivity(intent)
        }
    }
}
