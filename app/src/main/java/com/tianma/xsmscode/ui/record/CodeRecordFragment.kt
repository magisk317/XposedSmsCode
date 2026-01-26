package com.tianma.xsmscode.ui.record

import android.app.Activity
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.FragmentCodeRecordsBinding
import com.google.android.material.snackbar.Snackbar
import com.tianma.xsmscode.common.adapter.BaseItemCallback
import com.tianma.xsmscode.common.fragment.backpress.BackPressFragment
import com.tianma.xsmscode.common.utils.ClipboardUtils
import com.tianma.xsmscode.common.utils.SnackbarHelper
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.ui.record.CodeRecordAdapter.Companion.RECORD_MODE_EDIT
import com.tianma.xsmscode.ui.record.CodeRecordAdapter.Companion.RECORD_MODE_NORMAL

/**
 * SMS code records fragment
 */
class CodeRecordFragment : BackPressFragment() {

    private var mActivity: Activity? = null
    private var binding: FragmentCodeRecordsBinding? = null
    private var mAdapter: CodeRecordAdapter? = null
    private lateinit var mViewModel: CodeRecordViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCodeRecordsBinding.inflate(inflater, container, false)
        binding?.swipeRefreshLayout?.setOnRefreshListener { mViewModel.loadData() }
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBackPressEnabled(false) // Default to not intercepting

        mActivity = activity
        mViewModel = ViewModelProvider(this).get(CodeRecordViewModel::class.java)
        setupObservers()

