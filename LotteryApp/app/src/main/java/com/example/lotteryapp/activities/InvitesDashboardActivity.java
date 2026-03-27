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
    ImageButton btnCancelInvite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_invites_dashboard);

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.trim().isEmpty()) {
            finish();
            return;
        }

        ImageButton btnClose = findViewById(R.id.btn_close_invites_dashboard);
        TabLayout tabLayout = findViewById(R.id.tab_layout_invites);
        ViewPager2 viewPager = findViewById(R.id.view_pager_invites);

        btnClose.setOnClickListener(v -> finish());

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return InviteListFragment.newInstance(
                            eventId,
                            InviteListFragment.TYPE_INVITED
                    );
                } else if (position == 1) {
                    return InviteListFragment.newInstance(
                            eventId,
                            InviteListFragment.TYPE_DECLINED
                    );
                } else {
                    return InviteListFragment.newInstance(
                            eventId,
                            InviteListFragment.TYPE_ACCEPTED
                    );
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Pending");
            } else if (position == 1) {
                tab.setText("Declined");
            } else {
                tab.setText("Accepted");
            }
        }).attach();
    }
}