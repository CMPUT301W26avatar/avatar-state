package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lotteryapp.R;
import com.example.lotteryapp.fragments.InviteListFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class InvitesDashboardActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";

    private String eventId;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TabLayoutMediator tabMediator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_invites_dashboard);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.trim().isEmpty()) {
            finish();
            return;
        }

        ImageButton btnClose = findViewById(R.id.btn_close_invites_dashboard);
        tabLayout = findViewById(R.id.tab_layout_invites);
        viewPager = findViewById(R.id.view_pager_invites);

        btnClose.setOnClickListener(v -> finish());

        setupPager();
    }

    public void refreshDashboard() {
        if (viewPager == null || tabLayout == null || eventId == null || eventId.trim().isEmpty()) {
            return;
        }

        int currentItem = viewPager.getCurrentItem();

        if (tabMediator != null) {
            tabMediator.detach();
            tabMediator = null;
        }

        setupPager();

        int pageCount = 4;
        if (currentItem >= 0 && currentItem < pageCount) {
            viewPager.setCurrentItem(currentItem, false);
        }
    }

    private void setupPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return InviteListFragment.newInstance(
                                eventId,
                                InviteListFragment.TYPE_INVITED
                        );
                    case 1:
                        return InviteListFragment.newInstance(
                                eventId,
                                InviteListFragment.TYPE_CANCELLED
                        );
                    case 2:
                        return InviteListFragment.newInstance(
                                eventId,
                                InviteListFragment.TYPE_DECLINED
                        );
                    default:
                        return InviteListFragment.newInstance(
                                eventId,
                                InviteListFragment.TYPE_ACCEPTED
                        );
                }
            }

            @Override
            public int getItemCount() {
                return 4;
            }
        });

        tabMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Pending");
                    break;
                case 1:
                    tab.setText("Cancelled");
                    break;
                case 2:
                    tab.setText("Declined");
                    break;
                default:
                    tab.setText("Accepted");
                    break;
            }
        });
        tabMediator.attach();
    }
}