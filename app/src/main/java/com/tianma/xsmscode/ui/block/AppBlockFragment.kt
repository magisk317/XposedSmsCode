package com.tianma.xsmscode.ui.block

import android.app.Activity
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.github.tianma8023.xposed.smscode.R
import com.github.tianma8023.xposed.smscode.databinding.FragmentAppBlockBinding
import com.tianma.xsmscode.common.adapter.ItemCallback
import com.tianma.xsmscode.common.utils.SnackbarHelper
import com.tianma.xsmscode.data.db.entity.AppInfo
import java.util.ArrayList

class AppBlockFragment : Fragment() {

    private var _binding: FragmentAppBlockBinding? = null
    private val binding get() = _binding!!

    private var mAppInfoAdapter: AppInfoAdapter? = null
    private var mActivity: Activity? = null
    private lateinit var mViewModel: AppBlockViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppBlockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mActivity = requireActivity()
        mViewModel = ViewModelProvider(this).get(AppBlockViewModel::class.java)
        setupObservers()

        mAppInfoAdapter = AppInfoAdapter(requireContext(), ArrayList())
        mAppInfoAdapter?.setItemCallback(object : ItemCallback<AppInfo> {
            override fun onItemClicked(itemView: View, item: AppInfo, position: Int) {
                itemClicked(item, position)
            }

            override fun onItemLongClicked(itemView: View, item: AppInfo, position: Int): Boolean {
                itemClicked(item, position)
                return true
            }

            override fun onCreateItemContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?, item: AppInfo, position: Int) {
            }
        })

        binding.appBlockRecyclerView.layoutManager = LinearLayoutManager(mActivity)
        binding.appBlockRecyclerView.adapter = mAppInfoAdapter
        binding.appBlockRecyclerView.addItemDecoration(DividerItemDecoration(mActivity, DividerItemDecoration.VERTICAL))

        binding.swipeRefreshLayout.setOnRefreshListener { mViewModel.refreshData() }
        
        mViewModel.refreshData()

        setupMenu()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_app_block, menu)

                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem.actionView as SearchView
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        doFilter(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        doFilter(newText)
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.action_sort_by_label_asc -> mViewModel.doSort(SortType.LABEL_ASC)
                    R.id.action_sort_by_pkg_asc -> mViewModel.doSort(SortType.PACKAGE_ASC)
                    R.id.action_sort_by_label_desc -> mViewModel.doSort(SortType.LABEL_DESC)
                    R.id.action_sort_by_pkg_desc -> mViewModel.doSort(SortType.PACKAGE_DESC)
                    R.id.action_tick -> mViewModel.saveData()
                    else -> return false
                }
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupObservers() {
        mViewModel.appsLiveData.observe(viewLifecycleOwner) { showData(it) }
        mViewModel.loadingLiveData.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showProgress()
            } else {
                cancelProgress()
            }
        }
        mViewModel.errorEvent.observe(viewLifecycleOwner) { showError(it) }
        mViewModel.saveSuccessEvent.observe(viewLifecycleOwner) { onSaveSuccess() }
        mViewModel.saveFailedEvent.observe(viewLifecycleOwner) { onSaveFailed() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelProgress()
    }


    private fun doFilter(filter: String) {
        mViewModel.doFilter(filter)
    }

    private fun itemClicked(appInfo: AppInfo, position: Int) {
        mViewModel.doItemClicked(appInfo)
        mAppInfoAdapter?.setItemSelected(position)
    }

    private fun showData(appInfoList: List<AppInfo>) {
        mAppInfoAdapter?.setItemList(appInfoList)
        binding.swipeRefreshLayout.isEnabled = false
    }

    private fun showError(t: Throwable) {
        binding.swipeRefreshLayout.isEnabled = true
        SnackbarHelper.makeShort(binding.appBlockRecyclerView, R.string.load_failed).show()
    }

    private fun showProgress() {
        if (!binding.swipeRefreshLayout.isRefreshing) {
            binding.swipeRefreshLayout.isRefreshing = true
        }
    }

    private fun cancelProgress() {
        if (binding.swipeRefreshLayout.isRefreshing) {
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun onSaveSuccess() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    private fun onSaveFailed() {
        SnackbarHelper.makeShort(binding.appBlockRecyclerView, R.string.save_failed).show()
    }

    companion object {
        fun newInstance(): AppBlockFragment {
            return AppBlockFragment()
        }
    }
}
