package com.tianma.xsmscode.ui.rule.list

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.view.*
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AlertDialog
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.FragmentRuleListBinding
import com.google.android.material.snackbar.Snackbar
import com.tianma.xsmscode.common.TextWatcherAdapter
import com.tianma.xsmscode.common.adapter.BaseItemCallback
import com.tianma.xsmscode.common.utils.SnackbarHelper
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.common.widget.FabScrollBehavior
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.eventbus.Event
import com.tianma.xsmscode.data.eventbus.XEventBus
import com.tianma.xsmscode.feature.backup.BackupManager
import com.tianma.xsmscode.feature.backup.ImportResult
import com.tianma.xsmscode.ui.rule.edit.RuleEditFragment
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

/**
 * SMS code codeRule list fragment
 */
class RuleListFragment : Fragment() {

    private var binding: FragmentRuleListBinding? = null
    private var mActivity: Activity? = null
    private var mProgressDialog: AlertDialog? = null
    private var mRuleAdapter: RuleAdapter? = null
    private var mSelectedPosition = -1
    private lateinit var mViewModel: RuleListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRuleListBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mActivity = requireActivity()

        binding?.ruleListRecyclerView?.apply {
            layoutManager = LinearLayoutManager(mActivity)
            addItemDecoration(DividerItemDecoration(mActivity, DividerItemDecoration.VERTICAL))
            mRuleAdapter = RuleAdapter(requireActivity(), mutableListOf())
            adapter = mRuleAdapter
        }

        // swipe to remove
        val itemTouchHelper = ItemTouchHelper(mSwipeToRemoveCallback)
        binding?.let { itemTouchHelper.attachToRecyclerView(it.ruleListRecyclerView) }

        mRuleAdapter?.setItemCallback(object : BaseItemCallback<SmsCodeRule>() {
            override fun onItemClicked(itemView: View, item: SmsCodeRule, position: Int) {
                mSelectedPosition = position
                XEventBus.post(Event.StartRuleEditEvent(RuleEditFragment.EDIT_TYPE_UPDATE, item))
            }

            override fun onCreateItemContextMenu(
                menu: ContextMenu,
                v: View,
                menuInfo: ContextMenu.ContextMenuInfo?,
                item: SmsCodeRule,
                position: Int
            ) {
                mSelectedPosition = position
                v.setOnCreateContextMenuListener(this@RuleListFragment)
                this@RuleListFragment.onCreateContextMenu(menu, v, menuInfo)
            }
        })
        mRuleAdapter?.registerAdapterDataObserver(mDataObserver)

        // fab settings
        binding?.ruleListFab?.let { fab ->
            val params = fab.layoutParams as CoordinatorLayout.LayoutParams
            params.behavior = FabScrollBehavior()
            fab.layoutParams = params

            fab.setOnClickListener {
                val emptyRule = SmsCodeRule()
                XEventBus.post(Event.StartRuleEditEvent(RuleEditFragment.EDIT_TYPE_CREATE, emptyRule))
            }
        }

        mViewModel = ViewModelProvider(this).get(RuleListViewModel::class.java)
        setupObservers()
        refreshData()
        mViewModel.handleArguments(arguments)
        
