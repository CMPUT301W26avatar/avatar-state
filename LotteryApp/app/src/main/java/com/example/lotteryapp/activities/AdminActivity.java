package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lotteryapp.R;
import com.example.lotteryapp.fragments.AdminListFragment;
import com.example.lotteryapp.services.ServiceLocator;
import com.google.android.material.appbar.MaterialToolbar;
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

        MaterialButton btnNotifLog = findViewById(R.id.btn_notification_log);
        btnNotifLog.setOnClickListener(v -> {
            ServiceLocator.getNotificationLogStorage().getAllNotificationLogs(
                    logs -> {
                        StringBuilder sb = new StringBuilder();
                        if (logs.isEmpty()) {
                            sb.append("No notifications have been sent yet!");
                        } else {
                            for (com.example.lotteryapp.models.NotificationLog log: logs) {
                                sb.append("[").append(log.getType()).append("] ")
                                  .append(log.getTitle()).append("\n")
                                  .append(log.getMessage()).append("\n")
                                  .append("Organizer: ").append(log.getOrganizerId()).append("\n");
                            }
                        }
                        new AlertDialog.Builder(this)
                                .setTitle("Notification Log")
                                .setMessage(sb.toString())
                                .setPositiveButton("Close", null)
                                .show();
                    },
                    e -> android.widget.Toast.makeText(this, "Failed to load logs", Toast.LENGTH_SHORT).show()
            );
        });

        viewPager.setAdapter(new FragmentStateAdapter(this) {

            /**
             * differentiated create fragment for each fragment type
             */
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

            /// hardcoded return 3
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
            ///  text watcher methods
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
