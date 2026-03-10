package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lotteryapp.R;
import com.example.lotteryapp.fragments.AdminListFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.HashMap;
import java.util.Map;

public class AdminActivity extends AppCompatActivity {

    private final Map<Integer, AdminListFragment> fragments = new HashMap<>();
    private String currentFilter = "";
    private boolean sortAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        ImageButton btnCloseAdmin = findViewById(R.id.btn_close_admin);
        MaterialButton btnPromote = findViewById(R.id.btn_admin_promote);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        EditText etFilter = findViewById(R.id.et_filter);
        MaterialButton btnSort = findViewById(R.id.btn_sort);

        btnCloseAdmin.setOnClickListener(v -> finish());

        btnPromote.setOnClickListener(v -> {
            Intent intent = new Intent(this, PromoteNewAdminActivity.class);
            startActivity(intent);
        });

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                String type;
                if (position == 0) {
                    type = AdminListFragment.TYPE_EVENTS;
                }
                else if (position == 1) {
                    type = AdminListFragment.TYPE_PROFILES;
                }
                else {
                    type = AdminListFragment.TYPE_IMAGES;
                }

                AdminListFragment fragment = AdminListFragment.newInstance(type);
                fragments.put(position, fragment);
                return fragment;
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Events");
            }
            else if (position == 1) {
                tab.setText("Profiles");
            }
            else {
                tab.setText("Images");
            }
        }).attach();

        etFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentFilter = s.toString();
                applyFilterToCurrent(viewPager.getCurrentItem());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSort.setOnClickListener(v -> showSortDialog(viewPager.getCurrentItem()));
    }

    private void applyFilterToCurrent(int position) {
        AdminListFragment fragment = fragments.get(position);
        if (fragment != null) {
            fragment.setFilter(currentFilter);
        }
    }

    private void showSortDialog(int position) {
        String[] options = {"Ascending (A-Z)", "Descending (Z-A)"};
        new AlertDialog.Builder(this)
                .setTitle("Sort by Name")
                .setItems(options, (dialog, which) -> {
                    sortAscending = (which == 0);
                    AdminListFragment fragment = fragments.get(position);
                    if (fragment != null) {
                        fragment.setSortAscending(sortAscending);
                    }
                })
                .show();
    }
}
