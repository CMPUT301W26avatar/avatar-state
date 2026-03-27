package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.HashMap;
import java.util.Map;

/**
 * AdminActivity provides the administrator interface for managing application content
 *      tabbed interface
 *      tabs hold collections of items
 *      tabs use AdminListFragment
 * later: support filtering and sorting of items within the currently visible admin list
 */

public class AdminActivity extends AppCompatActivity {

    private final Map<Integer, AdminListFragment> fragments = new HashMap<>();
    private String currentFilter = "";
    private boolean sortAscending = true;

    /**
     * Initializes the admin screen and sets up UI components
     *      tab layout and fragments
     *      AdminList item click listeners
     */
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

            /**
             * Creates the fragment for each admin tab.
             */
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                String type;
                if (position == 0) {
                    type = AdminListFragment.TYPE_EVENTS;
                } else if (position == 1) {
                    type = AdminListFragment.TYPE_PROFILES;
                } else if (position == 2) {
                    type = AdminListFragment.TYPE_IMAGES;
                } else {
                    type = AdminListFragment.TYPE_NOTIFICATION_LOGS;
                }

                AdminListFragment fragment = AdminListFragment.newInstance(type);
                fragments.put(position, fragment);
                return fragment;
            }

            /**
             * Returns the number of admin tabs.
             */
            @Override
            public int getItemCount() {
                return 4;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Events");
            } else if (position == 1) {
                tab.setText("Profiles");
            } else if (position == 2) {
                tab.setText("Images");
            } else  {
                tab.setText("Notification Log");
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

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                applyFilterToCurrent(position);
                AdminListFragment fragment = fragments.get(position);
                if (fragment != null) {
                    fragment.setSortAscending(sortAscending);
                }
            }
        });
    }

/**
 * Applies the current filter string to the fragment displayed in the selected tab
 * Updates the fragment so its displayed items match the filter text
 */
    private void applyFilterToCurrent(int position) {
        AdminListFragment fragment = fragments.get(position);
        if (fragment != null) {
            fragment.setFilter(currentFilter);
        }
    }

    /**
     * Show dialog for sorting AdminList items
     */
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