        setupMenu()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_rule_list, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_import_rules -> {
                        attemptImportRuleList()
                        true
                    }
                    R.id.action_export_rules -> {
                        attemptExportRuleList()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun refreshData() {
        mViewModel.loadAllRules()
    }

    override fun onStart() {
        super.onStart()
        XEventBus.register(this)
    }

    override fun onStop() {
        super.onStop()
        XEventBus.unregister(this)
        mRuleAdapter?.getRuleList()?.let { mViewModel.saveRulesToFile(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRuleAdapter?.unregisterAdapterDataObserver(mDataObserver)
        cancelProgress()
        mProgressDialog = null
    }


    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater = mActivity?.menuInflater
        inflater?.inflate(R.menu.context_rule_list, menu)
        menu.setHeaderTitle(R.string.actions)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val smsCodeRule = mRuleAdapter?.getItemAt(mSelectedPosition) ?: return super.onContextItemSelected(item)
        return when (item.itemId) {
            R.id.action_edit_rule -> {
                XEventBus.post(Event.StartRuleEditEvent(RuleEditFragment.EDIT_TYPE_UPDATE, smsCodeRule))
                true
            }
            R.id.action_remove_rule -> {
                removeItemAt(mSelectedPosition)
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private val mSwipeToRemoveCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END or ItemTouchHelper.START) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                removeItemAt(position)
            }
        }

    private fun removeItemAt(position: Int) {
        val adapter = mRuleAdapter ?: return
        val itemToRemove = adapter.getItemAt(position) ?: return
        adapter.removeItemAt(position)

        binding?.let { b ->
            SnackbarHelper.makeLong(b.ruleListRecyclerView, R.string.removed)
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            mViewModel.removeRule(itemToRemove)
                        }
                    }
                })
                .setAction(R.string.revoke) { adapter.addRule(position, itemToRemove) }
                .show()
        }
    }

    private val mDataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            refreshEmptyView()
        }
    }

    private fun refreshEmptyView() {
        if (mRuleAdapter?.itemCount == 0) {
            binding?.emptyView?.visibility = View.VISIBLE
        } else {
            binding?.emptyView?.visibility = View.GONE
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRuleSaveOrUpdate(event: Event.OnRuleCreateOrUpdate) {
        if (event.type == RuleEditFragment.EDIT_TYPE_CREATE) {
            mRuleAdapter?.addRule(event.codeRule)
        } else if (event.type == RuleEditFragment.EDIT_TYPE_UPDATE) {
            mRuleAdapter?.updateAt(mSelectedPosition, event.codeRule)
        }
    }

    private fun attemptExportRuleList() {
        if ((mRuleAdapter?.itemCount ?: 0) == 0) {
            binding?.let { SnackbarHelper.makeLong(it.ruleListRecyclerView, R.string.rule_list_empty_snack_prompt).show() }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val exportIntent = BackupManager.getExportRuleListSAFIntent()
            try {
                mExportLauncher.launch(exportIntent)
            } catch (e: Exception) {
                binding?.let { SnackbarHelper.makeLong(it.ruleListRecyclerView, R.string.documents_ui_not_found).show() }
            }
        } else {
            val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                showNoPermissionInfo()
                return
            }

            val defaultFilename = BackupManager.getDefaultBackupFilename()
            val hint = getString(R.string.backup_file_name)
            val backupDir = BackupManager.getBackupDir()
            val backupDirPath = backupDir?.absolutePath ?: ""
            val content = getString(R.string.backup_file_dir, backupDirPath)
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_code_regex_quick_chcoose, null) // Reusing existing layout for input or just creating a new one
            // Actually I should create a simple input layout or use EditText directly. 
            // For now let's use a simple EditText with padding.
            val inputEditText = EditText(requireContext()).apply {
                setText(defaultFilename)
                setSelection(0, defaultFilename.length - BackupManager.getBackupFileExtension().length)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            val frameLayout = android.widget.FrameLayout(requireContext()).apply {
                val params = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(60, 20, 60, 20) // approx padding
                layoutParams = params
                addView(inputEditText)
            }

            val exportFilenameDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_file_name)
                .setMessage(content)
                .setView(frameLayout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val input = inputEditText.text.toString()
                    if (backupDir != null) {
                        val file = File(backupDir, input)
                        mRuleAdapter?.getRuleList()?.let {
                            mViewModel.exportRulesBelowQ(it, file, getString(R.string.exporting))
                        }
                    } else {
                         SnackbarHelper.makeLong(binding!!.ruleListRecyclerView, R.string.save_failed).show()
                    }
                }
                .create()

            exportFilenameDialog.show()
            val positiveBtn = exportFilenameDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            inputEditText.addTextChangedListener(object : TextWatcherAdapter() {
                override fun afterTextChanged(s: Editable) {
                    positiveBtn.isEnabled = Utils.isValidFilename(s.toString())
                }
            })
        }
    }

    private fun attemptImportRuleList() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val importIntent = BackupManager.getImportRuleListSAFIntent()
            try {
                mImportLauncher.launch(importIntent)
            } catch (e: Exception) {
                binding?.let { SnackbarHelper.makeLong(it.ruleListRecyclerView, R.string.documents_ui_not_found).show() }
            }
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                showNoPermissionInfo()
                return
            }

            val files = BackupManager.getBackupFiles()
            if (files == null || files.isEmpty()) {
                binding?.let { SnackbarHelper.makeLong(it.ruleListRecyclerView, R.string.no_backup_exists).show() }
                return
            }

            val filenames = files.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.choose_backup_file)
                .setItems(filenames) { _, position ->
                    val file = files[position]
                    val uri = Uri.fromFile(file)
                    showImportDialogConfirm(uri)
                }
                .show()
        }
    }

    private fun showNoPermissionInfo() {
        binding?.let { SnackbarHelper.makeShort(it.ruleListRecyclerView, R.string.no_permission_prompt).show() }
    }

    private fun setupObservers() {
        mViewModel.rulesLiveData.observe(viewLifecycleOwner) { displayRules(it) }
        mViewModel.importDirectEvent.observe(viewLifecycleOwner) { attemptImportRuleListDirectly(it) }
        mViewModel.importDialogConfirmEvent.observe(viewLifecycleOwner) { showImportDialogConfirm(it) }
        mViewModel.showProgressEvent.observe(viewLifecycleOwner) { showProgress(it) }
        mViewModel.cancelProgressEvent.observe(viewLifecycleOwner) { cancelProgress() }
        mViewModel.exportResultBelowQEvent.observe(viewLifecycleOwner) { pair -> onExportCompletedBelowQ(pair.first, pair.second) }
        mViewModel.exportResultAboveQEvent.observe(viewLifecycleOwner) { onExportCompletedAboveQ(it) }
        mViewModel.importResultEvent.observe(viewLifecycleOwner) { onImportComplete(it) }
    }

    private fun displayRules(rules: List<SmsCodeRule>) {
        mRuleAdapter?.setRules(rules)
    }

    private fun attemptImportRuleListDirectly(uri: Uri) {
        val perm = Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
            showNoPermissionInfo()
            return
        }
        showImportDialogConfirm(uri)
    }

    private fun showImportDialogConfirm(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_confirmation_title)
            .setMessage(R.string.import_confirmation_message)
            .setPositiveButton(R.string.yes) { _, _ -> mViewModel.importRules(uri, true, getString(R.string.importing)) }
            .setNegativeButton(R.string.no) { _, _ -> mViewModel.importRules(uri, false, getString(R.string.importing)) }
            .show()
    }

    private fun onExportCompletedBelowQ(success: Boolean, file: File) {
        val msgId = if (success) R.string.export_succeed else R.string.export_failed
        binding?.let {
            val snackbar = SnackbarHelper.makeLong(it.ruleListRecyclerView, msgId)
            if (success) {
                snackbar.setAction(R.string.share) {
                    mActivity?.let { activity ->
                        BackupManager.shareBackupFile(activity, file)
                    }
                }
            }
            snackbar.show()
        }
    }

    private fun onExportCompletedAboveQ(success: Boolean) {
        val msgId = if (success) R.string.export_succeed else R.string.export_failed
        binding?.let { SnackbarHelper.makeLong(it.ruleListRecyclerView, msgId).show() }
    }

    private fun onImportComplete(importResult: ImportResult) {
        @StringRes val msg: Int = when (importResult) {
            ImportResult.SUCCESS -> {
                refreshData()
                R.string.import_succeed
            }
            ImportResult.VERSION_MISSED -> R.string.import_failed_version_missed
            ImportResult.VERSION_UNKNOWN -> R.string.import_failed_version_unknown
            ImportResult.BACKUP_INVALID -> R.string.import_failed_backup_invalid
            else -> R.string.import_failed_read_error
        }
        binding?.let { SnackbarHelper.makeLong(it.ruleListRecyclerView, msg).show() }
    }

    private fun showProgress(progressMsg: String) {
        if (mProgressDialog == null) {
            val progressView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null)
            val msgView = progressView.findViewById<android.widget.TextView>(R.id.progress_message)
            msgView.text = progressMsg
            
            mProgressDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(progressView)
                .setCancelable(true)
                .create()
        } else {
            val msgView = mProgressDialog?.findViewById<android.widget.TextView>(R.id.progress_message)
            msgView?.text = progressMsg
        }
        if (mProgressDialog?.isShowing == false) {
            mProgressDialog?.show()
        }
    }

    private fun cancelProgress() {
        if (mProgressDialog?.isShowing == true) {
            mProgressDialog?.cancel()
        }
    }

    private val mExportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                mRuleAdapter?.getRuleList()?.let {
                    mViewModel.exportRulesAboveQ(it, requireContext(), uri, getString(R.string.exporting))
                }
            }
        }

    private val mImportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                showImportDialogConfirm(uri)
            }
        }

    companion object {
        const val EXTRA_IMPORT_URI = "extra_import_uri"

        @JvmStatic
        fun newInstance(importUri: Uri?): RuleListFragment {
            val args = Bundle()
            args.putParcelable(EXTRA_IMPORT_URI, importUri)
            val fragment = RuleListFragment()
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun newInstance() = newInstance(null)
    }
}
