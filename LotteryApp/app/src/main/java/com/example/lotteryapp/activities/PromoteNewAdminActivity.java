package com.example.lotteryapp.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.fragments.AdminListFragment;
import com.google.android.material.appbar.MaterialToolbar;

public class PromoteNewAdminActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promote_new_admin);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.admin_settings_container, AdminListFragment.newInstance(AdminListFragment.TYPE_REQUESTED_ADMINS))
                .commit();
        }
    }
}