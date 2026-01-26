package com.tianma.xsmscode.ui.block;

import android.app.Activity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.github.tianma8023.xposed.smscode.R;
import com.tianma.xsmscode.common.adapter.ItemCallback;
import com.tianma.xsmscode.common.utils.SnackbarHelper;
import com.tianma.xsmscode.data.db.entity.AppInfo;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.github.tianma8023.xposed.smscode.databinding.FragmentAppBlockBinding;
import dagger.android.support.DaggerFragment;

public class AppBlockFragment extends DaggerFragment implements AppBlockContract.View {

    private FragmentAppBlockBinding binding;

    private AppInfoAdapter mAppInfoAdapter;

    private Activity mActivity;

    @Inject
    AppBlockContract.Presenter mPresenter;

    static AppBlockFragment newInstance() {
        return new AppBlockFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = FragmentAppBlockBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = getActivity();

        mAppInfoAdapter = new AppInfoAdapter(mActivity, new ArrayList<>());
        mAppInfoAdapter.setItemCallback(new ItemCallback<AppInfo>() {
            @Override
            public void onItemClicked(View itemView, AppInfo item, int position) {
                itemClicked(item, position);
            }

            @Override
            public boolean onItemLongClicked(View itemView, AppInfo item, int position) {
                itemClicked(item, position);
                return true;
            }

            @Override
            public void onCreateItemContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo, AppInfo item, int position) {

            }
        });

        binding.appBlockRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        binding.appBlockRecyclerView.setAdapter(mAppInfoAdapter);
        binding.appBlockRecyclerView.addItemDecoration(new DividerItemDecoration(mActivity, DividerItemDecoration.VERTICAL));

        binding.swipeRefreshLayout.setOnRefreshListener(() -> mPresenter.refreshData());
    }

    @Override
    public void onResume() {
        super.onResume();
        mPresenter.refreshData();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onDetach();
        cancelProgress();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_app_block, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                doFilter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                doFilter(newText);
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort_by_label_asc) {
            mPresenter.doSort(SortType.LABEL_ASC);
        } else if (id == R.id.action_sort_by_pkg_asc) {
            mPresenter.doSort(SortType.PACKAGE_ASC);
        } else if (id == R.id.action_sort_by_label_desc) {
            mPresenter.doSort(SortType.LABEL_DESC);
        } else if (id == R.id.action_sort_by_pkg_desc) {
            mPresenter.doSort(SortType.PACKAGE_DESC);
        } else if (id == R.id.action_tick) {
            mPresenter.saveData();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void doFilter(String filter) {
        mPresenter.doFilter(filter);
    }

    private void itemClicked(AppInfo appInfo, int position) {
        mPresenter.doItemClicked(appInfo);
        mAppInfoAdapter.setItemSelected(position);
    }

    @Override
    public void showData(List<AppInfo> appInfoList) {
        mAppInfoAdapter.setItemList(appInfoList);
        binding.swipeRefreshLayout.setEnabled(false);
    }

    @Override
    public void showError(Throwable t) {
        binding.swipeRefreshLayout.setEnabled(true);
        SnackbarHelper.makeShort(binding.appBlockRecyclerView, R.string.load_failed).show();
    }

    @Override
    public void showProgress() {
        if (!binding.swipeRefreshLayout.isRefreshing()) {
            binding.swipeRefreshLayout.setRefreshing(true);
        }
    }

    @Override
    public void cancelProgress() {
        if (binding.swipeRefreshLayout.isRefreshing()) {
            binding.swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void onSaveSuccess() {
        mActivity.onBackPressed();
    }

    @Override
    public void onSaveFailed() {
        SnackbarHelper.makeShort(binding.appBlockRecyclerView, R.string.save_failed).show();
    }
}