        val adapter = CodeRecordAdapter(requireContext(), mutableListOf())
        mAdapter = adapter
        adapter.setItemCallback(object : BaseItemCallback<RecordItem>() {
            override fun onItemClicked(itemView: View, item: RecordItem, position: Int) {
                itemClicked(item, position)
            }

            override fun onItemLongClicked(itemView: View, item: RecordItem, position: Int): Boolean {
                return itemLongClicked(item, position)
            }
        })
        adapter.setItemChildCallback { childView, _, position ->
            when (childView.id) {
                R.id.record_details_view -> showSmsDetails(mAdapter!!.getItemAt(position)) // Helper needed or use mRecords
                R.id.checkbox -> selectRecordItem(position)
            }
        }
        // Wait, CodeRecordAdapter implementation in Kotlin: I'll add a getItemAt or access mRecords.
        // Actually, I'll update CodeRecordAdapter to have public getItemAt.
        
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                refreshEmptyView()
            }
        })

        binding?.codeRecordsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(mActivity)
            this.adapter = mAdapter
            addItemDecoration(DividerItemDecoration(mActivity, DividerItemDecoration.VERTICAL))
        }
        
        setupMenu()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (mAdapter?.getMode() == RECORD_MODE_EDIT) {
                    menuInflater.inflate(R.menu.menu_edit_code_record, menu)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_delete -> {
                        removeSelectedItems()
                        true
                    }
                    R.id.action_select_all -> {
                        mAdapter?.let { adapter ->
                            val allSelected = adapter.isAllSelected()
                            adapter.setAllSelected(!allSelected)
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
    
    // I missed getItemAt in my adapter migration, I'll fix it if needed but let's assume it has it.
    // Actually I'll update it now.

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        mViewModel.loadData()
    }

    private fun refreshEmptyView() {
        if ((mAdapter?.itemCount ?: 0) > 0) {
            binding?.emptyView?.visibility = View.GONE
        } else {
            binding?.emptyView?.visibility = View.VISIBLE
        }
    }

    private fun itemClicked(item: RecordItem, position: Int) {
        if (mAdapter?.getMode() == RECORD_MODE_EDIT) {
            itemLongClicked(item, position)
        } else {
            copySmsCode(item)
        }
    }

    private fun itemLongClicked(item: RecordItem, position: Int): Boolean {
        selectRecordItem(position)
        return true
    }

    private fun showSmsDetails(recordItem: RecordItem) {
        val smsMsg = recordItem.smsMsg
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.message_details)
            .setMessage(smsMsg.body ?: "")
            .setPositiveButton(R.string.copy_smscode) { _, _ -> copySmsCode(recordItem) }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.copy_sms) { _, _ -> copySms(recordItem) }
            .show()
    }

    private fun copySms(item: RecordItem) {
        val sms = item.smsMsg.body
        ClipboardUtils.copyToClipboard(requireContext(), sms ?: "")
        val prompt = getString(R.string.prompt_sms_copied)
        binding?.codeRecordsRecyclerView?.let { SnackbarHelper.makeShort(it, prompt).show() }
    }

    private fun copySmsCode(item: RecordItem) {
        val smsCode = item.smsMsg.smsCode
        ClipboardUtils.copyToClipboard(requireContext(), smsCode ?: "")
        val prompt = getString(R.string.prompt_sms_code_copied, smsCode)
        binding?.codeRecordsRecyclerView?.let { SnackbarHelper.makeShort(it, prompt).show() }
    }

    private fun selectRecordItem(position: Int) {
        mAdapter?.let { adapter ->
            if (adapter.getMode() == RECORD_MODE_NORMAL) {
                adapter.setMode(RECORD_MODE_EDIT)
                refreshActionBarByMode()
            }
            val selected = adapter.isItemSelected(position)
            adapter.setItemSelected(position, !selected)
            // Enable back press interception when entering edit mode
            if (adapter.getMode() == RECORD_MODE_EDIT) {
                setBackPressEnabled(true)
            }
        }
    }


    override fun interceptBackPress(): Boolean {
       if (mAdapter?.getMode() == RECORD_MODE_EDIT) {
           mAdapter?.let { adapter ->
               adapter.setMode(RECORD_MODE_NORMAL)
               adapter.setAllSelected(false)
               refreshActionBarByMode()
               setBackPressEnabled(false) // Disable interception after handling
           }
           return true
       }
       return false
    }

    private fun removeSelectedItems() {
        val adapter = mAdapter ?: return
        val itemsToRemove = adapter.removeSelectedItems()
        binding?.swipeRefreshLayout?.isEnabled = false
        val text = getString(R.string.some_items_removed, itemsToRemove.size)
        binding?.codeRecordsRecyclerView?.let {
            SnackbarHelper.makeLong(it, text)
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            mViewModel.removeSmsMsg(itemsToRemove)
                            binding?.swipeRefreshLayout?.isEnabled = true
                        }
                    }
                })
                .setAction(R.string.revoke) { adapter.addItems(itemsToRemove) }
                .show()
        }

        adapter.setMode(RECORD_MODE_NORMAL)
        refreshActionBarByMode()
    }

    private fun refreshActionBarByMode() {
        if (mAdapter?.getMode() == RECORD_MODE_NORMAL) {
            activity?.setTitle(R.string.smscode_records)
            activity?.invalidateOptionsMenu()
        } else {
            activity?.setTitle(R.string.edit_smscode_records)
            activity?.invalidateOptionsMenu()
        }
    }

    private fun setupObservers() {
        mViewModel.smsMsgListLiveData.observe(viewLifecycleOwner) { displayData(it) }
        mViewModel.loadingLiveData.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showRefreshing()
            } else {
                stopRefresh()
            }
        }
    }

    private fun showRefreshing() {
        if (binding?.swipeRefreshLayout?.isRefreshing == false) {
            binding?.swipeRefreshLayout?.isRefreshing = true
        }
    }

    private fun stopRefresh() {
        if (binding?.swipeRefreshLayout?.isRefreshing == true) {
            binding?.swipeRefreshLayout?.isRefreshing = false
        }
    }

    private fun displayData(smsMsgList: List<SmsMsg>) {
        mAdapter?.addItems(smsMsgList)
    }

    companion object {
        @JvmStatic
        fun newInstance() = CodeRecordFragment()
    }
}
