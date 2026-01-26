package com.tianma.xsmscode.ui.record;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.github.tianma8023.xposed.smscode.R;
import com.tianma.xsmscode.common.fragment.backpress.BackPressEventDispatchHelper;
import com.tianma.xsmscode.ui.app.base.BaseActivity;

import com.github.tianma8023.xposed.smscode.databinding.ActivityCodeRecordsBinding;

/**
 * Sms Code Records
 */
public class CodeRecordActivity extends BaseActivity {

    private ActivityCodeRecordsBinding binding;

    public static void startToMe(Context context) {
        Intent intent = new Intent(context, CodeRecordActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCodeRecordsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.code_records_main_content, CodeRecordFragment.newInstance())
                .commit();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar.getRoot());
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (!BackPressEventDispatchHelper.dispatchBackPressedEvent(this)) {
            super.onBackPressed();
        }
    }
}
